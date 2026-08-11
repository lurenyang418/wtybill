package tech.wtybill.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityLifecycleTest {
    @Test
    fun configurationRecreationKeepsPlaybackServiceAlive() {
        assertFalse(
            shouldReleasePlaybackOnStop(
                isChangingConfigurations = true,
                isInPictureInPictureMode = false,
                enteringPictureInPicture = false,
                allowBackgroundAudio = false,
            ),
        )
    }

    @Test
    fun normalBackgroundWithoutAudioReleasesPlayback() {
        assertTrue(
            shouldReleasePlaybackOnStop(
                isChangingConfigurations = false,
                isInPictureInPictureMode = false,
                enteringPictureInPicture = false,
                allowBackgroundAudio = false,
            ),
        )
    }

    @Test
    fun pictureInPictureKeepsPlaybackServiceAlive() {
        assertFalse(
            shouldReleasePlaybackOnStop(
                isChangingConfigurations = false,
                isInPictureInPictureMode = true,
                enteringPictureInPicture = false,
                allowBackgroundAudio = false,
            ),
        )
    }

    @Test
    fun backgroundAudioKeepsPlaybackServiceAlive() {
        assertFalse(
            shouldReleasePlaybackOnStop(
                isChangingConfigurations = false,
                isInPictureInPictureMode = false,
                enteringPictureInPicture = false,
                allowBackgroundAudio = true,
            ),
        )
    }
}
