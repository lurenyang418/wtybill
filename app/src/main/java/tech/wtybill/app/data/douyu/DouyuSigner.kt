package tech.wtybill.app.data.douyu

import android.annotation.SuppressLint
import android.content.Context
import androidx.javascriptengine.IsolateStartupParameters
import androidx.javascriptengine.JavaScriptSandbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import tech.wtybill.app.config.AppConfig

data class DouyuSignature(val fields: Map<String, String>) {
    val sign: String get() = fields["sign"] ?: error("signature has no sign")
}

class DouyuSigner(private val context: Context) {
    suspend fun sign(roomId: String, dynamicScript: String, unixSeconds: Long): DouyuSignature = try {
        signInternal(roomId, dynamicScript, unixSeconds)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: DouyuSignatureException) {
        throw error
    } catch (error: Throwable) {
        throw DouyuSignatureException("斗鱼动态签名执行失败", error)
    }

    @SuppressLint("RequiresFeature")
    private suspend fun signInternal(roomId: String, dynamicScript: String, unixSeconds: Long): DouyuSignature = withContext(Dispatchers.IO) {
        requireDynamicScriptWithinLimit(dynamicScript)
        require(unixSeconds > 0L) { "signature timestamp must be positive" }
        if (!JavaScriptSandbox.isSupported()) throw DouyuSandboxUnsupportedException()
        val sandbox = JavaScriptSandbox.createConnectedInstanceAsync(context).get(10, TimeUnit.SECONDS)
        try {
            val startup = IsolateStartupParameters()
            if (!sandbox.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_ISOLATE_MAX_HEAP_SIZE) ||
                !sandbox.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_EVALUATE_WITHOUT_TRANSACTION_LIMIT)
            ) {
                throw DouyuSandboxUnsupportedException()
            }
            startup.setMaxHeapSizeBytes(16L * 1024 * 1024)
            startup.setMaxEvaluationReturnSizeBytes(64 * 1024)
            val isolate = sandbox.createIsolate(startup)
            try {
                val crypto = context.assets.open("js/crypto-js-4.2.0.js").bufferedReader().use { it.readText() }
                isolate.evaluateJavaScriptAsync(crypto).get(5, TimeUnit.SECONDS)
                check(isolate.evaluateJavaScriptAsync("CryptoJS.MD5('abc').toString()").get(5, TimeUnit.SECONDS) == "900150983cd24fb0d6963f7d28e17f72") {
                    "CryptoJS self-test failed"
                }
                isolate.evaluateJavaScriptAsync(dynamicScript).get(5, TimeUnit.SECONDS)
                val call = "JSON.stringify(ub98484234(${jsString(roomId)},${jsString(DID)},$unixSeconds))"
                val encoded = isolate.evaluateJavaScriptAsync(call).get(5, TimeUnit.SECONDS)
                requireSignatureResultWithinLimit(encoded)
                validateDouyuSignature(parseDouyuForm(encoded.trim().trim('"')), unixSeconds)
            } finally {
                isolate.close()
            }
        } finally {
            sandbox.close()
        }
    }

    private fun jsString(value: String): String = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

    companion object {
        const val DID = AppConfig.DOUYU_DID
        const val MAX_SCRIPT_CHARS = 512 * 1024
        const val MAX_RESULT_CHARS = 64 * 1024
    }
}

internal fun requireDynamicScriptWithinLimit(script: String) {
    require(script.length <= DouyuSigner.MAX_SCRIPT_CHARS) { "signature script is too large" }
}

internal fun requireSignatureResultWithinLimit(result: String) {
    require(result.length <= DouyuSigner.MAX_RESULT_CHARS) { "signature result is too large" }
}

internal fun parseDouyuForm(value: String): Map<String, String> = value.split('&')
    .asSequence().filter { it.isNotEmpty() }.mapNotNull { part ->
        val separator = part.indexOf('=')
        if (separator <= 0) return@mapNotNull null
        val key = URLDecoder.decode(part.substring(0, separator), StandardCharsets.UTF_8)
        val field = URLDecoder.decode(part.substring(separator + 1), StandardCharsets.UTF_8)
        key to field
    }.toMap()

internal fun validateDouyuSignature(fields: Map<String, String>, unixSeconds: Long): DouyuSignature {
    val signature = DouyuSignature(fields)
    check(fields["did"] == DouyuSigner.DID) { "signature DID mismatch" }
    check(fields["tt"] == unixSeconds.toString()) { "signature timestamp mismatch" }
    check(signature.sign.matches(Regex("[0-9a-fA-F]{32}"))) { "invalid signature format" }
    return signature
}
