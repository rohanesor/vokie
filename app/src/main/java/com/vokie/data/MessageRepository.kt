package com.vokie.data

import com.vokie.data.local.MessageDao
import com.vokie.data.local.MessageEntity
import com.vokie.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface MessageRepository {
    fun observeMessages(): Flow<List<Message>>
    fun observeOutboundQueue(): Flow<List<Message>>
    suspend fun getMessage(id: String): Message?
    suspend fun createMessage(text: String, senderId: String, receiverId: String?, language: VokieLanguage, type: MessageType = MessageType.TEXT): Message
    suspend fun persistIncoming(message: Message): Boolean
    suspend fun markTransmitting(id: String, transport: TransportType)
    suspend fun markReceived(id: String)
    suspend fun markQueued(id: String, error: String? = null)
    suspend fun incrementRetry(id: String, error: String): Int
    suspend fun markFailed(id: String, error: String)
    suspend fun retry(id: String)
    suspend fun deleteMessage(id: String)
}

class RoomMessageRepository(private val dao: MessageDao) : MessageRepository {
    override fun observeMessages() = dao.observeAll().map { list -> list.map(MessageEntity::toDomain) }
    override fun observeOutboundQueue() = dao.observeOutboundQueue().map { list -> list.map(MessageEntity::toDomain) }
    override suspend fun getMessage(id: String) = dao.find(id)?.toDomain()

    override suspend fun createMessage(text: String, senderId: String, receiverId: String?, language: VokieLanguage, type: MessageType): Message {
        val message = Message(VokieMessageId.generate(), senderId, System.currentTimeMillis(), text.trim(), language.code, type, receiverId = receiverId)
        com.vokie.communication.VokieProtocol.validate(message)
        check(dao.insert(message.toEntity()) != -1L) { "Unable to persist message" }
        com.vokie.communication.VokieLog.msg("Message queued: ${message.id}")
        return message
    }

    override suspend fun persistIncoming(message: Message): Boolean {
        com.vokie.communication.VokieProtocol.validate(message)
        val inserted = dao.insert(message.copy(deliveryState = DeliveryState.RECEIVED_BY_PEER).toEntity()) != -1L
        com.vokie.communication.VokieLog.msg(if (inserted) "Message persisted: ${message.id}" else "Duplicate ignored: ${message.id}")
        return inserted
    }
    override suspend fun markTransmitting(id: String, transport: TransportType) = dao.markTransmitting(id, transport.name)
    override suspend fun markReceived(id: String) = dao.setState(id, DeliveryState.RECEIVED_BY_PEER.name)
    override suspend fun markQueued(id: String, error: String?) = dao.setState(id, DeliveryState.QUEUED.name, error)
    override suspend fun incrementRetry(id: String, error: String): Int { dao.incrementRetry(id, DeliveryState.RETRYING.name, error); return dao.find(id)?.retryCount ?: 0 }
    override suspend fun markFailed(id: String, error: String) = dao.setState(id, DeliveryState.FAILED.name, error)
    override suspend fun retry(id: String) = dao.resetForManualRetry(id)
    override suspend fun deleteMessage(id: String) = dao.delete(id)
}

object VokieMessageId { fun generate(): String = java.util.UUID.randomUUID().toString() }

fun Message.toEntity() = MessageEntity(id, senderId, receiverId, timestamp, text, language, messageType.name, deliveryState.name, transport?.name, hopCount, retryCount, requiresAck, lastError, sequenceNumber, ttlMs, priority, checksum, nextRetryAt)
fun MessageEntity.toDomain() = Message(id, senderId, timestamp, text, language, MessageType.valueOf(messageType), DeliveryState.valueOf(deliveryState), transport?.let(TransportType::valueOf), hopCount, receiverId, retryCount, requiresAck, lastError, sequenceNumber, ttlMs, priority, checksum, nextRetryAt)
