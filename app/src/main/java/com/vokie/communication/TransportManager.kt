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
) {
    val connectionState: StateFlow<TransportConnectionState> = bluetooth.connectionState
    val peers = bluetooth.peers
    val connectedPeerId = bluetooth.connectedPeerId
    val wifiState: StateFlow<PacketTransportState>? get() = wifiDirect?.state
    val bluetoothState: StateFlow<TransportConnectionState> get() = bluetooth.connectionState

    /** Wi-Fi Direct has priority only when its lifecycle is actually CONNECTED. */
    fun activePacketTransport(): PacketTransport? = wifiDirect?.takeIf { it.state.value == PacketTransportState.CONNECTED }
    fun activeTransport(): Transport? = bluetooth.takeIf { it.connectionState.value == TransportConnectionState.CONNECTED }
    fun activeTransportType(): TransportType? = activePacketTransport()?.type ?: activeTransport()?.type

    suspend fun sendPacket(packet: ByteArray) {
        activePacketTransport()?.send(packet) ?: throw IllegalStateException("No connected packet transport")
    }
    fun incomingPackets(): Flow<ByteArray> = wifiDirect?.observePackets() ?: kotlinx.coroutines.flow.emptyFlow()
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
