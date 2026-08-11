package tech.wtybill.app.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel

/**
 * Keeps the single playback coordinator across Activity configuration changes.
 * The MediaSessionService owns the ExoPlayer; this ViewModel owns only the
 * controller/recovery coordinator used to reach that service.
 */
class PlaybackViewModel(application: Application) : AndroidViewModel(application) {
    val coordinator: PlaybackCoordinator = PlaybackCoordinator(application)
    var activeRoomId: String? = null
        private set

    val hasActivePlayback: Boolean
        get() = activeRoomId != null

    fun markPlaybackStarted(roomId: String) {
        activeRoomId = roomId
    }

    fun markPlaybackStopped() {
        activeRoomId = null
    }

    override fun onCleared() {
        coordinator.release()
        super.onCleared()
    }
}
