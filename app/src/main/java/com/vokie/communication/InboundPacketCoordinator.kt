package com.vokie.communication

import com.vokie.data.MessageRepository
import com.vokie.data.local.ReceivedPacketDao
import com.vokie.data.local.ReceivedPacketEntity
import com.vokie.domain.model.Message
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Single authoritative raw-packet inbound path for every transport. */
class InboundPacketCoordinator(
    private val repository: MessageRepository,
    private val replayDao: ReceivedPacketDao,
    private val reassembler: PacketReassembler = PacketReassembler(),
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private val _messages = MutableSharedFlow<Message>(extraBufferCapacity = 32)
    val messages: SharedFlow<Message> = _messages.asSharedFlow()

    suspend fun accept(bytes: ByteArray, transport: PacketTransport, acknowledge: suspend (PacketTransport, String) -> Unit) {
        val decoded = runCatching { PacketV2.decode(bytes) }.getOrNull() ?: return
        if (decoded is PacketV2.Decoded.Ack) return
        val packet = decoded as PacketV2.Decoded.MessagePacket
        val complete = reassembler.add(packet, now()) ?: return
        val expiry = if (complete.ttlMs == 0L) Long.MAX_VALUE else complete.timestamp + complete.ttlMs
        if (now() >= expiry) return
        val inserted = replayDao.insert(ReceivedPacketEntity(complete.senderId, complete.id, complete.sequenceNumber, now(), expiry))
        if (inserted == -1L) return
        if (repository.persistIncoming(complete)) acknowledge(transport, complete.id)
        _messages.emit(complete)
    }
}
