package com.vokie

import android.app.Application
import com.vokie.communication.*
import com.vokie.data.RoomMessageRepository
import com.vokie.data.local.*
import com.vokie.domain.model.TransportType
import com.vokie.map.MapPackManager
import com.vokie.models.BundledModelStore
import com.vokie.models.ModelDownloadManager
import com.vokie.location.AndroidHeadingProvider
import com.vokie.location.AndroidLocationProvider
import com.vokie.location.EmergencyGuidanceCoordinator
import com.vokie.location.LocationMeasurementCollector
import com.vokie.location.LocationPacket
import com.vokie.location.LocationAvailability
import com.vokie.location.LocationMetadata
import com.vokie.proximity.BluetoothRssiTelemetryProvider
import com.vokie.ranging.RelativePeerLocalizationEngine
import com.vokie.map.MapPreferences
import com.vokie.map.OfflineMapUseCase
import com.vokie.stt.SpeechToTextUseCase
import com.vokie.stt.ContinuousTurnManager
import com.vokie.stt.TurnTimingRecorder
import com.vokie.stt.SttLanguagePreferences
import com.vokie.stt.UserLanguageProfilePreferences
import com.vokie.stt.SttState
import com.vokie.stt.SttResult
import com.vokie.stt.SenseVoiceSttEngine
import com.vokie.stt.SttEngine
import com.vokie.stt.WhisperSttEngine
import com.vokie.tts.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import com.vokie.translation.ReceiverTranslationCoordinator
import com.vokie.translation.Ctranslate2TranslationEngine
import java.util.UUID

class VokieApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var database: VokieDatabase; private set
    lateinit var messageRepository: RoomMessageRepository; private set
    lateinit var bluetoothTransport: BluetoothTransport; private set
    lateinit var wifiDirectTransport: WifiDirectTransport; private set
    lateinit var transportManager: TransportManager; private set
    lateinit var outboundProcessor: OutboundMessageProcessor; private set
    lateinit var sttEngine: SttEngine; private set
    lateinit var sttLanguagePreferences: SttLanguagePreferences; private set
    lateinit var userLanguageProfilePreferences: UserLanguageProfilePreferences; private set
    lateinit var speechToText: SpeechToTextUseCase; private set
    lateinit var continuousTurnManager: ContinuousTurnManager; private set
    lateinit var turnTiming: TurnTimingRecorder; private set
    lateinit var ttsEngine: TtsEngine; private set
    lateinit var modelDownloads: ModelDownloadManager; private set
    lateinit var textToSpeech: TextToSpeechUseCase; private set
    private lateinit var receiverTranslation: ReceiverTranslationCoordinator
    val receiverPresentations get() = receiverTranslation.presentations
    lateinit var offlineMap: OfflineMapUseCase; private set
    lateinit var locationProvider: AndroidLocationProvider; private set
    lateinit var headingProvider: AndroidHeadingProvider; private set
    lateinit var rssiTelemetry: BluetoothRssiTelemetryProvider; private set
    lateinit var emergencyGuidance: EmergencyGuidanceCoordinator; private set
    lateinit var proximityMeasurements: LocationMeasurementCollector; private set
    lateinit var localizationEngine: RelativePeerLocalizationEngine; private set
    val remoteLocation = MutableStateFlow(LocationMetadata(availability = LocationAvailability.UNAVAILABLE))
    private var remoteLocationSenderId: String? = null
    lateinit var communicationPreferences: CommunicationPreferences; private set
    lateinit var peerSessionManager: PeerSessionManager; private set
    lateinit var deviceId: String; private set

    override fun onCreate() {
        super.onCreate()
        deviceId = getSharedPreferences("vokie_identity", MODE_PRIVATE).let { preferences ->
            preferences.getString("device_id", null) ?: UUID.randomUUID().toString().also { preferences.edit().putString("device_id", it).apply() }
        }
        database = VokieDatabase.get(this)
        messageRepository = RoomMessageRepository(database.messages())
        bluetoothTransport = BluetoothTransport(applicationContext, applicationScope)
        wifiDirectTransport = WifiDirectTransport(applicationContext, applicationScope)
        proximityMeasurements = LocationMeasurementCollector()
        localizationEngine = RelativePeerLocalizationEngine(applicationContext)
        peerSessionManager = PeerSessionManager(database.peers())
        turnTiming = TurnTimingRecorder()
        transportManager = TransportManager(bluetoothTransport, wifiDirectTransport, proximityMeasurements, localizationEngine, applicationScope, turnTiming)
        // Wire transport disconnect → re-queue TRANSMITTING messages + update peer sessions.
        transportManager.disconnectListener = { transport, peerId ->
            applicationScope.launch(Dispatchers.IO) {
                database.messages().recoverInterrupted()
                VokieLog.rescue("MESSAGE_REQUEUED_ALL transport=${transport.type} peer=${peerId ?: "unknown"}")
                if (!peerId.isNullOrBlank()) {
                    peerSessionManager.updateConnectionState(peerId, com.vokie.domain.model.TransportConnectionState.DISCONNECTED)
                    peerSessionManager.persistSession(peerId)
                }
            }
        }
        outboundProcessor = OutboundMessageProcessor(messageRepository, transportManager, database.transportEvents(), applicationScope, peerSessionManager)
        val inboundPackets = InboundPacketCoordinator(messageRepository, database.receivedPackets())
        // SenseVoice is primary; Whisper is lazy fallback if SenseVoice model is absent.
        val senseVoice = SenseVoiceSttEngine(applicationContext)
        sttEngine = if (senseVoice.isAvailable) senseVoice else WhisperSttEngine(applicationContext)
        sttLanguagePreferences = SttLanguagePreferences(applicationContext)
        userLanguageProfilePreferences = UserLanguageProfilePreferences(applicationContext)
        speechToText = SpeechToTextUseCase(sttEngine, sttLanguagePreferences)
        continuousTurnManager = ContinuousTurnManager(sttEngine, applicationScope, timing = turnTiming)
        val bundledModels = BundledModelStore(applicationContext)
        val ttsModels = TtsModelManager(applicationContext)
        modelDownloads = ModelDownloadManager(applicationContext, bundledModels)
        val ttsPreferences = TtsPreferences(applicationContext)
        val ttsSpeed = ttsPreferences.speed.stateIn(applicationScope, SharingStarted.Eagerly, DEFAULT_TTS_SPEED)
        // Approved local MMS/VITS assets are model-gated by TtsModelManager; unavailable
        // languages still fail explicitly instead of falling back to cloud/system TTS.
        ttsEngine = SherpaOnnxTtsEngine(ttsModels, VokieAudioPlayer(applicationContext))
        // CT2 only loads an approved app-private staged model; it has no download/fallback path.
        receiverTranslation = ReceiverTranslationCoordinator(Ctranslate2TranslationEngine(applicationContext))
        val ttsQueue = TtsPlaybackQueue(ttsEngine, ttsSpeed, applicationScope, turnTiming)
        textToSpeech = TextToSpeechUseCase(ttsEngine, ttsModels, ttsPreferences, ttsQueue).also { it.start() }
        applicationScope.launch {
            // This is a local, atomic APK-asset extraction; it never performs network I/O.
            runCatching { bundledModels.prepare() }
                .onFailure { VokieLog.stt("Bundled model preparation failed: ${it.message}") }
                .onSuccess {
                    ttsModels.refresh()
                    speechToText.initialize()
                    // TTS initialization is intentionally skipped until an approved bundled artifact exists.
                }
        }
        communicationPreferences = CommunicationPreferences(applicationContext)
        // Telemetry adapters are constructed but intentionally not started until a future active guidance feature.
        locationProvider = AndroidLocationProvider(applicationContext)
        headingProvider = AndroidHeadingProvider(applicationContext)
        rssiTelemetry = BluetoothRssiTelemetryProvider()
        emergencyGuidance = EmergencyGuidanceCoordinator()
        val mapManager = MapPackManager(applicationContext)
        val mapPreferences = MapPreferences(applicationContext)
        offlineMap = OfflineMapUseCase(applicationContext, mapManager, mapPreferences)
        applicationScope.launch { offlineMap.refresh() }
        applicationScope.launch {
            while (isActive) {
                delay(5_000L)
                localizationEngine.refresh()
            }
        }

        applicationScope.launch {
            transportManager.decodedFrames.collect { frame ->
                if (frame is com.vokie.communication.TransportManager.DecodedTransportFrame.Ack) {
                    val peer = transportManager.connectedPeerId.value
                    if (peer != null) {
                        localizationEngine.recordAck(peer, frame.transport.type, null)
                        peerSessionManager.recordAck(peer, frame.messageId, frame.sequenceNumber)
                    }
                }
                if (frame is com.vokie.communication.TransportManager.DecodedTransportFrame.Message) {
                    runCatching {
                        val remotePeerId = frame.packet.packet.sourceDeviceId
                        // T4 is packet ingress, before deduplication/translation; PacketV2 stays untouched.
                        turnTiming.packetReceived(frame.packet.packet.messageId, frame.packet.packet.sequenceNumber)
                        inboundPackets.acceptDecoded(frame.packet, frame.transport) { transport, messageId, sequenceNumber ->
                            transportManager.sendAck(transport, messageId, deviceId, sequenceNumber)
                        }
                        proximityMeasurements.record(remotePeerId, com.vokie.location.MeasurementTrigger.MESSAGE_RECEIVED, transportManager.connectionState.value, wifiDirectTransport.state.value, frame.transport.type, delivered = true)
                        localizationEngine.recordMessage(remotePeerId, frame.transport.type)
                    }.onFailure { VokieLog.msg("Incoming packet rejected: ${it.message}") }
                }
            }
        }
        applicationScope.launch {
            inboundPackets.messages.collect { message ->
                // Register the remote peer and record the inbound message against its session.
                val remotePeer = message.senderId
                if (remotePeer.isNotBlank() && remotePeer != deviceId) {
                    peerSessionManager.registerPeer(remotePeer)
                    peerSessionManager.recordIncomingMessage(remotePeer, message)
                    peerSessionManager.persistSession(remotePeer)
                }
                if (message.messageType == com.vokie.domain.model.MessageType.LOCATION) {
                    LocationPacket.decode(message)?.let { location ->
                        val priorSender = remoteLocationSenderId
                        val prior = remoteLocation.value
                        if (priorSender != message.senderId || location.locationSequence > prior.locationSequence) {
                            remoteLocationSenderId = message.senderId
                            remoteLocation.value = location
                            VokieLog.bt("LOCATION_RECEIVED id=${message.id} sequence=${location.locationSequence}")
                        } else {
                            VokieLog.bt("LOCATION_REJECTED_OLD id=${message.id} sequence=${location.locationSequence}")
                        }
                    }
                    return@collect
                }
                runCatching {
                    val target = userLanguageProfilePreferences.profile.first()?.preferredOutputLanguage
                    if (target == null) {
                        VokieLog.translation("TRANSLATION_REQUEST_SKIPPED reason=receiver_output_language_unset messageId=${message.id}")
                        return@runCatching
                    }
                    val source = com.vokie.domain.model.VokieLanguage.fromCode(message.language)
                    if (source == null) {
                        VokieLog.translation("TRANSLATION_REQUEST_SKIPPED reason=invalid_source_language code=${message.language} messageId=${message.id}")
                        return@runCatching
                    }
                    VokieLog.translation("TRANSLATION_RECEIVER_REQUEST messageId=${message.id} source=${source.code} target=${target.code}")
                    turnTiming.translationStart(message.id, source.code, target.code)
                    val outcome = receiverTranslation.presentOnce(message.id, message.text, source, target)
                    turnTiming.translationComplete(message.id)
                    VokieLog.translation("TRANSLATION_RECEIVER_RESULT messageId=${message.id} state=${outcome.presentation.state} new=${outcome.isNew} error=${outcome.presentation.error}")
                    // TTS remains model-gated; only the final receiver-local presentation text is queued.
                    outcome.presentation.ttsHandoff()?.let { handoff ->
                        VokieLog.translation("TTS_HANDOFF messageId=${message.id} target=${handoff.language.code} chars=${handoff.text.length} state=${outcome.presentation.state}")
                        textToSpeech.enqueueReceived(message.id, handoff.text, handoff.language, message.messageType)
                    }
                }.onFailure {
                    turnTiming.fail(null, message.id, com.vokie.stt.TurnTimingFailure.TRANSLATION)
                    VokieLog.tts("Incoming message could not be queued for speech: ${it.message}")
                }
            }
        }
        applicationScope.launch {
            combine(locationProvider.location, headingProvider.heading, rssiTelemetry.state, remoteLocation) { receiver, heading, rssi, sender ->
                emergencyGuidance.update(sender, receiver, heading, rssi.filtered, System.currentTimeMillis())
            }.collect { }
        }
        applicationScope.launch {
            bluetoothTransport.peers.collect { peers ->
                peers.forEach { peer ->
                    localizationEngine.startPeer(peer.id, TransportType.BLUETOOTH)
                    peer.rssi?.let { localizationEngine.recordRssi(peer.id, it) }
                    peerSessionManager.registerPeer(peer.id, peer.name, TransportType.BLUETOOTH)
                }
            }
        }
        applicationScope.launch {
            wifiDirectTransport.peers.collect { peers ->
                peers.filter { it.available }.forEach { peer ->
                    localizationEngine.startPeer(peer.address, TransportType.WIFI_DIRECT)
                    localizationEngine.recordWifi(peer.address)
                    peerSessionManager.registerPeer(peer.address, peer.name, TransportType.WIFI_DIRECT)
                }
            }
        }
        // Propagate Bluetooth connection state changes to PeerSessionManager.
        applicationScope.launch {
            combine(bluetoothTransport.connectionState, bluetoothTransport.connectedPeerId) { state, peerId -> state to peerId }.collect { (state, peerId) ->
                if (!peerId.isNullOrBlank()) {
                    peerSessionManager.updateConnectionState(peerId, state)
                    peerSessionManager.persistSession(peerId)
                }
            }
        }
        applicationScope.launch(Dispatchers.IO) {
            combine(bluetoothTransport.peers, bluetoothTransport.connectionState) { peers, state -> peers to state }
                .collectLatest { (peers, state) ->
                    peers.forEach { peer ->
                        database.peers().upsert(PeerEntity(peer.id, peer.address, peer.name, VokieProtocol.VERSION, System.currentTimeMillis(), if (bluetoothTransport.connectedPeerId.value == peer.id) "CONNECTED" else state.name, TransportType.BLUETOOTH.name, peer.bonded))
                    }
                }
        }
        applicationScope.launch(Dispatchers.IO) {
            // Restore peer sessions from Room before recovering messages.
            peerSessionManager.restoreFromPersistence()
            database.messages().recoverInterrupted()
            outboundProcessor.start()
        }
    }

    /** Explicit bridge for a completed local Whisper result; receiver target remains receiver-local. */
    suspend fun enqueueWhisperTranscript(result: SttResult, receiverId: String? = null) =
        messageRepository.createMessage(result.text, deviceId, receiverId, result.language.messageLanguage)

    override fun onLowMemory() {
        releaseIdleStt()
        super.onLowMemory()
    }

    override fun onTrimMemory(level: Int) {
        if (level >= TRIM_MEMORY_COMPLETE) releaseIdleStt()
        super.onTrimMemory(level)
    }

    private fun releaseIdleStt() {
        if (sttEngine.status.value.state in setOf(SttState.READY, SttState.RESULT, SttState.ERROR)) sttEngine.release()
        if (ttsEngine.status.value.state in setOf(TtsState.READY, TtsState.COMPLETED, TtsState.ERROR, TtsState.MODEL_MISSING, TtsState.MODEL_LOAD_FAILED)) ttsEngine.release()
    }
}
