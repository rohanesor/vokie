package com.vokie.communication

import com.vokie.data.local.PeerDao
import com.vokie.data.local.PeerEntity
import com.vokie.domain.model.Message
import com.vokie.domain.model.TransportConnectionState
import com.vokie.domain.model.TransportType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory, peer-scoped session state. Transport ownership and wire protocol remain
 * in their existing components; this class only records peer-facing lifecycle events.
 */
data class PeerSessionState(
    val peerId: String,
    val displayName: String? = null,
    val connectionState: TransportConnectionState = TransportConnectionState.IDLE,
    val transport: TransportType? = null,
    val lastSeen: Long? = null,
    val lastMessageTimestamp: Long? = null,
    val pendingMessageCount: Int = 0,
    val unacknowledgedMessageCount: Int = 0,
    val sourceLanguage: String? = null,
    val targetLanguage: String? = null,
    val priority: Int = 0,
)

/**
 * Session bookkeeping is deliberately explicit: events for an unregistered peer are
 * ignored rather than silently creating state or being assigned to another peer.
 */
class PeerSessionManager(
    private val peerDao: PeerDao? = null,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private data class MessageKey(val messageId: String, val sequenceNumber: Long)
    private data class PeerPending(
        val messageIds: MutableSet<String> = mutableSetOf(),
        val acknowledgements: MutableSet<MessageKey> = mutableSetOf(),
    )

    private val _sessions = MutableStateFlow<Map<String, PeerSessionState>>(emptyMap())
    val sessions: StateFlow<Map<String, PeerSessionState>> = _sessions.asStateFlow()
    private val pending = mutableMapOf<String, PeerPending>()

    @Synchronized
    fun registerPeer(peerId: String, displayName: String? = null, transport: TransportType? = null): PeerSessionState? {
        if (peerId.isBlank()) return null
        val existing = _sessions.value[peerId]
        val state = existing?.copy(
            displayName = displayName ?: existing.displayName,
            transport = transport ?: existing.transport,
        ) ?: PeerSessionState(peerId, displayName, transport = transport)
        _sessions.value = _sessions.value + (peerId to state)
        pending.getOrPut(peerId) { PeerPending() }
        if (existing == null) VokieLog.rescue("SESSION_CREATED peer=$peerId")
        return state
    }

    /**
     * Restore peer sessions from Room after process death.
     * Connection state is always set to IDLE — a peer that was CONNECTED before
     * process death cannot genuinely still be connected.
     */
    suspend fun restoreFromPersistence() {
        val dao = peerDao ?: return
        val entities = dao.getAll()
        if (entities.isEmpty()) return
        synchronized(this) {
            entities.forEach { entity ->
                val transport = runCatching { TransportType.valueOf(entity.transport) }.getOrNull()
                val state = PeerSessionState(
                    peerId = entity.id,
                    displayName = entity.deviceName,
                    connectionState = TransportConnectionState.IDLE, // never restore as CONNECTED
                    transport = transport,
                    lastSeen = entity.lastSeen,
                    sourceLanguage = entity.sourceLanguage,
                    targetLanguage = entity.targetLanguage,
                    priority = entity.priority,
                )
                _sessions.value = _sessions.value + (entity.id to state)
                pending.getOrPut(entity.id) { PeerPending() }
            }
        }
        VokieLog.rescue("SESSIONS_RESTORED count=${entities.size}")
    }

    /** Persist current session metadata to Room. Called after significant state changes. */
    suspend fun persistSession(peerId: String) {
        val dao = peerDao ?: return
        val session = getSession(peerId) ?: return
        dao.upsert(PeerEntity(
            id = session.peerId,
            deviceAddress = session.peerId,
            deviceName = session.displayName ?: session.peerId,
            protocolVersion = VokieProtocol.VERSION,
            lastSeen = session.lastSeen ?: now(),
            connectionState = session.connectionState.name,
            transport = (session.transport ?: TransportType.BLUETOOTH).name,
            isTrusted = false,
            sourceLanguage = session.sourceLanguage,
            targetLanguage = session.targetLanguage,
            priority = session.priority,
        ))
    }

    @Synchronized
    fun removePeer(peerId: String): Boolean {
        if (peerId !in _sessions.value) return false
        _sessions.value = _sessions.value - peerId
        pending.remove(peerId)
        VokieLog.rescue("SESSION_REMOVED peer=$peerId")
        return true
    }

    @Synchronized
    fun updateConnectionState(peerId: String, state: TransportConnectionState): Boolean = update(peerId) {
        VokieLog.rescue("CONNECTION_STATE peer=$peerId state=$state")
        it.copy(connectionState = state, lastSeen = now())
    }

    /** Records only a message whose sender is the explicitly supplied peer. */
    @Synchronized
    fun recordIncomingMessage(peerId: String, message: Message): Boolean {
        if (message.senderId != peerId) return false
        return update(peerId) {
            it.copy(
                lastSeen = now(),
                lastMessageTimestamp = maxTimestamp(it.lastMessageTimestamp, message.timestamp),
                sourceLanguage = message.language,
                priority = message.priority,
            ).also { VokieLog.rescue("MESSAGE_IN peer=$peerId message=${message.id}") }
        }
    }

    /** Records only a message explicitly addressed to the supplied peer. */
    @Synchronized
    fun recordOutgoingMessage(peerId: String, message: Message): Boolean {
        if (message.receiverId != peerId) return false
        return update(peerId) {
            val peerPending = pending.getOrPut(peerId) { PeerPending() }
            if (message.requiresAck) {
                peerPending.messageIds += message.id
                peerPending.acknowledgements += MessageKey(message.id, message.sequenceNumber)
            }
            // Outgoing messages do not overwrite peer priority or sourceLanguage;
            // those are properties of the remote peer's incoming communication.
            it.copy(
                lastSeen = now(),
                lastMessageTimestamp = maxTimestamp(it.lastMessageTimestamp, message.timestamp),
                pendingMessageCount = peerPending.messageIds.size,
                unacknowledgedMessageCount = peerPending.acknowledgements.size,
            ).also { VokieLog.rescue("MESSAGE_OUT peer=$peerId message=${message.id}") }
        }
    }

    /** ACK association is peer-explicit and cannot resolve another peer's message. */
    @Synchronized
    fun recordAck(peerId: String, messageId: String, sequenceNumber: Long): Boolean {
        val session = _sessions.value[peerId] ?: return false
        val peerPending = pending[peerId] ?: return false
        val key = MessageKey(messageId, sequenceNumber)
        if (!peerPending.acknowledgements.remove(key)) return false
        peerPending.messageIds.remove(messageId)
        _sessions.value = _sessions.value + (peerId to session.copy(
            lastSeen = now(),
            pendingMessageCount = peerPending.messageIds.size,
            unacknowledgedMessageCount = peerPending.acknowledgements.size,
        ))
        VokieLog.rescue("ACK peer=$peerId message=$messageId")
        return true
    }

    @Synchronized
    fun getSession(peerId: String): PeerSessionState? = _sessions.value[peerId]

    @Synchronized
    fun getSessions(): List<PeerSessionState> = _sessions.value.values.toList()

    private fun update(peerId: String, transform: (PeerSessionState) -> PeerSessionState): Boolean {
        val current = _sessions.value[peerId] ?: return false
        _sessions.value = _sessions.value + (peerId to transform(current))
        return true
    }

    private fun maxTimestamp(existing: Long?, candidate: Long): Long = maxOf(existing ?: candidate, candidate)
}
