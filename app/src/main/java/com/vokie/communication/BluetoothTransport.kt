package com.vokie.communication

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.vokie.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/** Real classic Bluetooth RFCOMM transport. UI only sees the Transport contract. */
class BluetoothTransport(private val context: Context) : Transport {
    override val type = TransportType.BLUETOOTH
    private val adapter: BluetoothAdapter? = (context.getSystemService(BluetoothAdapter::class.java))
    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    override val peers: StateFlow<List<Peer>> = _peers.asStateFlow()
    private val _state = MutableStateFlow(TransportConnectionState.IDLE)
    override val connectionState: StateFlow<TransportConnectionState> = _state.asStateFlow()
    private var socket: BluetoothSocket? = null
    private var serverSocket: android.bluetooth.BluetoothServerSocket? = null
    private var acceptJob: Job? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null
    private var receiverRegistered = false
    private val received = MutableSharedFlow<Message>(extraBufferCapacity = 32)
    private val ackWaiters = ConcurrentHashMap<String, kotlinx.coroutines.CompletableDeferred<Boolean>>()

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                    device.fetchUuidsWithSdp()
                }
                BluetoothDevice.ACTION_UUID -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                    val uuids = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID)?.mapNotNull { (it as? android.os.ParcelUuid)?.uuid } ?: emptyList()
                    if (VokieProtocol.SERVICE_UUID in uuids) {
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).takeIf { it != Short.MIN_VALUE }?.toInt()
                        val peer = Peer(device.address, device.name ?: "Unnamed Vokie device", device.address, device.bondState == BluetoothDevice.BOND_BONDED, rssi)
                        _peers.update { old -> (old.filterNot { it.address == peer.address } + peer).sortedBy { it.name } }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> if (_state.value == TransportConnectionState.SEARCHING) _state.value = TransportConnectionState.IDLE
            }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun discoverPeers() = withContext(Dispatchers.IO) {
        check(BluetoothPermission.hasDiscovery(context)) { "Nearby Devices permission is required." }
        val bt = adapter ?: error("Bluetooth is not available on this phone")
        if (!bt.isEnabled) error("Turn on Bluetooth to communicate.")
        registerReceiver()
        _peers.value = emptyList()
        bt.bondedDevices.forEach { it.fetchUuidsWithSdp() }
        startServer(bt)
        if (bt.isDiscovering) bt.cancelDiscovery()
        _state.value = TransportConnectionState.SEARCHING
        check(bt.startDiscovery()) { "Bluetooth discovery could not start." }
        VokieLog.bt("Discovery started")
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(peerId: String) = withContext(Dispatchers.IO) {
        check(BluetoothPermission.hasConnection(context)) { "Nearby Devices permission is required." }
        val bt = adapter ?: error("Bluetooth is not available on this phone")
        if (!bt.isEnabled) error("Turn on Bluetooth to communicate.")
        val device = bt.getRemoteDevice(peerId)
        _state.value = TransportConnectionState.CONNECTING
        if (bt.isDiscovering) bt.cancelDiscovery()
        closeSocket()
        try {
            val candidate = withTimeout(15.seconds) { device.createRfcommSocketToServiceRecord(VokieProtocol.SERVICE_UUID).also { it.connect() } }
            socket = candidate; input = DataInputStream(candidate.inputStream); output = DataOutputStream(candidate.outputStream)
            _state.value = TransportConnectionState.CONNECTED
            VokieLog.bt("Connection established: $peerId")
            listenForFrames()
        } catch (t: Throwable) { closeSocket(); _state.value = TransportConnectionState.FAILED; VokieLog.bt("Connection failed: ${t.message}"); throw IOException("Connection failed", t) }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) { closeSocket(); closeServer(); _state.value = TransportConnectionState.DISCONNECTED; VokieLog.bt("Disconnected") }

    override suspend fun send(message: Message): SendResult = withContext(Dispatchers.IO) {
        val stream = output ?: return@withContext SendResult(message.id, false, "No connected Vokie device")
        val waiter = kotlinx.coroutines.CompletableDeferred<Boolean>(); ackWaiters[message.id] = waiter
        try {
            val frame = VokieProtocol.encode(message)
            synchronized(stream) { stream.writeInt(frame.size); stream.write(frame); stream.flush() }
            VokieLog.msg("Transmission started: ${message.id}")
            val ack = withTimeout(8.seconds) { waiter.await() }
            if (ack) { VokieLog.msg("ACK received: ${message.id}"); SendResult(message.id, true) } else SendResult(message.id, false, "Peer rejected message")
        } catch (t: Throwable) { VokieLog.msg("Transmission failed: ${message.id}: ${t.message}"); SendResult(message.id, false, "No acknowledgement received") } finally { ackWaiters.remove(message.id) }
    }

    override fun observeMessages(): Flow<Message> = received.asSharedFlow()

    private fun listenForFrames() {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                while (connectionState.value == TransportConnectionState.CONNECTED) {
                    val size = input?.readInt() ?: break
                    if (size <= 0 || size > 64 * 1024) throw IOException("Invalid Vokie frame size")
                    val bytes = ByteArray(size); input!!.readFully(bytes)
                    when (val frame = VokieProtocol.decode(bytes)) {
                        is VokieProtocol.DecodedFrame.Ack -> ackWaiters.remove(frame.messageId)?.complete(true)
                        is VokieProtocol.DecodedFrame.MessageFrame -> { received.emit(frame.message); if (frame.requiresAck) sendAck(frame.message.id) }
                    }
                }
            } catch (t: Throwable) { if (connectionState.value == TransportConnectionState.CONNECTED) { _state.value = TransportConnectionState.DISCONNECTED; VokieLog.bt("Connection lost: ${t.message}") } }
        }
    }

    private fun sendAck(messageId: String) { val stream = output ?: return; val frame = VokieProtocol.encodeAck(messageId); runCatching { synchronized(stream) { stream.writeInt(frame.size); stream.write(frame); stream.flush() } } }
    @SuppressLint("MissingPermission") private fun closeSocket() { runCatching { input?.close() }; runCatching { output?.close() }; runCatching { socket?.close() }; input = null; output = null; socket = null }
    @SuppressLint("MissingPermission") private fun closeServer() { acceptJob?.cancel(); acceptJob = null; runCatching { serverSocket?.close() }; serverSocket = null }
    @SuppressLint("MissingPermission")
    private fun startServer(bt: BluetoothAdapter) {
        if (acceptJob?.isActive == true) return
        acceptJob = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                serverSocket = bt.listenUsingRfcommWithServiceRecord(VokieProtocol.SERVICE_NAME, VokieProtocol.SERVICE_UUID)
                while (true) {
                    val accepted = serverSocket?.accept() ?: break
                    closeSocket()
                    socket = accepted; input = DataInputStream(accepted.inputStream); output = DataOutputStream(accepted.outputStream)
                    _state.value = TransportConnectionState.CONNECTED
                    VokieLog.bt("Incoming Vokie connection established")
                    listenForFrames()
                }
            }.onFailure { if (_state.value != TransportConnectionState.CONNECTED) VokieLog.bt("Server stopped: ${it.message}") }
        }
    }

    private fun registerReceiver() {
        if (receiverRegistered) return
        ContextCompat.registerReceiver(context, discoveryReceiver, IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND); addAction(BluetoothDevice.ACTION_UUID); addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }
}

object VokieLog {
    fun bt(message: String) { android.util.Log.i("VOKIE][BT", message) }
    fun msg(message: String) { android.util.Log.i("VOKIE][MSG", message) }
    fun stt(message: String) { android.util.Log.i("VOKIE][STT", message) }
    fun tts(message: String) { android.util.Log.i("VOKIE][TTS", message) }
}
