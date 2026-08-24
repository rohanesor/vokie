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
import androidx.core.content.IntentCompat
import com.vokie.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

/** Real classic Bluetooth RFCOMM transport. UI only sees the Transport contract. */
class BluetoothTransport(private val context: Context, private val scope: kotlinx.coroutines.CoroutineScope) : Transport {
    override val type = TransportType.BLUETOOTH
    private val adapter: BluetoothAdapter? = (context.getSystemService(BluetoothAdapter::class.java))
    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    override val peers: StateFlow<List<Peer>> = _peers.asStateFlow()
    private val _state = MutableStateFlow(initialState())
    override val connectionState: StateFlow<TransportConnectionState> = _state.asStateFlow()
    private val _connectedPeerId = MutableStateFlow<String?>(null)
    override val connectedPeerId: StateFlow<String?> = _connectedPeerId.asStateFlow()
    private var socket: BluetoothSocket? = null
    private var serverSocket: android.bluetooth.BluetoothServerSocket? = null
    private var acceptJob: Job? = null
    private var connectionJob: Job? = null
    private var reconnectJob: Job? = null
    private var manuallyDisconnected = false
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null
    private var receiverRegistered = false
    private val received = MutableSharedFlow<Message>(extraBufferCapacity = 32)
    private val ackTracker = AckTracker()

    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    if (!BluetoothPermission.hasDiscovery(ctx) || !BluetoothPermission.hasConnection(ctx)) return
                    val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
                    device.fetchUuidsWithSdp()
                }
                BluetoothDevice.ACTION_UUID -> {
                    if (!BluetoothPermission.hasDiscovery(ctx) || !BluetoothPermission.hasConnection(ctx)) return
                    val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
                    val uuids = IntentCompat.getParcelableArrayExtra(intent, BluetoothDevice.EXTRA_UUID, android.os.ParcelUuid::class.java)?.mapNotNull { (it as? android.os.ParcelUuid)?.uuid } ?: emptyList()
                    if (VokieProtocol.SERVICE_UUID in uuids) {
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).takeIf { it != Short.MIN_VALUE }?.toInt()
                        val peer = Peer(device.address, device.name ?: "Unnamed Vokie device", device.address, device.bondState == BluetoothDevice.BOND_BONDED, rssi)
                        _peers.update { old -> (old.filterNot { it.address == peer.address } + peer).sortedBy { it.name } }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> if (_state.value == TransportConnectionState.SEARCHING) _state.value = TransportConnectionState.IDLE
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                        BluetoothAdapter.STATE_OFF -> { closeSocket(); _connectedPeerId.value = null; _state.value = TransportConnectionState.BLUETOOTH_DISABLED }
                        BluetoothAdapter.STATE_ON -> _state.value = if (BluetoothPermission.hasConnection(context)) TransportConnectionState.IDLE else TransportConnectionState.PERMISSION_REQUIRED
                    }
                }
            }
        }
    }

    init { registerReceiver() }

    @SuppressLint("MissingPermission")
    override suspend fun startListening() = withContext(Dispatchers.IO) {
        check(BluetoothPermission.hasConnection(context)) { "Nearby Devices permission is required." }
        val bt = adapter ?: error("Bluetooth is not available on this phone")
        if (!bt.isEnabled) error("Turn on Bluetooth to communicate.")
        registerReceiver(); startServer(bt)
        if (_state.value != TransportConnectionState.CONNECTED) _state.value = TransportConnectionState.IDLE
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
    override suspend fun stopDiscovery() = withContext(Dispatchers.IO) {
        if (BluetoothPermission.hasDiscovery(context) && adapter?.isDiscovering == true) adapter.cancelDiscovery()
        if (_state.value == TransportConnectionState.SEARCHING) _state.value = TransportConnectionState.IDLE
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(peerId: String) = withContext(Dispatchers.IO) {
        manuallyDisconnected = false
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
            _connectedPeerId.value = peerId
            _state.value = TransportConnectionState.CONNECTED
            VokieLog.bt("Connection established: $peerId")
            listenForFrames()
        } catch (t: Throwable) { closeSocket(); _state.value = TransportConnectionState.FAILED; VokieLog.bt("Connection failed: ${t.message}"); scheduleReconnect(peerId); throw IOException("Connection failed", t) }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) { manuallyDisconnected = true; reconnectJob?.cancel(); reconnectJob = null; closeSocket(); closeServer(); _connectedPeerId.value = null; _state.value = TransportConnectionState.DISCONNECTED; VokieLog.bt("Disconnected") }

    override suspend fun send(message: Message): SendResult = withContext(Dispatchers.IO) {
        val stream = output ?: return@withContext SendResult(message.id, false, error = "No connected Vokie device")
        val waiter = ackTracker.register(message.id)
        try {
            val frame = VokieProtocol.encode(message)
            synchronized(stream) { stream.writeInt(frame.size); stream.write(frame); stream.flush() }
            VokieLog.msg("Transmission started: ${message.id}")
            val started = android.os.SystemClock.elapsedRealtime()
            val ack = withTimeout(8.seconds) { waiter.await() }
            if (ack) { val latency = android.os.SystemClock.elapsedRealtime() - started; VokieLog.msg("ACK received: ${message.id}"); SendResult(message.id, true, latency) } else SendResult(message.id, false, error = "Peer rejected message")
        } catch (t: Throwable) { VokieLog.msg("Transmission failed: ${message.id}: ${t.message}"); SendResult(message.id, false, error = "No acknowledgement received") } finally { ackTracker.remove(message.id) }
    }

    override fun observeMessages(): Flow<Message> = received.asSharedFlow()

    override suspend fun acknowledge(messageId: String) = withContext(Dispatchers.IO) {
        val localId = context.getSharedPreferences("vokie_identity", Context.MODE_PRIVATE).getString("device_id", null) ?: error("Device identity unavailable")
        sendAck(messageId, localId)
    }

    private fun listenForFrames() {
        connectionJob?.cancel()
        connectionJob = scope.launch(Dispatchers.IO) {
            try {
                while (connectionState.value == TransportConnectionState.CONNECTED) {
                    val size = input?.readInt() ?: break
                    if (size <= 0 || size > 64 * 1024) throw IOException("Invalid Vokie frame size")
                    val bytes = ByteArray(size); input!!.readFully(bytes)
                    when (val frame = VokieProtocol.decode(bytes)) {
                        is VokieProtocol.DecodedFrame.Ack -> if (!ackTracker.acknowledge(frame.messageId)) VokieLog.msg("Unknown ACK ignored: ${frame.messageId}")
                        is VokieProtocol.DecodedFrame.MessageFrame -> received.emit(frame.message)
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (t: Throwable) { if (connectionState.value == TransportConnectionState.CONNECTED) { val lostPeer = _connectedPeerId.value; closeSocket(); _connectedPeerId.value = null; _state.value = TransportConnectionState.DISCONNECTED; VokieLog.bt("Connection lost: ${t.message}"); lostPeer?.let(::scheduleReconnect) } }
        }
    }

    private fun sendAck(messageId: String, receiverId: String) { val stream = output ?: return; val frame = VokieProtocol.encodeAck(messageId, receiverId, System.currentTimeMillis()); synchronized(stream) { stream.writeInt(frame.size); stream.write(frame); stream.flush() } }
    @SuppressLint("MissingPermission") private fun closeSocket() { connectionJob?.cancel(); connectionJob = null; runCatching { input?.close() }; runCatching { output?.close() }; runCatching { socket?.close() }; input = null; output = null; socket = null }
    @SuppressLint("MissingPermission") private fun closeServer() { acceptJob?.cancel(); acceptJob = null; runCatching { serverSocket?.close() }; serverSocket = null }
    @SuppressLint("MissingPermission")
    private fun startServer(bt: BluetoothAdapter) {
        if (acceptJob?.isActive == true) return
        acceptJob = scope.launch(Dispatchers.IO) {
            runCatching {
                serverSocket = bt.listenUsingRfcommWithServiceRecord(VokieProtocol.SERVICE_NAME, VokieProtocol.SERVICE_UUID)
                while (true) {
                    val accepted = serverSocket?.accept() ?: break
                    closeSocket()
                    socket = accepted; input = DataInputStream(accepted.inputStream); output = DataOutputStream(accepted.outputStream)
                    manuallyDisconnected = false
                    _connectedPeerId.value = accepted.remoteDevice.address
                    _state.value = TransportConnectionState.CONNECTED
                    VokieLog.bt("Incoming Vokie connection established")
                    listenForFrames()
                }
            }.onFailure { if (_state.value != TransportConnectionState.CONNECTED) VokieLog.bt("Server stopped: ${it.message}") }
        }
    }

    private fun scheduleReconnect(peerId: String) {
        if (manuallyDisconnected || reconnectJob?.isActive == true) return
        reconnectJob = scope.launch(Dispatchers.IO) {
            repeat(RetryPolicy.MAX_RETRIES) { attempt ->
                delay(RetryPolicy.delayMillis(attempt + 1))
                if (manuallyDisconnected || adapter?.isEnabled != true) return@launch
                if (runCatching { connect(peerId) }.isSuccess) { VokieLog.bt("Reconnected: $peerId"); return@launch }
            }
            VokieLog.bt("Reconnect limit reached: $peerId")
        }
    }

    private fun registerReceiver() {
        if (receiverRegistered) return
        ContextCompat.registerReceiver(context, discoveryReceiver, IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND); addAction(BluetoothDevice.ACTION_UUID); addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED); addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }, ContextCompat.RECEIVER_EXPORTED)
        receiverRegistered = true
    }

    private fun initialState(): TransportConnectionState = when {
        adapter == null -> TransportConnectionState.UNAVAILABLE
        !BluetoothPermission.hasConnection(context) -> TransportConnectionState.PERMISSION_REQUIRED
        !adapter.isEnabled -> TransportConnectionState.BLUETOOTH_DISABLED
        else -> TransportConnectionState.IDLE
    }
}

object VokieLog {
    fun bt(message: String) = debug("VOKIE][BT", message)
    fun msg(message: String) = debug("VOKIE][MSG", message)
    fun stt(message: String) = debug("VOKIE][STT", message)
    fun tts(message: String) = debug("VOKIE][TTS", message)
    private fun debug(tag: String, message: String) { if (com.vokie.BuildConfig.DEBUG) runCatching { android.util.Log.i(tag, message) } }
}
