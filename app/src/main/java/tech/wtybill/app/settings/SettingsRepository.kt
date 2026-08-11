package tech.wtybill.app.settings

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tech.wtybill.app.config.AppConfig

private val Context.wtybillDataStore by preferencesDataStore(
    name = AppConfig.SETTINGS_DATASTORE_NAME,
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

data class UserSettings(
    val danmakuEnabled: Boolean = true,
    val danmakuTextSize: Int = 16,
    val danmakuOpacity: Float = 0.85f,
    val backgroundAudio: Boolean = false,
    val preferredRate: Int? = null,
    val preferredCdn: String? = null,
)

internal object SettingsKeys {
    val danmakuEnabled = booleanPreferencesKey("danmaku_enabled")
    val danmakuTextSize = intPreferencesKey("danmaku_text_size")
    val danmakuOpacity = floatPreferencesKey("danmaku_opacity")
    val backgroundAudio = booleanPreferencesKey("background_audio")
    val preferredRate = intPreferencesKey("preferred_rate")
    val preferredCdn = androidx.datastore.preferences.core.stringPreferencesKey("preferred_cdn")
}

internal fun userSettingsFromPreferences(prefs: Preferences): UserSettings = UserSettings(
    danmakuEnabled = prefs[SettingsKeys.danmakuEnabled] ?: true,
    danmakuTextSize = (prefs[SettingsKeys.danmakuTextSize] ?: 16).coerceIn(10, 40),
    danmakuOpacity = (prefs[SettingsKeys.danmakuOpacity] ?: 0.85f).coerceIn(0.1f, 1f),
    backgroundAudio = prefs[SettingsKeys.backgroundAudio] ?: false,
    preferredRate = prefs[SettingsKeys.preferredRate],
    preferredCdn = prefs[SettingsKeys.preferredCdn],
)

class SettingsRepository(private val context: Context) {
    val settings: Flow<UserSettings> = context.wtybillDataStore.data.map(::userSettingsFromPreferences)

    suspend fun setDanmakuEnabled(value: Boolean) = context.wtybillDataStore.edit { it[SettingsKeys.danmakuEnabled] = value }
    suspend fun setDanmakuTextSize(value: Int) = context.wtybillDataStore.edit { it[SettingsKeys.danmakuTextSize] = value.coerceIn(10, 40) }
    suspend fun setDanmakuOpacity(value: Float) = context.wtybillDataStore.edit { it[SettingsKeys.danmakuOpacity] = value.coerceIn(0.1f, 1f) }
    suspend fun setBackgroundAudio(value: Boolean) = context.wtybillDataStore.edit { it[SettingsKeys.backgroundAudio] = value }
    suspend fun setPreferredStream(rate: Int?, cdn: String?) = context.wtybillDataStore.edit {
        if (rate == null) it.remove(SettingsKeys.preferredRate) else it[SettingsKeys.preferredRate] = rate
        if (cdn == null) it.remove(SettingsKeys.preferredCdn) else it[SettingsKeys.preferredCdn] = cdn
    }
}
