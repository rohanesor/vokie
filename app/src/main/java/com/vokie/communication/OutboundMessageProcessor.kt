package com.vokie.communication

import com.vokie.data.MessageRepository
import com.vokie.domain.model.TransportConnectionState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Persistent queue worker. Room is authoritative; UI lifetime is irrelevant. */
class OutboundMessageProcessor(
    private val repository: MessageRepository,
    private val transports: TransportManager,
    private val events: com.vokie.data.local.TransportEventDao,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            combine(repository.observeOutboundQueue(), transports.connectionState) { queue, state -> queue to state }
                .collect { (queue, state) ->
                    if (state == TransportConnectionState.CONNECTED && queue.isNotEmpty()) process(queue.map { it.id })
                }
        }
    }

    private suspend fun process(ids: List<String>) = mutex.withLock {
        for (id in ids) {
            val message = repository.getMessage(id)?.takeIf { it.deliveryState == com.vokie.domain.model.DeliveryState.QUEUED || it.deliveryState == com.vokie.domain.model.DeliveryState.RETRYING } ?: continue
            val transport = transports.activeTransport() ?: return
            repository.markTransmitting(id)
            events.insert(com.vokie.data.local.TransportEventEntity(timestamp = System.currentTimeMillis(), transport = transport.type.name, eventType = "TRANSMISSION_STARTED", peerId = transports.connectedPeerId.value, messageId = id, detail = null, latencyMs = null))
            val result = transport.send(message)
            if (result.acknowledged) {
                repository.markReceived(id)
                events.insert(com.vokie.data.local.TransportEventEntity(timestamp = System.currentTimeMillis(), transport = transport.type.name, eventType = "ACK_RECEIVED", peerId = transports.connectedPeerId.value, messageId = id, detail = null, latencyMs = result.ackLatencyMs))
            } else if (transports.connectionState.value != TransportConnectionState.CONNECTED) {
                repository.markQueued(id, result.error ?: "Connection lost")
                return
            } else {
                val retry = repository.incrementRetry(id, result.error ?: "No acknowledgement received")
                if (RetryPolicy.exhausted(retry)) { repository.markFailed(id, result.error ?: "Retry limit reached"); events.insert(com.vokie.data.local.TransportEventEntity(timestamp = System.currentTimeMillis(), transport = transport.type.name, eventType = "TRANSMISSION_FAILED", peerId = transports.connectedPeerId.value, messageId = id, detail = result.error, latencyMs = null)) }
                else {
                    delay(RetryPolicy.delayMillis(retry))
                    repository.markQueued(id, result.error)
                }
            }
        }
    }

    fun stop() { job?.cancel(); job = null }
}
