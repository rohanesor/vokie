package com.vokie.stt

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sttPreferences by preferencesDataStore(name = "stt_settings")

enum class SttRecognitionMode { PREFERRED_LANGUAGE, AUTO_DETECT, EXPLICIT_LANGUAGE }

class SttLanguagePreferences(private val context: Context) {
    val selectedLanguage: Flow<SttLanguage> = context.sttPreferences.data.map { values ->
        values[LANGUAGE]?.let(SttLanguage::fromWhisperCode) ?: SttLanguage.AUTO
    }
    // Missing legacy state intentionally means normal production preferred-language mode.
    val recognitionMode: Flow<SttRecognitionMode> = context.sttPreferences.data.map { values ->
        runCatching { SttRecognitionMode.valueOf(values[MODE] ?: SttRecognitionMode.PREFERRED_LANGUAGE.name) }
            .getOrDefault(SttRecognitionMode.PREFERRED_LANGUAGE)
    }

    suspend fun select(language: SttLanguage) {
        context.sttPreferences.edit {
            it[LANGUAGE] = language.whisperCode
            it[MODE] = if (language == SttLanguage.AUTO) SttRecognitionMode.AUTO_DETECT.name else SttRecognitionMode.EXPLICIT_LANGUAGE.name
        }
    }
    suspend fun usePreferredLanguage() = context.sttPreferences.edit { it[MODE] = SttRecognitionMode.PREFERRED_LANGUAGE.name }

    private companion object {
        val LANGUAGE = stringPreferencesKey("language")
        val MODE = stringPreferencesKey("recognition_mode")
    }
}
