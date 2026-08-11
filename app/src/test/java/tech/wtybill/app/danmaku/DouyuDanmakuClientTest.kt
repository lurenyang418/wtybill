package tech.wtybill.app.danmaku

import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DouyuDanmakuClientTest {
    @Test
    fun sendsLoginJoinAndParsesPacketsAcrossFrames() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val request = Request.Builder().url("wss://example.test/socket").build()
        val socket = FakeWebSocket(request)
        var listener: WebSocketListener? = null
        val messages = mutableListOf<String>()
        var transportErrors = 0
        val factory = DanmakuSocketFactory { openedRequest, openedListener ->
            assertEquals(request.url, openedRequest.url)
            listener = openedListener
            socket
        }
        val client = DouyuDanmakuClient(
            scope = this,
            endpoint = request.url.toString(),
            onMessage = { messages += it.text },
            onError = { transportErrors++ },
            socketFactoryOverride = factory,
            ioDispatcher = dispatcher,
        )
        client.start("57321")
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(101)
            .message("Switching Protocols")
            .build()
        listener!!.onOpen(socket, response)
        assertEquals(2, socket.sent.size)
        assertTrue(socket.sent[0].utf8().contains("loginreq"))
        assertTrue(socket.sent[1].utf8().contains("joingroup"))

        val packets = DouyuPacketCodec.encode("type@=chatmsg/dms@=1/nn@=a/txt@=one/") +
            DouyuPacketCodec.encode("type@=chatmsg/dms@=1/nn@=b/txt@=two/")
        listener!!.onMessage(socket, packets.copyOfRange(0, packets.size - 2).toByteString())
        listener!!.onMessage(socket, packets.takeLast(2).toByteArray().toByteString())
        assertEquals(listOf("one", "two"), messages)
        assertEquals(0, transportErrors)
        client.stop()
    }

    @Test
    fun schedulesReconnectAfterTransportFailure() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val request = Request.Builder().url("wss://example.test/socket").build()
        val listeners = mutableListOf<WebSocketListener>()
        val factory = DanmakuSocketFactory { _, openedListener ->
            listeners += openedListener
            FakeWebSocket(request)
        }
        val client = DouyuDanmakuClient(
            scope = this,
            endpoint = request.url.toString(),
            onMessage = {},
            socketFactoryOverride = factory,
            ioDispatcher = dispatcher,
            reconnectBaseDelayMs = 10,
        )
        client.start("57321")
        listeners.single().onFailure(FakeWebSocket(request), IOException("offline"), null)
        advanceTimeBy(10)
        runCurrent()
        assertEquals(2, listeners.size)
        client.stop()
    }

    @Test
    fun staleSocketFailureDoesNotCreateAnotherConnectionAfterRestart() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val request = Request.Builder().url("wss://example.test/socket").build()
        val listeners = mutableListOf<WebSocketListener>()
        val factory = DanmakuSocketFactory { _, openedListener ->
            listeners += openedListener
            FakeWebSocket(request)
        }
        val client = DouyuDanmakuClient(
            scope = this,
            endpoint = request.url.toString(),
            onMessage = {},
            socketFactoryOverride = factory,
            ioDispatcher = dispatcher,
            reconnectBaseDelayMs = 10,
        )
        client.start("57321")
        val staleListener = listeners.single()
        client.start("57321")
        staleListener.onFailure(FakeWebSocket(request), IOException("stale"), null)
        advanceTimeBy(100)
        runCurrent()
        assertEquals(2, listeners.size)
        client.stop()
    }

    @Test
    fun malformedFrameCancelsSocketAndUsesReconnectPath() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val request = Request.Builder().url("wss://example.test/socket").build()
        val socket = FakeWebSocket(request)
        var listener: WebSocketListener? = null
        var connections = 0
        var protocolErrors = 0
        val client = DouyuDanmakuClient(
            scope = this,
            endpoint = request.url.toString(),
            onMessage = {},
            onError = { if (it is DanmakuProtocolError) protocolErrors++ },
            socketFactoryOverride = DanmakuSocketFactory { _, openedListener ->
                listener = openedListener
                connections++
                socket
            },
            ioDispatcher = dispatcher,
            reconnectBaseDelayMs = 10,
        )
        client.start("57321")
        val malformed = DouyuPacketCodec.encode("type@=mrkl/").also { packet ->
            packet[4] = (packet[4].toInt() + 1).toByte()
        }
        listener!!.onMessage(socket, malformed.toByteString())
        assertEquals(1, protocolErrors)
        assertTrue(socket.cancelled)
        advanceTimeBy(10)
        runCurrent()
        assertEquals(2, connections)
        client.stop()
    }

    private class FakeWebSocket(private val requestValue: Request) : WebSocket {
        val sent = mutableListOf<ByteString>()
        var cancelled = false

        override fun request(): Request = requestValue
        override fun queueSize(): Long = 0
        override fun send(text: String): Boolean = sent.add(text.toByteArray().toByteString())
        override fun send(bytes: ByteString): Boolean = sent.add(bytes)
        override fun close(code: Int, reason: String?): Boolean = true
        override fun cancel() { cancelled = true }
    }
}
