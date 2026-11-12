package com.mustafoy.transloy.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mustafoy.transloy.model.OcrScriptMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

private val Context.dataStore by preferencesDataStore(name = "transloy_settings")

data class AppSettings(
    val bubbleEnabled: Boolean = true,
    val targetLanguage: String = Locale.getDefault().language.ifBlank { "en" },
    val ocrScriptMode: OcrScriptMode = OcrScriptMode.AUTO_ALL,
    val autoDownloadOfflinePacks: Boolean = true
)

class UserPreferences(private val context: Context) {

    companion object {
        private val KEY_BUBBLE_ENABLED = booleanPreferencesKey("bubble_enabled")
        private val KEY_TARGET_LANG = stringPreferencesKey("target_language")
        private val KEY_OCR_SCRIPT = stringPreferencesKey("ocr_script_mode")
        private val KEY_AUTO_DOWNLOAD = booleanPreferencesKey("auto_download_offline")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val defaultLang = Locale.getDefault().language.ifBlank { "en" }
        val scriptName = prefs[KEY_OCR_SCRIPT] ?: OcrScriptMode.AUTO_ALL.name
        val scriptMode = runCatching { OcrScriptMode.valueOf(scriptName) }
            .getOrDefault(OcrScriptMode.AUTO_ALL)

        AppSettings(
            bubbleEnabled = prefs[KEY_BUBBLE_ENABLED] ?: true,
            targetLanguage = prefs[KEY_TARGET_LANG] ?: defaultLang,
            ocrScriptMode = scriptMode,
            autoDownloadOfflinePacks = prefs[KEY_AUTO_DOWNLOAD] ?: true
        )
    }

    suspend fun setBubbleEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BUBBLE_ENABLED] = enabled }
    }

    suspend fun setTargetLanguage(langCode: String) {
        context.dataStore.edit { it[KEY_TARGET_LANG] = langCode }
    }

    suspend fun setOcrScriptMode(mode: OcrScriptMode) {
        context.dataStore.edit { it[KEY_OCR_SCRIPT] = mode.name }
    }

    suspend fun setAutoDownloadOffline(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_DOWNLOAD] = enabled }
    }
}
