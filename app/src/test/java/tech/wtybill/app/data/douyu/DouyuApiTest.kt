package tech.wtybill.app.data.douyu

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import tech.wtybill.app.config.AppConfig
import tech.wtybill.app.net.NetworkClients

class DouyuApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: DouyuApi

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        api = DouyuApi(OkHttpClient(), server.url("/").toString().trimEnd('/'), server.url("/").toUrl().host)
    }

    @After fun tearDown() = server.shutdown()

    @Test fun parsesRoomAndUnescapesScriptString() = runBlocking {
        server.enqueue(MockResponse().setBody("{\"room\":{\"room_id\":\"57321\",\"room_name\":\"标题\",\"owner_name\":\"主播\",\"show_status\":1,\"videoLoop\":0,\"room_biz_all\":{\"hot\":\"12\"}}}"))
        server.enqueue(MockResponse().setBody("{\"error\":0,\"data\":{\"room57321\":\"function ub98484234(){\\n return 'x';\\n}\"}}"))
        val room = api.room("57321")
        val script = api.signingScript("57321")
        assertTrue(room.isLive)
        assertEquals("function ub98484234(){\n return 'x';\n}", script)
    }

    @Test
    fun parsesCurrentRoomStatusAndNestedAvatarFallback() = runBlocking {
        server.enqueue(MockResponse().setBody("""
            {"room":{"room_id":57321,"room_name":"标题","owner_name":"主播",
            "status":"1","show_status":"2","videoLoop":0,
            "avatar":{"big":"https://avatar.example/big.jpg"},"room_pic":"https://cover.example/a.avif"}}
        """.trimIndent()))
        val room = api.room("57321")
        assertFalse(room.isLive)
        assertEquals("https://avatar.example/big.jpg", room.anchorAvatar)
    }

    @Test
    fun rejectsUnsafeImageUrls() = runBlocking {
        server.enqueue(MockResponse().setBody("""
            {"room":{"room_id":"57321","room_name":"标题","owner_name":"主播",
            "show_status":0,"videoLoop":0,"owner_avatar":"http://bad.example/a.jpg",
            "room_pic":"/relative.jpg"}}
        """.trimIndent()))
        val room = api.room("57321")
        assertEquals(null, room.anchorAvatar)
        assertEquals(null, room.cover)
    }

    @Test fun sendsHeadersAndFormEncodedPlaybackRequest() = runBlocking {
        server.enqueue(MockResponse().setBody("{\"data\":{\"show_time\":1700000000}}"))
        server.enqueue(MockResponse().setBody("{\"data\":{\"multirates\":[],\"cdnsWithName\":[]}}"))
        assertEquals(1700000000L, api.showTime("57321"))
        val response = api.getH5Play("57321", mapOf("cdn" to "", "rate" to "-1", "sign" to "a/b"))
        assertTrue(response.toString().contains("multirates"))
        server.takeRequest()
        val request = server.takeRequest()
        assertEquals(AppConfig.DOUYU_REFERER, request.getHeader("Referer"))
        assertTrue(request.getHeader("User-Agent").orEmpty().contains("wtybill"))
        assertTrue(request.getHeader("Content-Type").orEmpty().startsWith("application/x-www-form-urlencoded"))
        assertTrue(request.body.readUtf8().contains("sign=a%2Fb"))
    }

    @Test(expected = DouyuHttpException::class)
    fun surfacesHttpErrorForPlaybackRequest() {
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))
            api.getH5Play("57321", mapOf("rate" to "-1"))
        }
    }

    @Test(expected = DouyuProtocolException::class)
    fun surfacesPlaybackProtocolError() {
        runBlocking {
            server.enqueue(MockResponse().setBody("{\"error\":-5}"))
            api.getH5Play("57321", mapOf("rate" to "-1"))
        }
    }

    @Test(expected = SocketTimeoutException::class)
    fun surfacesPlaybackReadTimeout() {
        runBlocking {
            val shortTimeoutApi = DouyuApi(
                OkHttpClient(),
                server.url("/").toString().trimEnd('/'),
                server.url("/").toUrl().host,
                connectTimeoutMs = 500,
                readTimeoutMs = 50,
            )
            server.enqueue(MockResponse().setBody("{\"data\":{}}" ).setBodyDelay(250, TimeUnit.MILLISECONDS))
            shortTimeoutApi.getH5Play("57321", mapOf("rate" to "-1"))
        }
    }

    @Test(expected = DouyuProtocolException::class)
    fun surfacesSigningEndpointError() {
        runBlocking {
            server.enqueue(MockResponse().setBody("{\"error\":-5}"))
            api.signingScript("57321")
        }
    }

    @Test(expected = DouyuProtocolException::class)
    fun surfacesMalformedRoomResponse() {
        runBlocking {
            server.enqueue(MockResponse().setBody("{\"data\":{}}"))
            api.room("57321")
        }
    }

    @Test
    fun sharedClientRetriesGetOnce() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setBody("{\"data\":{\"show_time\":1700000000}}"))
        val sharedApi = DouyuApi(NetworkClients.base, server.url("/").toString().trimEnd('/'), server.url("/").toUrl().host)
        assertEquals(1700000000L, sharedApi.showTime("57321"))
        assertEquals(2, server.requestCount)
    }

    @Test(expected = DouyuHttpException::class)
    fun sharedClientDoesNotRetryPost() {
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))
            server.enqueue(MockResponse().setBody("{\"data\":{}}"))
            val sharedApi = DouyuApi(NetworkClients.base, server.url("/").toString().trimEnd('/'), server.url("/").toUrl().host)
            try {
                sharedApi.getH5Play("57321", mapOf("rate" to "-1"))
            } finally {
                assertEquals(1, server.requestCount)
            }
        }
    }
}
