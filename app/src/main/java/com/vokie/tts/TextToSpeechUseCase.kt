package com.vokie.tts

import android.net.Uri
import com.vokie.domain.model.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class TextToSpeechUseCase(
    private val engine: TtsEngine,
    private val modelManager: TtsModelManager,
    private val preferences: TtsPreferences,
    private val queue: TtsPlaybackQueue,
) {
    val status: StateFlow<TtsStatus> = engine.status
    val messageStates: StateFlow<Map<String, MessageTtsState>> = queue.messageStates
    val installedLanguages: StateFlow<Set<TtsLanguage>> = modelManager.installedLanguages
    val speed: Flow<Float> = preferences.speed

    fun start() = queue.start()

    suspend fun installModel(language: TtsLanguage, zipUri: Uri) {
        engine.install(language) { modelManager.installZip(language, zipUri) }
    }

    suspend fun setSpeed(speed: Float) = preferences.setSpeed(speed)

    suspend fun enqueueReceived(message: Message) = queue.enqueue(message.toTtsQueueItem())
    suspend fun play(message: Message) = queue.enqueue(message.toTtsQueueItem())
    suspend fun stop(messageId: String? = null) = queue.stop(messageId)
    suspend fun acknowledgeSos(messageId: String) = queue.stop(messageId, acknowledgedSos = true)

    private fun Message.toTtsQueueItem(): TtsQueueItem {
        val ttsLanguage = TtsLanguage.fromMessageCode(language)
            ?: throw TtsException(TtsErrorCode.UNSUPPORTED_LANGUAGE, "Message language $language is not supported for speech.")
        return TtsQueueItem(id, text, ttsLanguage, messageType)
    }
}
