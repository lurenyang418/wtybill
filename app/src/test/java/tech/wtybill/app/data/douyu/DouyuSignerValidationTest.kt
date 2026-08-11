package tech.wtybill.app.data.douyu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DouyuSignerValidationTest {
    @Test fun parsesEncodedSignatureFormOnce() {
        val fields = parseDouyuForm("v=1&did=${DouyuSigner.DID}&tt=1700000000&sign=a%2Fb%26c")
        assertEquals("a/b&c", fields["sign"])
    }

    @Test fun validatesSignatureIdentityAndTimestamp() {
        val signature = validateDouyuSignature(
            mapOf("v" to "1", "did" to DouyuSigner.DID, "tt" to "1700000000", "sign" to "0123456789abcdef0123456789abcdef"),
            1700000000L,
        )
        assertEquals(DouyuSigner.DID, signature.fields["did"])
    }

    @Test fun rejectsMismatchedSignatureFields() {
        assertThrows(IllegalStateException::class.java) {
            validateDouyuSignature(
                mapOf("did" to "wrong", "tt" to "1700000000", "sign" to "0123456789abcdef0123456789abcdef"),
                1700000000L,
            )
        }
    }
}
