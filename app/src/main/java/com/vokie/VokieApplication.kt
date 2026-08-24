package com.vokie

import android.app.Application
import com.vokie.communication.*
import com.vokie.data.RoomMessageRepository
import com.vokie.data.local.*
import com.vokie.domain.model.TransportType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import java.util.UUID

class VokieApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var database: VokieDatabase; private set
    lateinit var messageRepository: RoomMessageRepository; private set
    lateinit var bluetoothTransport: BluetoothTransport; private set
    lateinit var transportManager: TransportManager; private set
    lateinit var outboundProcessor: OutboundMessageProcessor; private set
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

        applicationScope.launch {
            transportManager.incomingMessages().collect { message ->
                try {
                    val inserted = messageRepository.persistIncoming(message)
                    database.transportEvents().insert(TransportEventEntity(timestamp = System.currentTimeMillis(), transport = TransportType.BLUETOOTH.name, eventType = if (inserted) "MESSAGE_RECEIVED" else "DUPLICATE_RECEIVED", peerId = bluetoothTransport.connectedPeerId.value, messageId = message.id, detail = null, latencyMs = null))
                    if (message.requiresAck) transportManager.acknowledge(message.id)
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
}
