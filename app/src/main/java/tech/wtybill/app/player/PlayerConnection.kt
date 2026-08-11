package tech.wtybill.app.player

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@SuppressLint("UnsafeOptInUsageError")
class PlayerConnection(context: Context) {
    private val appContext = context.applicationContext
    private var controller: MediaController? = null

    suspend fun connect(): MediaController = withContext(Dispatchers.IO) {
        controller ?: MediaController.Builder(
            appContext,
            SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java)),
        ).buildAsync().get(10, TimeUnit.SECONDS).also { controller = it }
    }

    fun play(url: String) {
        val mediaController = checkNotNull(controller) { "player is not connected" }
        require(!url.startsWith("rtmp://")) { "RTMP stream requires a dedicated player extension" }
        val mimeType = if (url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) {
            MimeTypes.APPLICATION_M3U8
        } else {
            MimeTypes.VIDEO_FLV
        }
        mediaController.setMediaItem(MediaItem.Builder().setUri(url).setMimeType(mimeType).build())
        mediaController.prepare()
        mediaController.play()
    }

    fun stop() {
        controller?.stop()
        controller?.clearMediaItems()
    }

    fun release() {
        controller?.release()
        controller = null
    }
}
