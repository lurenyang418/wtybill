package tech.wtybill.app.player

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tech.wtybill.app.data.douyu.DouyuStreamResolver
import tech.wtybill.app.data.douyu.DouyuSandboxUnsupportedException
import tech.wtybill.app.data.douyu.StreamOptions

sealed class PlaybackError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Resolve(cause: Throwable) : PlaybackError(
        when (cause) {
            is DouyuSandboxUnsupportedException -> cause.message.orEmpty()
            is tech.wtybill.app.data.douyu.DouyuProtocolException -> cause.message ?: "斗鱼取流协议错误"
            else -> "无法获取直播流"
        },
        cause,
    )
    class Unsupported(cause: Throwable) : PlaybackError("当前流格式不受支持", cause)
}

class PlaybackCoordinator(context: Context) {
    private val resolver = DouyuStreamResolver(context)
    private val connection = PlayerConnection(context)
    private val mutex = Mutex()
    private var generation = 0L
    private var activeCandidate: tech.wtybill.app.data.douyu.ResolvedStream? = null

    suspend fun connect() = connection.connect()

    suspend fun loadStreamOptions(roomId: String): StreamOptions = withContext(Dispatchers.IO) {
        resolver.options(roomId)
    }

    suspend fun play(
        roomId: String,
        preferredRate: Int? = null,
        preferredCdn: String? = null,
        skipActiveCandidate: Boolean = false,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val requestGeneration = ++generation
            try {
                val streams = resolver.resolveCandidates(roomId, preferredRate, preferredCdn)
                val candidates = if (skipActiveCandidate) {
                    candidatesAfterFailure(streams, activeCandidate)
                } else streams
                var lastError: Throwable? = null
                for (stream in candidates) {
                    check(requestGeneration == generation) { "stale playback request" }
                    try {
                        activeCandidate = stream
                        // MediaController is bound to the application main looper;
                        // keep network/signature work on IO, but invoke player APIs on Main.
                        withContext(Dispatchers.Main.immediate) {
                            connection.play(stream.url)
                        }
                        return@withContext Result.success(Unit)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        lastError = error
                    }
                }
                throw (lastError ?: IllegalStateException("no playable stream"))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Result.failure(
                    when (error) {
                        is IllegalArgumentException -> PlaybackError.Unsupported(error)
                        else -> PlaybackError.Resolve(error)
                    },
                )
            }
        }
    }

    fun stop() {
        generation++
        activeCandidate = null
        connection.stop()
    }

    fun release() {
        generation++
        activeCandidate = null
        connection.release()
    }
}

/** Keep all deterministic fallbacks, but do not immediately retry the failed pair. */
internal fun candidatesAfterFailure(
    streams: List<tech.wtybill.app.data.douyu.ResolvedStream>,
    failed: tech.wtybill.app.data.douyu.ResolvedStream?,
): List<tech.wtybill.app.data.douyu.ResolvedStream> = failed?.let { candidate ->
    streams.filterNot { it.rate == candidate.rate && it.cdn == candidate.cdn }
} ?: streams
