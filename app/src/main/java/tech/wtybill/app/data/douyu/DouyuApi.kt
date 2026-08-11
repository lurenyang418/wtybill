package tech.wtybill.app.data.douyu

import android.net.Uri
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.URI
import java.util.concurrent.TimeUnit
import tech.wtybill.app.config.AppConfig

class DouyuApi(
    private val baseClient: OkHttpClient,
    private val baseUrl: String = AppConfig.DOUYU_BASE_URL,
    private val apiHost: String = AppConfig.DOUYU_HOST,
    private val connectTimeoutMs: Long = 8_000,
    private val readTimeoutMs: Long = 12_000,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val apiClient = baseClient.newBuilder()
        .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            if (request.url.host == apiHost) {
                chain.proceed(request.newBuilder()
                    .header("Referer", AppConfig.DOUYU_REFERER)
                    .header("User-Agent", "Mozilla/5.0 (Android 13; Mobile) ${AppConfig.PROJECT_NAME}/0.1")
                    .build())
            } else chain.proceed(request)
        }.build()

    suspend fun room(roomId: String): RoomInfo {
        val root = getJson("/betard/$roomId").unwrapString()
        val room = root.jsonObject["room"]?.jsonObject
            ?: throw DouyuProtocolException("斗鱼房间响应缺少 room")
        return RoomInfo(
            roomId = room.string("room_id") ?: roomId,
            title = room.string("room_name").orEmpty(),
            anchorName = room.string("owner_name").orEmpty(),
            anchorAvatar = safeHttpsUrl(room.string("owner_avatar") ?: room.obj("avatar")?.string("big")),
            cover = safeHttpsUrl(room.string("room_pic")),
            hot = room.number("room_biz_all", "hot"),
            introduction = room.string("show_details"),
            showStatus = room.int("show_status"),
            videoLoop = room.int("videoLoop"),
            status = room.int("status"),
        )
    }

    suspend fun showTime(roomId: String): Long? = getJson("/swf_api/h5room/$roomId")
        .jsonObject["data"]?.jsonObject?.get("show_time")?.jsonPrimitive?.content?.toLongOrNull()

    suspend fun signingScript(roomId: String): String {
        val root = getJson("/swf_api/homeH5Enc?rids=$roomId")
        val endpointError = root.jsonObject["error"]?.jsonPrimitive?.content
        if (endpointError != "0") throw DouyuProtocolException("斗鱼签名接口错误: ${endpointError ?: "missing error"}")
        return root.jsonObject["data"]?.jsonObject?.get("room$roomId")?.jsonPrimitive?.content
            ?: throw DouyuProtocolException("斗鱼签名响应缺少脚本")
    }

    suspend fun getH5Play(roomId: String, fields: Map<String, String>): JsonElement {
        val body = FormBody.Builder().apply { fields.forEach { (key, value) -> add(key, value) } }.build()
        val request = Request.Builder().url("$baseUrl/lapi/live/getH5Play/$roomId").post(body).build()
        return apiClient.newCall(request).execute().use {
            val root = it.requireJson()
            val error = root.jsonObject["error"]?.jsonPrimitive?.content
            if (error != null && error != "0") {
                throw DouyuProtocolException("斗鱼取流接口错误: $error")
            }
            root
        }
    }

    private fun getJson(path: String): JsonElement {
        val request = Request.Builder().url(baseUrl + path).get().build()
        return apiClient.newCall(request).execute().use { it.requireJson() }
    }

    private fun Response.requireJson(): JsonElement {
        if (!isSuccessful) throw DouyuHttpException(code)
        val raw = body.string()
        return try {
            json.parseToJsonElement(raw)
        } catch (error: Throwable) {
            throw DouyuProtocolException("斗鱼接口返回了无效 JSON", error)
        }
    }

    private fun JsonElement.unwrapString(): JsonElement =
        if (this is kotlinx.serialization.json.JsonPrimitive && isString) json.parseToJsonElement(content) else this

    private fun JsonElement.string(key: String): String? = jsonObject[key]?.jsonPrimitive?.content
    private fun JsonElement.obj(key: String): JsonElement? = jsonObject[key]?.takeIf { it is kotlinx.serialization.json.JsonObject }
    private fun safeHttpsUrl(value: String?): String? = value?.trim()?.takeIf {
        runCatching {
            val uri = URI(it)
            uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
        }.getOrDefault(false)
    }
    private fun JsonElement.int(key: String): Int = string(key)?.toIntOrNull() ?: 0
    private fun JsonElement.number(parent: String, key: String): Long =
        jsonObject[parent]?.jsonObject?.get(key)?.jsonPrimitive?.content?.toLongOrNull() ?: 0L

    companion object { const val BASE = AppConfig.DOUYU_BASE_URL }
}
