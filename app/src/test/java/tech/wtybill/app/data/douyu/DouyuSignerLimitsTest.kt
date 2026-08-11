package tech.wtybill.app.data.douyu

import org.junit.Assert.assertThrows
import org.junit.Test

class DouyuSignerLimitsTest {
    @Test fun rejectsOversizedDynamicScriptBeforeExecution() {
        assertThrows(IllegalArgumentException::class.java) {
            requireDynamicScriptWithinLimit("x".repeat(DouyuSigner.MAX_SCRIPT_CHARS + 1))
        }
    }

    @Test fun rejectsOversizedSignatureResult() {
        assertThrows(IllegalArgumentException::class.java) {
            requireSignatureResultWithinLimit("x".repeat(DouyuSigner.MAX_RESULT_CHARS + 1))
        }
    }
}
