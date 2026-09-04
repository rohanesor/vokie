package com.vokie.communication

import com.vokie.domain.model.*
import com.vokie.location.LocationMeasurementCollector
import com.vokie.location.MeasurementTrigger
import com.vokie.ranging.RelativePeerLocalizationEngine
import com.vokie.stt.TurnTimingRecorder
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Transport coordinator. Packet methods are the transport-neutral production boundary. */
class TransportManager(
    private val bluetooth: BluetoothTransport,
    private val wifiDirect: PacketTransport? = null,
    private val measurements: LocationMeasurementCollector? = null,
    private val localization: RelativePeerLocalizationEngine? = null,
    scope: kotlinx.coroutines.CoroutineScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default),
    private val timing: TurnTimingRecorder? = null,
) {
    private val bluetoothPacket = BluetoothPacketTransport(bluetooth, scope)
    val pendingAcks = PendingAckRegistry()
    private val managerScope = scope
    private val _decodedFrames = MutableSharedFlow<DecodedTransportFrame>(extraBufferCapacity = 64)
    val decodedFrames: Flow<DecodedTransportFrame> = _decodedFrames.asSharedFlow()

    sealed interface DecodedTransportFrame {
        val transport: PacketTransport
        data class Message(val packet: PacketV2.Decoded.MessagePacket, override val transport: PacketTransport) : DecodedTransportFrame
        data class Ack(val messageId: String, val sequenceNumber: Long, override val transport: PacketTransport) : DecodedTransportFrame
    }
    // The queue must react to whichever transport is actually active. Previously it
    // observed Bluetooth only, so Wi-Fi Direct packets remained queued despite a live socket.
    val connectionState: StateFlow<TransportConnectionState> = wifiDirect?.state
        ?.combine(bluetooth.connectionState) { wifi, bt ->
            if (wifi == PacketTransportState.CONNECTED) TransportConnectionState.CONNECTED else bt
        }
        ?.stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, bluetooth.connectionState.value)
        ?: bluetooth.connectionState
    val peers = bluetooth.peers
    val connectedPeerId = bluetooth.connectedPeerId
    val wifiState: StateFlow<PacketTransportState>? get() = wifiDirect?.state
    val bluetoothState: StateFlow<TransportConnectionState> get() = bluetooth.connectionState

    /** Wi-Fi Direct has priority only when its lifecycle is actually CONNECTED. */
    fun activePacketTransport(): PacketTransport? = wifiDirect?.takeIf { it.state.value == PacketTransportState.CONNECTED }
        ?: bluetoothPacket.takeIf { it.state.value == PacketTransportState.CONNECTED }
    fun activeTransport(): Transport? = bluetooth.takeIf { it.connectionState.value == TransportConnectionState.CONNECTED }
    fun activeTransportType(): TransportType? = activePacketTransport()?.type

    suspend fun sendPacket(packet: ByteArray, messageId: String, sequenceNumber: Long, requireAck: Boolean = true): SendResult {
        val transport = activePacketTransport() ?: return SendResult(messageId, false, error = "No connected transport")
        val pending = if (requireAck) pendingAcks.register(PendingAckRegistry.Key(messageId, sequenceNumber), transport, ACK_TIMEOUT_MS).also {
            VokieLog.bt("TX_REGISTER_PENDING id=$messageId")
        } else null
        return try {
            transport.send(packet)
            if (pending == null) SendResult(messageId, true) else if (pendingAcks.await(pending)) SendResult(messageId, true) else SendResult(messageId, false, error = "ACK timeout")
        } catch (error: Throwable) {
            pending?.let { pendingAcks.fail(it.key) }; pendingAcks.clearTransport(transport)
            SendResult(messageId, false, error = error.message ?: "Transport send failed")
        }
    }

    /** Single queue entry point: encode here, then only bytes cross PacketTransport. */
    suspend fun sendMessage(message: Message): SendResult {
        val frames = PacketV2.fromMessage(message)
        val transport = activePacketTransport()
        val peer = connectedPeerId.value ?: message.receiverId ?: "unknown"
        val started = android.os.SystemClock.elapsedRealtime()
        measurements?.record(peer, MeasurementTrigger.MESSAGE_SENT, connectionState.value, wifiDirect?.state?.value ?: PacketTransportState.IDLE, transport?.type)
        transport?.type?.let { localization?.recordMessage(peer, it) }
        VokieLog.bt("TX_CREATE id=${message.id}")
        VokieLog.bt("WIFI_PACKET_TX messageId=${message.id} frames=${frames.size}")
        var result = SendResult(message.id, true)
        frames.forEachIndexed { index, frame ->
            if (result.acknowledged) {
                if (index == 0) timing?.transportTx(message.id, message.sequenceNumber)
                result = sendPacket(frame, message.id, message.sequenceNumber, index == frames.lastIndex)
            }
        }
        if (result.acknowledged) {
            val rtt = android.os.SystemClock.elapsedRealtime() - started
            measurements?.record(peer, MeasurementTrigger.ACK_RECEIVED, connectionState.value, wifiDirect?.state?.value ?: PacketTransportState.IDLE, transport?.type, rttMs = rtt, delivered = true)
            transport?.type?.let { localization?.recordAck(peer, it, rtt) }
        }
        return result
    }
    data class IncomingPacket(val transport: PacketTransport, val bytes: ByteArray)
    init {
        // Wire transport disconnect callbacks into the common lifecycle.
        bluetooth.onDisconnected = { peerId -> onTransportDisconnected(bluetoothPacket, peerId) }
        (wifiDirect as? WifiDirectTransport)?.let { wifi -> wifi.onDisconnected = { onTransportDisconnected(wifi, connectedPeerId.value) } }

        managerScope.launch {
            incomingPackets().collect { incoming ->
                val decoded = runCatching { PacketV2.decode(incoming.bytes) }.getOrElse {
                    VokieLog.bt("PACKET_DECODE_FAILURE reason=${it.message}")
                    return@collect
                }
                when (decoded) {
                    is PacketV2.Decoded.Ack -> {
                        VokieLog.bt("PACKET_DECODE type=ACK id=${decoded.messageId}")
                        VokieLog.bt("FRAME_DISPATCH type=ACK id=${decoded.messageId}")
                        VokieLog.bt("ACK_RESOLVE_ATTEMPT id=${decoded.messageId}")
                        if (pendingAcks.resolve(decoded.messageId, decoded.sequenceNumber)) {
                            VokieLog.bt("ACK_CORRELATED id=${decoded.messageId}")
                            VokieLog.bt("TX_COMPLETE id=${decoded.messageId}")
                            timing?.transportAck(decoded.messageId, decoded.sequenceNumber)
                        } else {
                            VokieLog.bt("ACK_UNKNOWN id=${decoded.messageId} pending=${pendingAcks.size()}")
                        }
                        _decodedFrames.emit(DecodedTransportFrame.Ack(decoded.messageId, decoded.sequenceNumber, incoming.transport))
                    }
                    is PacketV2.Decoded.MessagePacket -> {
                        VokieLog.bt("PACKET_DECODE type=MESSAGE id=${decoded.packet.messageId}")
                        _decodedFrames.emit(DecodedTransportFrame.Message(decoded, incoming.transport))
                    }
                }
            }
        }
    }

    fun incomingPackets(): Flow<IncomingPacket> = kotlinx.coroutines.flow.merge(
        (wifiDirect?.observePackets() ?: kotlinx.coroutines.flow.emptyFlow()).map { IncomingPacket(wifiDirect!!, it) },
        bluetoothPacket.observePackets().map { IncomingPacket(bluetoothPacket, it) },
    )

    suspend fun sendAck(transport: PacketTransport, messageId: String, receiverId: String, sequenceNumber: Long = 0) {
        val packet = PacketV2.encodeAck(messageId, receiverId, System.currentTimeMillis(), sequenceNumber)
        VokieLog.bt("WIFI_ACK_TX messageId=$messageId length=${packet.size}")
        transport.send(packet)
    }

    /** Callback for application-level disconnect recovery (message re-queue, session update). */
    var disconnectListener: ((transport: PacketTransport, peerId: String?) -> Unit)? = null

    fun onTransportDisconnected(transport: PacketTransport, peerId: String? = null) {
        pendingAcks.clearTransport(transport)
        VokieLog.rescue("TRANSPORT_DISCONNECTED transport=${transport.type} peer=${peerId ?: "unknown"}")
        disconnectListener?.invoke(transport, peerId)
    }

    private companion object { const val ACK_TIMEOUT_MS = 8_000L }
    suspend fun discoverWifiDirect() { wifiDirect?.discover() ?: error("Wi-Fi Direct unavailable") }
    suspend fun connectWifiDirect(address: String) { wifiDirect?.connect(address) ?: error("Wi-Fi Direct unavailable") }
    suspend fun disconnectWifiDirect() { wifiDirect?.disconnect() }

    suspend fun startBluetoothListener() = bluetooth.startListening()
    suspend fun discoverBluetooth() = bluetooth.discoverPeers()
    suspend fun stopDiscovery() = bluetooth.stopDiscovery()
    suspend fun connectBluetooth(peerId: String) = bluetooth.connect(peerId)
    suspend fun disconnect() = bluetooth.disconnect()
    fun incomingMessages() = bluetooth.observeMessages()
    suspend fun acknowledge(messageId: String) = bluetooth.acknowledge(messageId)
    fun discoverabilityRequest(): android.content.Intent = android.content.Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(android.bluetooth.BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
}
