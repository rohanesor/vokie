package com.vokie.communication

import com.vokie.domain.model.*
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

    suspend fun sendPacket(packet: ByteArray) {
        activePacketTransport()?.send(packet) ?: throw IllegalStateException("No connected packet transport")
    }

    /** Single queue entry point. PacketV2 is encoded here, then only bytes cross PacketTransport. */
    suspend fun sendMessage(message: Message): SendResult {
        val packetTransport = activePacketTransport()
        if (packetTransport != null) {
            return runCatching {
                PacketV2.fromMessage(message).forEach { packetTransport.send(it) }
                SendResult(message.id, false, error = "Wi-Fi Direct packet sent; ACK correlation is pending")
            }.getOrElse { SendResult(message.id, false, error = "Wi-Fi Direct send failed: ${it.message}") }
        }
        // Compatibility fallback until Bluetooth exposes its raw PacketTransport session.
        return activeTransport()?.send(message) ?: SendResult(message.id, false, error = "No connected transport")
    }
    data class IncomingPacket(val transport: PacketTransport, val bytes: ByteArray)
    fun incomingPackets(): Flow<IncomingPacket> = kotlinx.coroutines.flow.merge(
        (wifiDirect?.observePackets() ?: kotlinx.coroutines.flow.emptyFlow()).map { IncomingPacket(wifiDirect!!, it) },
        bluetoothPacket.observePackets().map { IncomingPacket(bluetoothPacket, it) },
    )

    suspend fun sendAck(transport: PacketTransport, messageId: String, receiverId: String) {
        transport.send(PacketV2.encodeAck(messageId, receiverId, System.currentTimeMillis()))
    }
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
