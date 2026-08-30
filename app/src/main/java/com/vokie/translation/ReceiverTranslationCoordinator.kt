package com.vokie.translation

import com.vokie.domain.model.VokieLanguage

/** Receiver-local cache boundary. Source packets and source text are never mutated. */
class ReceiverTranslationCoordinator(private val engine: TranslationEngine) {
    private val cache = mutableMapOf<Key, TranslationResult>()
    private data class Key(val messageId: String, val target: VokieLanguage)

    suspend fun translate(messageId: String, sourceText: String, sourceLanguage: VokieLanguage, targetLanguage: VokieLanguage): TranslationResult {
        require(messageId.isNotBlank())
        require(sourceText.isNotBlank())
        val key = Key(messageId, targetLanguage)
        cache[key]?.let { return it }
        val result = if (sourceLanguage == targetLanguage) {
            TranslationResult(sourceText, sourceLanguage, targetLanguage, sourceText, TranslationStatus.PASSTHROUGH)
        } else engine.translate(sourceText, sourceLanguage, targetLanguage)
        cache[key] = result
        return result
    }
}
