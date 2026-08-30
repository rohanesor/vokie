package com.vokie.translation

import com.vokie.domain.model.VokieLanguage

enum class ReceiverPresentationState { UNTRANSLATED, TRANSLATING, TRANSLATED, TRANSLATION_UNAVAILABLE, TRANSLATION_FAILED, INVALID_SOURCE_LANGUAGE, INVALID_TARGET_LANGUAGE }
data class ReceiverPresentationOutcome(val presentation: ReceiverPresentation, val isNew: Boolean)

data class ReceiverPresentation(
    val messageId: String,
    val sourceText: String,
    val sourceLanguage: VokieLanguage?,
    val targetLanguage: VokieLanguage?,
    val displayText: String? = null,
    val state: ReceiverPresentationState,
    val error: String? = null,
) {
    val ttsText: String? get() = displayText
    val ttsLanguage: VokieLanguage? get() = targetLanguage
}

/** Receiver-local cache boundary. Source packets and source text are never mutated. */
class ReceiverTranslationCoordinator(private val engine: TranslationEngine) {
    private val cache = mutableMapOf<Key, ReceiverPresentation>()
    private data class Key(val messageId: String, val target: VokieLanguage)

    suspend fun presentOnce(messageId: String, sourceText: String, sourceLanguage: VokieLanguage?, targetLanguage: VokieLanguage?): ReceiverPresentationOutcome {
        if (messageId.isBlank() || sourceText.isBlank() || sourceLanguage == null) return ReceiverPresentationOutcome(ReceiverPresentation(messageId, sourceText, sourceLanguage, targetLanguage, state = ReceiverPresentationState.INVALID_SOURCE_LANGUAGE), true)
        if (targetLanguage == null) return ReceiverPresentationOutcome(ReceiverPresentation(messageId, sourceText, sourceLanguage, null, state = ReceiverPresentationState.INVALID_TARGET_LANGUAGE), true)
        val key = Key(messageId, targetLanguage)
        cache[key]?.let { return ReceiverPresentationOutcome(it, false) }
        val presentation = if (sourceLanguage == targetLanguage) {
            ReceiverPresentation(messageId, sourceText, sourceLanguage, targetLanguage, sourceText, ReceiverPresentationState.TRANSLATED)
        } else when (val result = engine.translate(sourceText, sourceLanguage, targetLanguage)) {
            else -> when (result.status) {
                TranslationStatus.TRANSLATED, TranslationStatus.PASSTHROUGH -> ReceiverPresentation(messageId, sourceText, sourceLanguage, targetLanguage, result.translatedText, ReceiverPresentationState.TRANSLATED)
                TranslationStatus.UNAVAILABLE -> ReceiverPresentation(messageId, sourceText, sourceLanguage, targetLanguage, state = ReceiverPresentationState.TRANSLATION_UNAVAILABLE, error = result.error)
                TranslationStatus.FAILED -> ReceiverPresentation(messageId, sourceText, sourceLanguage, targetLanguage, state = ReceiverPresentationState.TRANSLATION_FAILED, error = result.error)
            }
        }
        cache[key] = presentation
        return ReceiverPresentationOutcome(presentation, true)
    }

    suspend fun present(messageId: String, sourceText: String, sourceLanguage: VokieLanguage?, targetLanguage: VokieLanguage?) = presentOnce(messageId, sourceText, sourceLanguage, targetLanguage).presentation

    suspend fun translate(messageId: String, sourceText: String, sourceLanguage: VokieLanguage, targetLanguage: VokieLanguage): TranslationResult {
        val presentation = present(messageId, sourceText, sourceLanguage, targetLanguage)
        return when (presentation.state) {
            ReceiverPresentationState.TRANSLATED -> TranslationResult(sourceText, sourceLanguage, targetLanguage, presentation.displayText, if (sourceLanguage == targetLanguage) TranslationStatus.PASSTHROUGH else TranslationStatus.TRANSLATED)
            ReceiverPresentationState.TRANSLATION_UNAVAILABLE -> TranslationResult(sourceText, sourceLanguage, targetLanguage, status = TranslationStatus.UNAVAILABLE, error = presentation.error)
            else -> TranslationResult(sourceText, sourceLanguage, targetLanguage, status = TranslationStatus.FAILED, error = presentation.error)
        }
    }
}
