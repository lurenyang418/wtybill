package tech.wtybill.app.data.douyu

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tech.wtybill.app.net.NetworkClients

class DouyuStreamResolver(context: Context) {
    private val api = DouyuApi(NetworkClients.base)
    private val signer = DouyuSigner(context)

    suspend fun resolve(roomId: String, preferredRate: Int? = null, preferredCdn: String? = null): ResolvedStream = withContext(Dispatchers.IO) {
        resolveCandidates(roomId, preferredRate, preferredCdn).first()
    }

    suspend fun options(roomId: String): StreamOptions = withContext(Dispatchers.IO) {
        val script = api.signingScript(roomId)
        val signature = signer.sign(roomId, script, System.currentTimeMillis() / 1000)
        DouyuStreamParsing.parseOptions(api.getH5Play(roomId, DouyuStreamRequest.fields(signature.fields)))
    }

    suspend fun resolveCandidates(
        roomId: String,
        preferredRate: Int? = null,
        preferredCdn: String? = null,
    ): List<ResolvedStream> = withContext(Dispatchers.IO) {
        val script = api.signingScript(roomId)
        val firstSignature = signer.sign(roomId, script, System.currentTimeMillis() / 1000)
        val optionsResponse = api.getH5Play(roomId, DouyuStreamRequest.fields(firstSignature.fields))
        val options = DouyuStreamParsing.parseOptions(optionsResponse)
        DouyuStreamParsing.requirePlayableOptions(options)
        val rates = options.rates.orderByPreferred(preferredRate) { it.rate } // Keep server order after the preference.
        val cdns = options.cdns.orderByPreferred(preferredCdn) { it.code }
        val candidates = mutableListOf<ResolvedStream>()
        var lastError: Throwable? = null
        // Bound the work: one preferred pair plus a small, deterministic fallback budget.
        for (rate in rates.take(MAX_RATES)) {
            for (cdn in cdns.take(MAX_CDNS)) {
                try {
                    val signature = signer.sign(roomId, script, System.currentTimeMillis() / 1000)
                    val streamResponse = api.getH5Play(
                        roomId,
                        DouyuStreamRequest.fields(signature.fields, cdn.code, rate.rate),
                    )
                    val url = DouyuStreamParsing.streamUrl(streamResponse)
                    candidates += ResolvedStream(url, rate.rate, cdn.code)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    lastError = error
                }
            }
        }
        if (candidates.isEmpty()) {
            throw (lastError ?: IllegalStateException("no playable stream candidates"))
        }
        candidates
    }

    private fun <T, K> List<T>.orderByPreferred(preferred: K?, key: (T) -> K): List<T> =
        if (preferred == null) this else filter { key(it) == preferred } + filter { key(it) != preferred }

    companion object {
        private const val MAX_RATES = 2
        private const val MAX_CDNS = 4
    }
}

internal object DouyuStreamRequest {
    private val defaultFields = mapOf(
        "cdn" to "",
        "rate" to "-1",
        "ver" to "Douyu_223061205",
        "iar" to "1",
        "ive" to "1",
        "hevc" to "0",
        "fa" to "0",
    )

    fun fields(signature: Map<String, String>, cdn: String = "", rate: Int? = null): Map<String, String> =
        signature + defaultFields + mapOf(
            "cdn" to cdn,
            "rate" to (rate?.toString() ?: "-1"),
        )
}

internal object DouyuStreamParsing {
    fun parseOptions(root: JsonElement): StreamOptions {
        val data = root.jsonObject["data"]?.jsonObject ?: error("missing stream options")
        val rates = data["multirates"]?.jsonArray.orEmpty().mapNotNull {
            val obj = it as? JsonObject ?: return@mapNotNull null
            val name = obj.string("name") ?: return@mapNotNull null
            val rate = obj.value("rate")?.toIntOrNull() ?: return@mapNotNull null
            StreamRate(name, rate)
        }
        val cdns = data["cdnsWithName"]?.jsonArray.orEmpty().mapNotNull {
            val obj = it as? JsonObject ?: return@mapNotNull null
            val code = obj.string("cdn") ?: return@mapNotNull null
            CdnLine(code, obj.string("name"))
        }.let { lines -> lines.filterNot { it.code.startsWith("scdn") } + lines.filter { it.code.startsWith("scdn") } }
        return StreamOptions(rates, cdns)
    }

    fun streamUrl(root: JsonElement): String {
        val data = root.jsonObject["data"]?.jsonObject ?: error("missing stream data")
        val base = data.string("rtmp_url") ?: error("missing rtmp_url")
        val leaf = htmlUnescape(data.string("rtmp_live") ?: error("missing rtmp_live"))
        val url = base.trimEnd('/') + "/" + leaf.trimStart('/')
        require(url.startsWith("http://") || url.startsWith("https://") || url.startsWith("rtmp://")) { "unsupported stream scheme" }
        return url
    }

    fun requirePlayableOptions(options: StreamOptions): StreamOptions {
        require(options.rates.isNotEmpty()) { "no stream rates" }
        require(options.cdns.isNotEmpty()) { "no CDN lines" }
        return options
    }

    private fun JsonElement.string(key: String): String? = runCatching {
        jsonObject[key]?.jsonPrimitive?.takeIf { it.isString }?.content
    }.getOrNull()

    private fun JsonObject.value(key: String): String? = runCatching {
        this[key]?.jsonPrimitive?.content
    }.getOrNull()

    private fun htmlUnescape(value: String): String = value
        .replace("&amp;", "&")
        .replace("&#47;", "/")
        .replace("&#x2F;", "/", ignoreCase = true)
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
}
