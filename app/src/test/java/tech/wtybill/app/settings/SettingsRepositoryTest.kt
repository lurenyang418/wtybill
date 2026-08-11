package tech.wtybill.app.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRepositoryTest {
    @Test fun invalidStoredRangesAreClampedAndMissingValuesUseDefaults() {
        val settings = userSettingsFromPreferences(
            mutablePreferencesOf(
                SettingsKeys.danmakuTextSize to 100,
                SettingsKeys.danmakuOpacity to 0f,
            ),
        )
        assertEquals(40, settings.danmakuTextSize)
        assertEquals(0.1f, settings.danmakuOpacity)
        assertTrue(settings.danmakuEnabled)
        assertFalse(settings.backgroundAudio)
        assertEquals(null, settings.preferredRate)
        assertEquals(null, settings.preferredCdn)
    }
}
