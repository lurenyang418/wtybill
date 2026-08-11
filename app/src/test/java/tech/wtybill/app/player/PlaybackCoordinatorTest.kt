package tech.wtybill.app.player

import org.junit.Assert.assertEquals
import org.junit.Test
import tech.wtybill.app.data.douyu.DouyuProtocolException
import tech.wtybill.app.data.douyu.ResolvedStream

class PlaybackCoordinatorTest {
    @Test
    fun failureSkipsOnlyFailedRateAndCdnPair() {
        val first = ResolvedStream("https://one", rate = 0, cdn = "cdn-a")
        val sameCdnOtherRate = ResolvedStream("https://two", rate = 1, cdn = "cdn-a")
        val fallback = ResolvedStream("https://three", rate = 0, cdn = "cdn-b")

        assertEquals(
            listOf(sameCdnOtherRate, fallback),
            candidatesAfterFailure(listOf(first, sameCdnOtherRate, fallback), first),
        )
    }

    @Test
    fun protocolErrorRemainsDiagnosableToPlaybackLayer() {
        val error = PlaybackError.Resolve(DouyuProtocolException("斗鱼取流接口错误: -5"))
        assertEquals("斗鱼取流接口错误: -5", error.message)
    }
}
