package com.vokie.communication

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import com.vokie.domain.model.TransportType
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/** Byte-oriented transport boundary. PacketV2 serialization is outside this interface. */
interface PacketTransport {
    val type: TransportType
    val state: StateFlow<PacketTransportState>
    val peers: StateFlow<List<WifiPeer>>
    suspend fun discover()
    suspend fun connect(deviceAddress: String)
    suspend fun send(packet: ByteArray)
    suspend fun disconnect()
    fun observePackets(): Flow<ByteArray>
}

enum class PacketTransportState { IDLE, DISCOVERING, CONNECTING, CONNECTED, DISCONNECTING, FAILED }
data class WifiPeer(val address: String, val name: String, val available: Boolean)

/** Shared TCP framing used by Wi-Fi Direct. It handles short reads and rejects bad lengths. */
object LengthPrefixedFrames {
    const val MAX_PACKET_BYTES = PacketV2.MAX_FRAME_BYTES
    suspend fun write(output: DataOutputStream, packet: ByteArray) = withContext(Dispatchers.IO) {
        require(packet.isNotEmpty() && packet.size <= MAX_PACKET_BYTES)
        synchronized(output) { output.writeInt(packet.size); output.write(packet); output.flush() }
    }
    suspend fun read(input: DataInputStream): ByteArray = withContext(Dispatchers.IO) {
        val length = input.readInt()
        require(length in 1..MAX_PACKET_BYTES) { "Invalid packet length" }
        ByteArray(length).also { input.readFully(it) }
    }
}

/** Native Android Wi-Fi Direct transport. It carries opaque PacketV2 bytes only. */
class WifiDirectTransport(private val context: Context, private val scope: CoroutineScope) : PacketTransport {
    override val type = TransportType.WIFI_DIRECT
    private val manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val channel = manager?.initialize(context, context.mainLooper, null)
    private val _state = MutableStateFlow(if (manager == null) PacketTransportState.FAILED else PacketTransportState.IDLE)
    override val state = _state.asStateFlow()
    private val _peers = MutableStateFlow<List<WifiPeer>>(emptyList())
    override val peers = _peers.asStateFlow()
    private val packets = MutableSharedFlow<ByteArray>(extraBufferCapacity = 32)
    private var socket: Socket? = null
    private var server: ServerSocket? = null
    private var ioJob: Job? = null
    private var receiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> refreshPeers()
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> requestConnectionInfo()
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> if (intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, 0) == WifiP2pManager.WIFI_P2P_STATE_DISABLED) fail("Wi-Fi Direct disabled")
            }
        }
    }

    override suspend fun discover() = withContext(Dispatchers.Main) {
        ensureReady(); register(); _state.value = PacketTransportState.DISCOVERING
        manager!!.discoverPeers(channel, action("peer discovery failed"))
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(deviceAddress: String) = withContext(Dispatchers.Main) {
        ensureReady(); register(); _state.value = PacketTransportState.CONNECTING
        manager!!.connect(channel, WifiP2pConfig().apply { deviceAddress.also { this.deviceAddress = it } }, action("Wi-Fi Direct connection failed"))
    }

    override suspend fun send(packet: ByteArray) {
        val output = socket?.let { DataOutputStream(it.getOutputStream()) } ?: throw IOException("Wi-Fi Direct is not connected")
        LengthPrefixedFrames.write(output, packet)
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        _state.value = PacketTransportState.DISCONNECTING; ioJob?.cancel(); runCatching { socket?.close() }; runCatching { server?.close() }; socket = null; server = null
        withContext(Dispatchers.Main) { channel?.let { manager?.removeGroup(it, null) } }; _state.value = PacketTransportState.IDLE
    }

    override fun observePackets(): Flow<ByteArray> = packets.asSharedFlow()

    private fun refreshPeers() {
        manager?.requestPeers(channel) { list: WifiP2pDeviceList -> _peers.value = list.deviceList.map { WifiPeer(it.deviceAddress, it.deviceName.ifBlank { "Unnamed iTantra device" }, it.status != WifiP2pDevice.UNAVAILABLE) } }
    }
    private fun requestConnectionInfo() { manager?.requestConnectionInfo(channel) { info: WifiP2pInfo -> if (info.groupFormed) establishSocket(info.isGroupOwner, info.groupOwnerAddress) } }
    private fun establishSocket(owner: Boolean, address: InetAddress?) {
        ioJob?.cancel(); ioJob = scope.launch(Dispatchers.IO) {
            runCatching {
                val s = if (owner) ServerSocket(PORT).also { server = it }.accept() else Socket(address ?: error("Missing group owner address"), PORT)
                socket = s; _state.value = PacketTransportState.CONNECTED; val input = DataInputStream(s.getInputStream())
                while (isActive) packets.emit(LengthPrefixedFrames.read(input))
            }.onFailure { if (it !is CancellationException) fail(it.message ?: "Wi-Fi Direct socket failed") }
        }
    }
    private fun ensureReady() { check(manager != null && channel != null) { "Wi-Fi Direct is unavailable" } }
    private fun register() { if (receiverRegistered) return; ContextCompat.registerReceiver(context, receiver, IntentFilter().apply { addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION); addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION); addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION) }, ContextCompat.RECEIVER_EXPORTED); receiverRegistered = true }
    private fun fail(message: String) { _state.value = PacketTransportState.FAILED; VokieLog.bt(message) }
    private fun action(message: String) = object : WifiP2pManager.ActionListener { override fun onSuccess() = Unit; override fun onFailure(reason: Int) = fail("$message ($reason)") }
    private companion object { const val PORT = 39_721 }
}
