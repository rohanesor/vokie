package com.vokie.stt

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sttPreferences by preferencesDataStore(name = "stt_settings")

class SttLanguagePreferences(private val context: Context) {
    val selectedLanguage: Flow<SttLanguage> = context.sttPreferences.data.map { values ->
        values[LANGUAGE]?.let(SttLanguage::fromWhisperCode) ?: SttLanguage.AUTO
    }

    suspend fun select(language: SttLanguage) {
        context.sttPreferences.edit { it[LANGUAGE] = language.whisperCode }
    }

    private companion object { val LANGUAGE = stringPreferencesKey("language") }
}
