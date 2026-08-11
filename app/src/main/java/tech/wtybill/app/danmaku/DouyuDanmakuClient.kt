package tech.wtybill.app.danmaku

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import tech.wtybill.app.net.NetworkClients
import tech.wtybill.app.config.AppConfig
import java.util.concurrent.TimeUnit
import kotlin.math.min

sealed class DanmakuError(message: String, cause: Throwable? = null) : Exception(message, cause)
class DanmakuTransportError(cause: Throwable) : DanmakuError("弹幕连接失败", cause)
class DanmakuProtocolError(cause: Throwable) : DanmakuError("弹幕数据解析失败", cause)

class DouyuDanmakuClient(
    private val scope: CoroutineScope,
    private val onMessage: (DanmakuMessage) -> Unit,
    private val onError: (DanmakuError) -> Unit = {},
    private val endpoint: String = AppConfig.DOUYU_DANMAKU_ENDPOINT,
    httpClient: OkHttpClient? = null,
    socketFactoryOverride: DanmakuSocketFactory? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val reconnectBaseDelayMs: Long = 1_000L,
) {
    private val client = (httpClient ?: NetworkClients.base).newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    private val socketFactory = socketFactoryOverride ?: DanmakuSocketFactory { request, listener ->
        client.newWebSocket(request, listener)
    }
    private var socket: WebSocket? = null
    private var heartbeat: Job? = null
    private var reconnect: Job? = null
    private var retry = 0
    private var remainder = ByteArray(0)
    private var activeRoom: String? = null
    private var connectionGeneration = 0L

    fun start(roomId: String) {
        val generation = ++connectionGeneration
        reconnect?.cancel()
        socket?.cancel()
        heartbeat?.cancel()
        remainder = ByteArray(0)
        activeRoom = roomId
        connect(roomId, generation)
    }

    fun stop() {
        activeRoom = null
        connectionGeneration++
        reconnect?.cancel()
        heartbeat?.cancel()
        socket?.close(1000, "closed")
        socket = null
    }

    private fun connect(roomId: String, generation: Long) {
        val request = Request.Builder().url(endpoint).build()
        socket = socketFactory.open(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!isCurrent(roomId, generation)) {
                    webSocket.cancel()
                    return
                }
                retry = 0
                webSocket.send(DouyuPacketCodec.encode("type@=loginreq/roomid@=$roomId/").toByteString())
                webSocket.send(DouyuPacketCodec.encode("type@=joingroup/rid@=$roomId/gid@=-9999/").toByteString())
                heartbeat = scope.launch(ioDispatcher) {
                    while (isActive) {
                        delay(45_000)
                        webSocket.send(DouyuPacketCodec.encode("type@=mrkl/").toByteString())
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                runCatching {
                    val result = DouyuPacketCodec.decode(remainder + bytes.toByteArray())
                    remainder = result.remainder
                    result.bodies.flatMapTo(mutableListOf(), DouyuPacketCodec::chatMessages).forEach(onMessage)
                }.onFailure {
                    onError(DanmakuProtocolError(it))
                    // A malformed frame can leave the parser at an unknown
                    // boundary. Drop this socket and let the normal bounded
                    // reconnect path establish a clean stream.
                    if (isCurrent(roomId, generation)) {
                        webSocket.cancel()
                        reconnect(roomId, generation)
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                heartbeat?.cancel()
                if (!isCurrent(roomId, generation)) return
                onError(DanmakuTransportError(t))
                reconnect(roomId, generation)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                heartbeat?.cancel()
                if (isCurrent(roomId, generation)) reconnect(roomId, generation)
            }
        })
    }

    private fun reconnect(roomId: String, generation: Long) {
        if (!isCurrent(roomId, generation)) return
        if (reconnect?.isActive == true) return
        val delayMs = min(60_000L, reconnectBaseDelayMs shl min(retry++, 6))
        reconnect = scope.launch(ioDispatcher) {
            delay(delayMs)
            if (isCurrent(roomId, generation)) connect(roomId, generation)
        }
    }

    private fun isCurrent(roomId: String, generation: Long): Boolean =
        activeRoom == roomId && connectionGeneration == generation
}

fun interface DanmakuSocketFactory {
    fun open(request: Request, listener: WebSocketListener): WebSocket
}
