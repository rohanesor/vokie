package com.vokie

import android.app.Application
import com.vokie.communication.*
import com.vokie.data.RoomMessageRepository
import com.vokie.data.local.*
import com.vokie.domain.model.TransportType
import com.vokie.map.MapPackManager
import com.vokie.models.BundledModelStore
import com.vokie.map.MapPreferences
import com.vokie.map.OfflineMapUseCase
import com.vokie.stt.SpeechToTextUseCase
import com.vokie.stt.SttLanguagePreferences
import com.vokie.stt.SttState
import com.vokie.stt.WhisperSttEngine
import com.vokie.tts.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.util.UUID

class VokieApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var database: VokieDatabase; private set
    lateinit var messageRepository: RoomMessageRepository; private set
    lateinit var bluetoothTransport: BluetoothTransport; private set
    lateinit var transportManager: TransportManager; private set
    lateinit var outboundProcessor: OutboundMessageProcessor; private set
    lateinit var sttEngine: WhisperSttEngine; private set
    lateinit var sttLanguagePreferences: SttLanguagePreferences; private set
    lateinit var speechToText: SpeechToTextUseCase; private set
    lateinit var ttsEngine: SherpaOnnxTtsEngine; private set
    lateinit var textToSpeech: TextToSpeechUseCase; private set
    lateinit var offlineMap: OfflineMapUseCase; private set
    lateinit var communicationPreferences: CommunicationPreferences; private set
    lateinit var deviceId: String; private set

    override fun onCreate() {
        super.onCreate()
        deviceId = getSharedPreferences("vokie_identity", MODE_PRIVATE).let { preferences ->
            preferences.getString("device_id", null) ?: UUID.randomUUID().toString().also { preferences.edit().putString("device_id", it).apply() }
        }
        database = VokieDatabase.get(this)
        messageRepository = RoomMessageRepository(database.messages())
        bluetoothTransport = BluetoothTransport(applicationContext, applicationScope)
        transportManager = TransportManager(bluetoothTransport)
        outboundProcessor = OutboundMessageProcessor(messageRepository, transportManager, database.transportEvents(), applicationScope)
        sttEngine = WhisperSttEngine(applicationContext)
        sttLanguagePreferences = SttLanguagePreferences(applicationContext)
        speechToText = SpeechToTextUseCase(sttEngine, sttLanguagePreferences)
        val bundledModels = BundledModelStore(applicationContext)
        val ttsModels = TtsModelManager(applicationContext)
        val ttsPreferences = TtsPreferences(applicationContext)
        val ttsSpeed = ttsPreferences.speed.stateIn(applicationScope, SharingStarted.Eagerly, DEFAULT_TTS_SPEED)
        ttsEngine = SherpaOnnxTtsEngine(ttsModels, VokieAudioPlayer(applicationContext))
        val ttsQueue = TtsPlaybackQueue(ttsEngine, ttsSpeed, applicationScope)
        textToSpeech = TextToSpeechUseCase(ttsEngine, ttsModels, ttsPreferences, ttsQueue).also { it.start() }
        applicationScope.launch {
            // This is a local, atomic APK-asset extraction; it never performs network I/O.
            runCatching { bundledModels.prepare() }
                .onFailure { VokieLog.stt("Bundled model preparation failed: ${it.message}") }
                .onSuccess {
                    ttsModels.refresh()
                    speechToText.initialize()
                    ttsEngine.initialize(TtsLanguage.ENGLISH)
                }
        }
        communicationPreferences = CommunicationPreferences(applicationContext)
        val mapManager = MapPackManager(applicationContext)
        val mapPreferences = MapPreferences(applicationContext)
        offlineMap = OfflineMapUseCase(applicationContext, mapManager, mapPreferences)
        applicationScope.launch { offlineMap.refresh() }

        applicationScope.launch {
            transportManager.incomingMessages().collect { message ->
                try {
                    val inserted = messageRepository.persistIncoming(message)
                    database.transportEvents().insert(TransportEventEntity(timestamp = System.currentTimeMillis(), transport = TransportType.BLUETOOTH.name, eventType = if (inserted) "MESSAGE_RECEIVED" else "DUPLICATE_RECEIVED", peerId = bluetoothTransport.connectedPeerId.value, messageId = message.id, detail = null, latencyMs = null))
                    if (message.requiresAck) runCatching { transportManager.acknowledge(message.id) }.onFailure { VokieLog.msg("ACK send failed: ${message.id}: ${it.message}") }
                    if (inserted) runCatching { textToSpeech.enqueueReceived(message) }.onFailure { VokieLog.tts("Incoming message could not be queued for speech: ${it.message}") }
                } catch (error: Throwable) {
                    VokieLog.msg("Incoming message rejected: ${error.message}")
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
            database.messages().recoverInterrupted()
            outboundProcessor.start()
        }
    }

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
