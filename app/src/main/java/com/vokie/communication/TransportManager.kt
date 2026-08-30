package com.vokie.communication

import com.vokie.domain.model.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Transport coordinator. Packet methods are the transport-neutral production boundary. */
class TransportManager(
    private val bluetooth: BluetoothTransport,
    private val wifiDirect: PacketTransport? = null,
    scope: kotlinx.coroutines.CoroutineScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default),
) {
    private val bluetoothPacket = BluetoothPacketTransport(bluetooth, scope)
    val pendingAcks = PendingAckRegistry()
    private val managerScope = scope
    val connectionState: StateFlow<TransportConnectionState> = bluetooth.connectionState
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
        val pending = if (requireAck) pendingAcks.register(PendingAckRegistry.Key(messageId, sequenceNumber), transport, ACK_TIMEOUT_MS) else null
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
        var result = SendResult(message.id, true)
        frames.forEachIndexed { index, frame -> if (result.acknowledged) result = sendPacket(frame, message.id, message.sequenceNumber, index == frames.lastIndex) }
        return result
    }
    data class IncomingPacket(val transport: PacketTransport, val bytes: ByteArray)
    init {
        managerScope.launch {
            incomingPackets().collect { incoming ->
                val decoded = runCatching { PacketV2.decode(incoming.bytes) }.getOrNull()
                if (decoded is PacketV2.Decoded.Ack) pendingAcks.resolve(decoded.messageId, decoded.sequenceNumber)
            }
        }
    }

    fun incomingPackets(): Flow<IncomingPacket> = kotlinx.coroutines.flow.merge(
        (wifiDirect?.observePackets() ?: kotlinx.coroutines.flow.emptyFlow()).map { IncomingPacket(wifiDirect!!, it) },
        bluetoothPacket.observePackets().map { IncomingPacket(bluetoothPacket, it) },
    )

    suspend fun sendAck(transport: PacketTransport, messageId: String, receiverId: String, sequenceNumber: Long = 0) {
        transport.send(PacketV2.encodeAck(messageId, receiverId, System.currentTimeMillis(), sequenceNumber))
    }

    fun onTransportDisconnected(transport: PacketTransport) = pendingAcks.clearTransport(transport)

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
