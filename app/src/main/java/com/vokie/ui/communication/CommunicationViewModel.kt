package com.vokie.ui.communication

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vokie.VokieApplication
import com.vokie.communication.CommunicationPreferences
import com.vokie.domain.model.*
import com.vokie.stt.SttLanguage
import com.vokie.stt.SttStatus
import com.vokie.tts.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class CommunicationViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as VokieApplication
    private val repository = app.messageRepository
    private val manager = app.transportManager
    private val speechToText = app.speechToText
    private val textToSpeech = app.textToSpeech
    private val communicationPreferences = app.communicationPreferences

    val pushToTalkEnabled: StateFlow<Boolean> = communicationPreferences.pushToTalkEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val messages: StateFlow<List<Message>> = repository.observeMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val peers = manager.peers
    val connectionState = manager.connectionState
    val connectedPeerId = manager.connectedPeerId
    val sttStatus: StateFlow<SttStatus> = speechToText.status
    val selectedSttLanguage: StateFlow<SttLanguage> = speechToText.selectedLanguage
        .stateIn(viewModelScope, SharingStarted.Eagerly, SttLanguage.ENGLISH)
    val ttsStatus: StateFlow<TtsStatus> = textToSpeech.status
    val messageTtsStates: StateFlow<Map<String, MessageTtsState>> = textToSpeech.messageStates
    val installedTtsLanguages: StateFlow<Set<TtsLanguage>> = textToSpeech.installedLanguages
    val ttsSpeed: StateFlow<Float> = textToSpeech.speed.stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_TTS_SPEED)
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun startListening() = action { manager.startBluetoothListener() }
    fun discover() = action { manager.discoverBluetooth() }
    fun stopDiscovery() = action { manager.stopDiscovery() }
    fun connect(peerId: String) = action { manager.connectBluetooth(peerId) }
    fun disconnect() = action { manager.disconnect() }

    fun send(text: String, language: VokieLanguage = selectedSttLanguage.value.messageLanguage, type: MessageType = MessageType.TEXT, onQueued: () -> Unit = {}) {
        if (text.isBlank()) { _error.value = "Enter a message before sending."; return }
        viewModelScope.launch {
            runCatching { repository.createMessage(text, app.deviceId, connectedPeerId.value, language, type) }
                .onSuccess { _error.value = null; onQueued() }
                .onFailure { _error.value = it.message ?: "Message could not be queued" }
        }
    }

    fun selectSttLanguage(language: SttLanguage) = action { speechToText.selectLanguage(language) }
    fun initializeStt() = action { speechToText.initialize() }
    fun startVoice() = action { speechToText.start(selectedSttLanguage.value) }
    fun stopVoice() = action { speechToText.stop() }
    fun setTtsSpeed(speed: Float) = action { textToSpeech.setSpeed(speed) }
    fun playMessage(message: Message) = action { textToSpeech.play(message) }
    fun stopMessage(messageId: String) = action { textToSpeech.stop(messageId) }
    fun stopTts() = action { textToSpeech.stop() }
    fun acknowledgeSos(messageId: String) = action { textToSpeech.acknowledgeSos(messageId) }
    fun isIncoming(message: Message) = message.senderId != app.deviceId
    fun setPushToTalk(enabled: Boolean) = action { communicationPreferences.setPushToTalk(enabled) }
    fun retry(messageId: String) = action { repository.retry(messageId) }
    fun clearError() { _error.value = null }
    fun reportError(message: String) { _error.value = message }
    fun discoverabilityRequest() = manager.discoverabilityRequest()
    private fun action(block: suspend () -> Unit) { viewModelScope.launch { runCatching { block() }.onSuccess { _error.value = null }.onFailure { _error.value = it.message ?: "Communication failed" } } }
}
