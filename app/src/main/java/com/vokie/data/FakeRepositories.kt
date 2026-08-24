package com.vokie.data

import com.vokie.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import java.util.UUID

interface MessageRepository {
    fun observeMessages(): Flow<List<Message>>
    suspend fun save(message: Message)
    suspend fun update(message: Message)
}

interface SettingsRepository { val settings: Flow<AppSettings>; suspend fun set(settings: AppSettings) }

class FakeMessageRepository : MessageRepository {
    private val messages = MutableStateFlow<List<Message>>(emptyList())
    override fun observeMessages(): Flow<List<Message>> = messages.asStateFlow()
    override suspend fun save(message: Message) { messages.value = messages.value + message }
    override suspend fun update(message: Message) { messages.value = messages.value.map { if (it.id == message.id) message else it } }
}

class FakeSettingsRepository : SettingsRepository {
    private val state = MutableStateFlow(AppSettings())
    override val settings: Flow<AppSettings> = state.asStateFlow()
    override suspend fun set(settings: AppSettings) { state.value = settings }
}

class DemoTransport : Transport {
    override val type = TransportType.BLUETOOTH
    private val incoming = MutableStateFlow<Message?>(null)
    override suspend fun discoverPeers() = Unit
    override suspend fun connect(peerId: String) = Unit
    override suspend fun send(message: Message) { incoming.value = message.copy(deliveryState = DeliveryState.RECEIVED_BY_PEER) }
    override fun observeMessages(): Flow<Message> = kotlinx.coroutines.flow.flow { incoming.collect { it?.let { emit(it) } } }
}

class DemoSpeechToTextEngine : SpeechToTextEngine {
    override suspend fun transcribe(audio: ByteArray): String = "அம்மா, நான் பாதுகாப்பாக இருக்கிறேன்."
}

class DemoTextToSpeechEngine : TextToSpeechEngine {
    override suspend fun synthesize(text: String, language: String) = Unit
}

fun demoMessage(text: String, type: MessageType = MessageType.TEXT) = Message(
    id = UUID.randomUUID().toString(), senderId = "THIS DEVICE", timestamp = System.currentTimeMillis(),
    text = text, language = "ta", messageType = type, transport = TransportType.BLUETOOTH,
    deliveryState = DeliveryState.RECEIVED_BY_PEER, hopCount = 1,
)
