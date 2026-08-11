package tech.wtybill.app.data.douyu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DouyuStreamRequestTest {
    @Test fun includesAllDefaultFieldsAndOverridesTargetLine() {
        val fields = DouyuStreamRequest.fields(
            mapOf("v" to "1", "did" to DouyuSigner.DID, "tt" to "1700000000", "sign" to "abc"),
            cdn = "cdn-a",
            rate = 2,
        )
        assertEquals("cdn-a", fields["cdn"])
        assertEquals("2", fields["rate"])
        assertEquals("Douyu_223061205", fields["ver"])
        assertEquals("1", fields["iar"])
        assertEquals("1", fields["ive"])
        assertEquals("0", fields["hevc"])
        assertEquals("0", fields["fa"])
        assertTrue(fields.keys.containsAll(listOf("v", "did", "tt", "sign")))
    }
}
