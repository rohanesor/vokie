package com.vokie.ui.communication

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vokie.VokieApplication
import com.vokie.domain.model.*
import com.vokie.stt.SttLanguage
import com.vokie.stt.SttStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class CommunicationViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as VokieApplication
    private val repository = app.messageRepository
    private val manager = app.transportManager
    private val speechToText = app.speechToText

    val messages: StateFlow<List<Message>> = repository.observeMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val peers = manager.peers
    val connectionState = manager.connectionState
    val connectedPeerId = manager.connectedPeerId
    val sttStatus: StateFlow<SttStatus> = speechToText.status
    val selectedSttLanguage: StateFlow<SttLanguage> = speechToText.selectedLanguage
        .stateIn(viewModelScope, SharingStarted.Eagerly, SttLanguage.ENGLISH)
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
    fun installSttModel(uri: Uri) = action { speechToText.installModel(uri) }
    fun startVoice() = action { speechToText.start(selectedSttLanguage.value) }
    fun stopVoice() = action { speechToText.stop() }
    fun retry(messageId: String) = action { repository.retry(messageId) }
    fun clearError() { _error.value = null }
    fun reportError(message: String) { _error.value = message }
    fun discoverabilityRequest() = manager.discoverabilityRequest()
    private fun action(block: suspend () -> Unit) { viewModelScope.launch { runCatching { block() }.onSuccess { _error.value = null }.onFailure { _error.value = it.message ?: "Communication failed" } } }
}
