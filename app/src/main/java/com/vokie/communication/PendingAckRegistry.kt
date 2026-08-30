package com.vokie.communication

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** Single owner for transport ACK waiters. It is intentionally not a Room/application queue. */
class PendingAckRegistry {
    data class Key(val messageId: String, val sequenceNumber: Long)
    data class Entry(val key: Key, val transport: PacketTransport, val createdAt: Long, val deadline: Long, val completion: CompletableDeferred<Boolean>)
    private val entries = mutableMapOf<Key, Entry>()
    @Synchronized fun register(key: Key, transport: PacketTransport, timeoutMs: Long, now: Long = System.currentTimeMillis()): Entry {
        require(timeoutMs > 0); check(key !in entries) { "ACK already pending" }
        return Entry(key, transport, now, now + timeoutMs, CompletableDeferred()).also { entries[key] = it }
    }
    suspend fun await(entry: Entry): Boolean = try { withTimeoutOrNull((entry.deadline - System.currentTimeMillis()).coerceAtLeast(1)) { entry.completion.await() } ?: false } finally { remove(entry.key) }
    @Synchronized fun resolve(messageId: String, sequenceNumber: Long): Boolean = entries[Key(messageId, sequenceNumber)]?.completion?.complete(true) == true
    @Synchronized fun fail(key: Key) { entries.remove(key)?.completion?.complete(false) }
    @Synchronized fun clearTransport(transport: PacketTransport) { entries.values.filter { it.transport === transport }.forEach { fail(it.key) } }
    @Synchronized fun remove(key: Key) { entries.remove(key) }
    @Synchronized fun size() = entries.size
}
