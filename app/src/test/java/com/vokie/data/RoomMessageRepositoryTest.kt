package com.vokie.data

import com.vokie.data.local.MessageDao
import com.vokie.data.local.MessageEntity
import com.vokie.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RoomMessageRepositoryTest {
    @Test fun queueTransitionsAndManualRetryArePersisted() = runBlocking {
        val dao = MemoryMessageDao(); val repository = RoomMessageRepository(dao)
        val message = repository.createMessage("Hello from Vokie", "sender", "peer", VokieLanguage.EN)
        assertEquals(DeliveryState.QUEUED, repository.getMessage(message.id)?.deliveryState)
        repository.markTransmitting(message.id, TransportType.BLUETOOTH)
        assertEquals(DeliveryState.TRANSMITTING, repository.getMessage(message.id)?.deliveryState)
        assertEquals(TransportType.BLUETOOTH, repository.getMessage(message.id)?.transport)
        assertEquals(1, repository.incrementRetry(message.id, "ACK timeout"))
        assertEquals(DeliveryState.RETRYING, repository.getMessage(message.id)?.deliveryState)
        repository.markFailed(message.id, "Retry limit reached")
        assertEquals(DeliveryState.FAILED, repository.getMessage(message.id)?.deliveryState)
        repository.retry(message.id)
        assertEquals(DeliveryState.QUEUED, repository.getMessage(message.id)?.deliveryState)
        assertEquals(0, repository.getMessage(message.id)?.retryCount)
        repository.markReceived(message.id)
        assertEquals(DeliveryState.RECEIVED_BY_PEER, repository.getMessage(message.id)?.deliveryState)
    }

    @Test fun duplicateIncomingMessageUsesIdempotencyKey() = runBlocking {
        val repository = RoomMessageRepository(MemoryMessageDao())
        val incoming = Message("same-id", "sender", 1234, "Help", receiverId = "receiver")
        assertTrue(repository.persistIncoming(incoming))
        assertFalse(repository.persistIncoming(incoming))
        assertEquals(1, repository.observeMessages().first().size)
    }
}

private class MemoryMessageDao : MessageDao {
    private val rows = MutableStateFlow<List<MessageEntity>>(emptyList())
    override fun observeAll() = rows
    override fun observeOutboundQueue() = MutableStateFlow(rows.value.filter { it.deliveryState in setOf("QUEUED", "RETRYING") })
    override suspend fun find(id: String) = rows.value.firstOrNull { it.id == id }
    override suspend fun insert(entity: MessageEntity): Long { if (find(entity.id) != null) return -1; rows.value = rows.value + entity; return rows.value.size.toLong() }
    override suspend fun update(entity: MessageEntity) { rows.value = rows.value.map { if (it.id == entity.id) entity else it } }
    override suspend fun setState(id: String, state: String, error: String?) { rows.value = rows.value.map { if (it.id == id) it.copy(deliveryState = state, lastError = error) else it } }
    override suspend fun markTransmitting(id: String, transport: String) { rows.value = rows.value.map { if (it.id == id) it.copy(deliveryState = "TRANSMITTING", transport = transport, lastError = null) else it } }
    override suspend fun incrementRetry(id: String, state: String, error: String) { rows.value = rows.value.map { if (it.id == id) it.copy(deliveryState = state, retryCount = it.retryCount + 1, lastError = error) else it } }
    override suspend fun resetForManualRetry(id: String) { rows.value = rows.value.map { if (it.id == id) it.copy(deliveryState = "QUEUED", retryCount = 0, lastError = null) else it } }
    override suspend fun recoverInterrupted() { rows.value = rows.value.map { if (it.deliveryState == "TRANSMITTING") it.copy(deliveryState = "QUEUED") else it } }
    override suspend fun delete(id: String) { rows.value = rows.value.filterNot { it.id == id } }
}
