package com.vokie.communication

import com.vokie.domain.model.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.UUID

/** Compact deterministic binary protocol. Audio is never transmitted. */
object VokieProtocol {
    const val VERSION = 1
    const val SERVICE_NAME = "iTantra Emergency Communication"
    val SERVICE_UUID: UUID = UUID.fromString("8f6f3a10-4c5b-4d22-9f1c-2c7d9f4e1a01")
    const val MAX_FRAME_BYTES = 16 * 1024
    const val MAX_TEXT_CHARS = 4096
    private const val MAGIC = 0x564B // VK
    private const val KIND_MESSAGE = 1
    private const val KIND_ACK = 2
    private const val MAX_ID_CHARS = 128

    fun newMessageId(): String = UUID.randomUUID().toString()

    fun encode(message: Message): ByteArray {
        validate(message)
        return output { out ->
            header(out, KIND_MESSAGE)
            out.writeUTF(message.id); out.writeUTF(message.senderId); out.writeUTF(message.receiverId.orEmpty())
            out.writeLong(message.timestamp); out.writeUTF(message.language); out.writeByte(message.messageType.ordinal)
            out.writeUTF(message.text); out.writeInt(message.hopCount); out.writeBoolean(message.requiresAck)
        }
    }

    fun encodeAck(messageId: String, receiverId: String, timestamp: Long): ByteArray {
        validateId(messageId, "messageId"); validateId(receiverId, "receiverId")
        return output { out -> header(out, KIND_ACK); out.writeUTF(messageId); out.writeUTF(receiverId); out.writeLong(timestamp) }
    }

    fun decode(bytes: ByteArray): DecodedFrame {
        require(bytes.isNotEmpty() && bytes.size <= MAX_FRAME_BYTES) { "Invalid frame size" }
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readUnsignedShort() == MAGIC) { "Invalid protocol magic" }
            require(input.readUnsignedByte() == VERSION) { "Unsupported iTantra protocol version" }
            when (input.readUnsignedByte()) {
                KIND_MESSAGE -> {
                    val message = Message(
                        id = input.readUTF(), senderId = input.readUTF(), receiverId = input.readUTF().ifBlank { null },
                        timestamp = input.readLong(), language = input.readUTF(),
                        messageType = MessageType.entries.getOrNull(input.readUnsignedByte()) ?: error("Unknown message type"),
                        text = input.readUTF(), hopCount = input.readInt(), requiresAck = input.readBoolean(),
                        deliveryState = DeliveryState.RECEIVED_BY_PEER, transport = TransportType.BLUETOOTH,
                    )
                    validate(message)
                    require(input.available() == 0) { "Unexpected trailing data" }
                    DecodedFrame.MessageFrame(message)
                }
                KIND_ACK -> {
                    val id = input.readUTF(); val receiverId = input.readUTF(); val timestamp = input.readLong()
                    validateId(id, "messageId"); validateId(receiverId, "receiverId")
                    require(input.available() == 0) { "Unexpected trailing data" }
                    DecodedFrame.Ack(id, receiverId, timestamp)
                }
                else -> error("Unknown frame kind")
            }
        }
    }

    fun validate(message: Message) {
        validateId(message.id, "messageId"); validateId(message.senderId, "senderId")
        message.receiverId?.let { validateId(it, "receiverId") }
        require(message.timestamp > 0) { "Invalid timestamp" }
        require(VokieLanguage.isSupported(message.language)) { "Unsupported language" }
        require(message.text.isNotBlank() && message.text.length <= MAX_TEXT_CHARS) { "Invalid payload length" }
        require(message.hopCount in 0..32) { "Invalid hop count" }
    }

    private fun validateId(value: String, name: String) { require(value.isNotBlank() && value.length <= MAX_ID_CHARS) { "Invalid $name" } }
    private fun header(out: DataOutputStream, kind: Int) { out.writeShort(MAGIC); out.writeByte(VERSION); out.writeByte(kind) }
    private inline fun output(block: (DataOutputStream) -> Unit): ByteArray {
        val bytes = ByteArrayOutputStream(); DataOutputStream(bytes).use(block)
        return bytes.toByteArray().also { require(it.size <= MAX_FRAME_BYTES) { "Frame exceeds protocol limit" } }
    }

    sealed interface DecodedFrame {
        data class Ack(val messageId: String, val receiverId: String, val timestamp: Long) : DecodedFrame
        data class MessageFrame(val message: Message) : DecodedFrame
    }
}
