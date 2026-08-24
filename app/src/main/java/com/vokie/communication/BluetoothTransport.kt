package com.vokie.communication

import com.vokie.domain.model.Message
import com.vokie.domain.model.Transport
import com.vokie.domain.model.TransportType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Production transport boundary. Android Bluetooth APIs belong behind this class, never in UI. */
class BluetoothTransport : Transport {
    override val type = TransportType.BLUETOOTH
    override suspend fun discoverPeers() { /* BluetoothLeScanner implementation in Phase 5 */ }
    override suspend fun connect(peerId: String) { /* GATT connection implementation in Phase 5 */ }
    override suspend fun send(message: Message) { /* chunk, checksum, acknowledge in Phase 5 */ }
    override fun observeMessages(): Flow<Message> = emptyFlow()
}
