package com.vokie.ui.communication

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vokie.BuildConfig
import com.vokie.VokieApplication
import com.vokie.communication.CommunicationPreferences
import com.vokie.communication.PeerSessionState
import com.vokie.communication.VokieLog
import com.vokie.domain.model.*
import com.vokie.models.DownloadState
import com.vokie.location.LocationPacket
import com.vokie.location.LocationAvailability
import com.vokie.stt.SttLanguage
import com.vokie.stt.TurnEvent
import com.vokie.stt.TurnMode
import com.vokie.stt.SttStatus
import com.vokie.stt.UserLanguageProfile
import com.vokie.stt.resolveProductionSttLanguage
import com.vokie.tts.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class CommunicationViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as VokieApplication
    private val repository = app.messageRepository
    private val manager = app.transportManager
    private val speechToText = app.speechToText
    private val turnManager = app.continuousTurnManager
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
    val preferredLanguage: StateFlow<UserLanguageProfile?> = app.userLanguageProfilePreferences.profile
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _debugFastSttEnabled = MutableStateFlow(false)
    /** Debug-only product experiment; Release always uses the selected STT mode. */
    val debugFastSttEnabled: StateFlow<Boolean> = _debugFastSttEnabled.asStateFlow()
    val ttsStatus: StateFlow<TtsStatus> = textToSpeech.status
    val messageTtsStates: StateFlow<Map<String, MessageTtsState>> = textToSpeech.messageStates
    val installedTtsLanguages: StateFlow<Set<TtsLanguage>> = textToSpeech.installedLanguages
    val ttsSpeed: StateFlow<Float> = textToSpeech.speed.stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_TTS_SPEED)
    val modelDownloadState: StateFlow<DownloadState> = app.modelDownloads.state
    val telemetryLocation = app.locationProvider.location
    val telemetryHeading = app.headingProvider.reading
    val telemetryRssi = app.rssiTelemetry.state
    val telemetryGuidance = app.emergencyGuidance.state
    val proximityMeasurements = app.proximityMeasurements.latest
    val localizationStates = app.localizationEngine.states
    val receiverPresentations = app.receiverPresentations
    // Wi-Fi Direct remains a Debug-only physical-validation path until its lifecycle is proven.
    val wifiDirectState = app.wifiDirectTransport.state
    val wifiDirectPeers = app.wifiDirectTransport.peers
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _locationShareState = MutableStateFlow("IDLE")
    val locationShareState: StateFlow<String> = _locationShareState.asStateFlow()

    /** Peer-scoped session state from the PeerSessionManager. */
    val peerSessions: StateFlow<Map<String, PeerSessionState>> = app.peerSessionManager.sessions

    /** The currently selected peer for conversation filtering. Defaults to connectedPeerId. */
    private val _selectedPeerId = MutableStateFlow<String?>(null)
    val selectedPeerId: StateFlow<String?> = _selectedPeerId.asStateFlow()

    /**
     * The effective peer for message filtering: explicit selection takes priority,
     * falling back to the transport-level connected peer for backward compatibility.
     */
    val effectivePeerId: StateFlow<String?> = combine(_selectedPeerId, connectedPeerId) { selected, connected ->
        selected ?: connected
    }.stateIn(viewModelScope, SharingStarted.Eagerly, connectedPeerId.value)

    /** Messages filtered to the currently effective peer. */
    val selectedPeerMessages: StateFlow<List<Message>> = combine(messages, effectivePeerId) { allMessages, peerId ->
        if (peerId == null) allMessages
        else allMessages.filter { it.senderId == peerId || it.receiverId == peerId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectPeer(peerId: String?) {
        _selectedPeerId.value = peerId
        VokieLog.rescue("SELECTED_PEER peer=${peerId ?: "NONE"}")
    }

    init {
        // C2 is the sole production owner of microphone/STT turns. Sentence events replace
        // ChatScreen's former raw SttStatus RESULT enqueue observer.
        viewModelScope.launch {
            turnManager.events.collect { event ->
                if (event is TurnEvent.Sentence) {
                    runCatching {
                        val message = repository.createMessage(event.text, app.deviceId, effectivePeerId.value, event.language)
                        app.turnTiming.associateMessage(event.turnId, message.id)
                    }.onFailure { _error.value = it.message ?: "Voice message could not be queued" }
                }
            }
        }
    }

    fun startListening() = action { manager.startBluetoothListener() }
    fun discover() = action { manager.discoverBluetooth() }
    fun stopDiscovery() = action { manager.stopDiscovery() }
    fun connect(peerId: String) = action { manager.connectBluetooth(peerId) }
    fun disconnect() = action { manager.disconnect() }
    fun discoverWifiDirect() = action { manager.discoverWifiDirect() }
    fun connectWifiDirect(address: String) = action { manager.connectWifiDirect(address) }
    fun disconnectWifiDirect() = action { manager.disconnectWifiDirect() }

    fun send(text: String, language: VokieLanguage? = null, type: MessageType = MessageType.TEXT, onQueued: () -> Unit = {}) {
        if (text.isBlank()) { _error.value = "Enter a message before sending."; return }
        val resolvedLanguage = language ?: sttStatus.value.result?.language?.messageLanguage
            ?: if (type == MessageType.SOS) VokieLanguage.EN else null
        if (resolvedLanguage == null) { _error.value = "Choose the language you speak before sending a message."; return }
        // Use the effective peer (explicit selection or transport-level connected peer).
        val targetPeer = effectivePeerId.value
        viewModelScope.launch {
            runCatching { repository.createMessage(text, app.deviceId, targetPeer, resolvedLanguage, type) }
                .onSuccess { _error.value = null; onQueued() }
                .onFailure { _error.value = it.message ?: "Message could not be queued" }
        }
    }

    fun selectPreferredLanguage(language: UserLanguageProfile) = action {
        app.userLanguageProfilePreferences.select(language)
    }
    fun enqueueWhisperResult(result: com.vokie.stt.SttResult) = viewModelScope.launch {
        runCatching { app.enqueueWhisperTranscript(result, effectivePeerId.value) }
            .onSuccess { _error.value = null }
            .onFailure { _error.value = it.message ?: "Voice message could not be queued" }
    }
    fun initializeStt() = action { speechToText.initialize() }
    fun resetOnboardingForDebug() {
        if (BuildConfig.DEBUG) action { app.userLanguageProfilePreferences.clear() }
    }
    fun setDebugFastStt(enabled: Boolean) {
        if (BuildConfig.DEBUG) _debugFastSttEnabled.value = enabled
    }
    fun startVoice() = action {
        val preferred = preferredLanguage.value ?: throw IllegalStateException("Choose your language before recording.")
        turnManager.start(if (pushToTalkEnabled.value) TurnMode.PUSH_TO_TALK else TurnMode.CONTINUOUS, resolveProductionSttLanguage(preferred), preferred)
    }
    fun stopVoice() = action { turnManager.stop() }
    fun replayLastPcmBenchmark() = action {
        val preferred = preferredLanguage.value ?: throw IllegalStateException("Select a preferred language before replaying.")
        app.sttEngine.replayLastCaptureForBenchmark(resolveProductionSttLanguage(preferred), preferred)
    }
    fun setTtsSpeed(speed: Float) = action { textToSpeech.setSpeed(speed) }
    fun downloadTtsLanguage(language: TtsLanguage) = action {
        app.modelDownloads.download(language)
        textToSpeech.refreshDownloadedLanguage(language)
    }
    fun playMessage(message: Message) = action { textToSpeech.play(message) }
    fun stopMessage(messageId: String) = action { textToSpeech.stop(messageId) }
    fun stopTts() = action { textToSpeech.stop() }
    fun acknowledgeSos(messageId: String) = action { textToSpeech.acknowledgeSos(messageId) }
    fun isIncoming(message: Message) = message.senderId != app.deviceId
    fun setPushToTalk(enabled: Boolean) = action { communicationPreferences.setPushToTalk(enabled) }
    fun startTelemetryLocation() = app.locationProvider.start()
    fun stopTelemetryLocation() = app.locationProvider.stop()
    fun shareCurrentLocation() = viewModelScope.launch {
        _locationShareState.value = "SHARING"
        runCatching {
            val fix = app.locationProvider.location.value
            check(fix.availability == LocationAvailability.AVAILABLE) { "Your location unavailable." }
            val transmission = LocationPacket.encode(app.deviceId, fix)
            transmission.frames.forEach { frame ->
                val result = manager.sendPacket(frame, transmission.messageId, transmission.sequenceNumber, requireAck = true)
                check(result.acknowledged) { result.error ?: "Location could not be sent." }
            }
        }.onSuccess {
            _locationShareState.value = "SENT"
            _error.value = null
        }.onFailure {
            _locationShareState.value = "UNAVAILABLE"
            _error.value = it.message ?: "Location could not be sent."
        }
    }
    fun startTelemetryHeading() = app.headingProvider.start()
    fun stopTelemetryHeading() = app.headingProvider.stop()
    fun startTelemetryRssi() = action {
        manager.discoverBluetooth()
        peers.value.forEach { app.rssiTelemetry.recordDiscovery(it, System.currentTimeMillis()) }
    }
    fun stopTelemetryRssi() = Unit
    fun refreshTelemetryRssi() { app.rssiTelemetry.refresh(System.currentTimeMillis()) }
    fun retry(messageId: String) = action { repository.retry(messageId) }
    fun clearError() { _error.value = null }
    fun reportError(message: String) { _error.value = message }
    fun discoverabilityRequest() = manager.discoverabilityRequest()

    /**
     * DEBUG-ONLY: populate simulated peer sessions for UI testing.
     * These peers are clearly labelled SIMULATED and never mixed with real transport peers.
     */
    fun addSimulatedPeers() {
        if (!BuildConfig.DEBUG) return
        val sm = app.peerSessionManager
        data class SimPeer(val id: String, val name: String, val lang: String, val priority: Int, val connected: Boolean)
        val peers = listOf(
            SimPeer("SIM-CIV-001", "SIMULATED \u2014 CIV-001", "TA", 150, true),
            SimPeer("SIM-CIV-002", "SIMULATED \u2014 CIV-002", "HI", 10, true),
            SimPeer("SIM-CIV-003", "SIMULATED \u2014 CIV-003", "EN", 50, false),
        )
        peers.forEach { peer ->
            sm.registerPeer(peer.id, peer.name)
            // Inject a deterministic incoming message so session metadata is populated.
            sm.recordIncomingMessage(peer.id, Message(
                "sim-msg-${peer.id}", peer.id, System.currentTimeMillis(),
                "Test message from ${peer.name}", peer.lang, priority = peer.priority,
            ))
            if (peer.connected) {
                sm.updateConnectionState(peer.id, com.vokie.domain.model.TransportConnectionState.CONNECTED)
            }
        }
        VokieLog.rescue("DEBUG_SIMULATED_PEERS_ADDED count=${peers.size}")
    }

    /** DEBUG-ONLY: remove simulated peers and clean up selection. */
    fun removeSimulatedPeers() {
        if (!BuildConfig.DEBUG) return
        val simIds = listOf("SIM-CIV-001", "SIM-CIV-002", "SIM-CIV-003")
        simIds.forEach { app.peerSessionManager.removePeer(it) }
        // If the currently selected peer was simulated, clear the selection.
        if (_selectedPeerId.value in simIds) _selectedPeerId.value = null
        VokieLog.rescue("DEBUG_SIMULATED_PEERS_REMOVED")
    }

    private fun action(block: suspend () -> Unit) { viewModelScope.launch { runCatching { block() }.onSuccess { _error.value = null }.onFailure { _error.value = it.message ?: "Communication failed" } } }
}
