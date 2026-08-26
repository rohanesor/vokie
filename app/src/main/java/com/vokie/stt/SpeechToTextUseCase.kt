package com.vokie.stt

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** UI-facing orchestration boundary; transport remains text-only and is intentionally absent here. */
class SpeechToTextUseCase(
    private val engine: WhisperSttEngine,
    private val languagePreferences: SttLanguagePreferences,
) {
    val status: StateFlow<SttStatus> = engine.status
    val selectedLanguage: Flow<SttLanguage> = languagePreferences.selectedLanguage

    suspend fun initialize() = engine.initialize()
    suspend fun selectLanguage(language: SttLanguage) = languagePreferences.select(language)
    suspend fun start(language: SttLanguage) = engine.start(language)
    suspend fun stop() = engine.stop()
}
