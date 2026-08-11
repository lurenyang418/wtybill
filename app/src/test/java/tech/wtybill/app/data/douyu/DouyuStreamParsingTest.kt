package tech.wtybill.app.data.douyu

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DouyuStreamParsingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun sortsScdnAfterServerOrderedPrimaryLines() {
        val options = DouyuStreamParsing.parseOptions(json.parseToJsonElement("""
            {"data":{"multirates":[{"name":"原画","rate":"0"},{"name":"蓝光","rate":"2"}],"cdnsWithName":[{"cdn":"scdn2","name":"备用2"},{"cdn":"cdn-b","name":"线路B"},{"cdn":"scdn1","name":"备用1"},{"cdn":"cdn-a","name":"线路A"}]}}
        """))
        assertEquals(listOf("cdn-b", "cdn-a", "scdn2", "scdn1"), options.cdns.map { it.code })
        assertEquals(listOf(0, 2), options.rates.map { it.rate })
    }

    @Test fun acceptsNumericRateValues() {
        val options = DouyuStreamParsing.parseOptions(json.parseToJsonElement("""
            {"data":{"multirates":[{"name":"蓝光","rate":0}],
            "cdnsWithName":[{"cdn":"cdn-a","name":"主线路"}]}}
        """.trimIndent()))
        assertEquals(listOf(StreamRate("蓝光", 0)), options.rates)
    }

    @Test fun unescapesAndJoinsStreamUrl() {
        val url = DouyuStreamParsing.streamUrl(json.parseToJsonElement("""
            {"data":{"rtmp_url":"https://cdn.example/live/","rtmp_live":"room&#47;stream&amp;token=1"}}
        """))
        assertEquals("https://cdn.example/live/room/stream&token=1", url)
        assertTrue(url.startsWith("https://"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyRates() {
        DouyuStreamParsing.requirePlayableOptions(StreamOptions(emptyList(), listOf(CdnLine("cdn-a"))))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyCdns() {
        DouyuStreamParsing.requirePlayableOptions(StreamOptions(listOf(StreamRate("原画", 0)), emptyList()))
    }

    @Test fun skipsMalformedRateAndCdnEntries() {
        val options = DouyuStreamParsing.parseOptions(json.parseToJsonElement("""
            {"data":{"multirates":[1,{"name":"原画","rate":"0"},{"name":2,"rate":"bad"}],"cdnsWithName":[true,{"cdn":"cdn-a","name":"线路A"},{"cdn":3}]}}
        """))
        assertEquals(listOf(0), options.rates.map { it.rate })
        assertEquals(listOf("cdn-a"), options.cdns.map { it.code })
    }
}
