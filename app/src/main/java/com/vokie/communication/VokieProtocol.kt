package com.vokie.communication

import com.vokie.domain.model.DeliveryState
import com.vokie.domain.model.Message
import com.vokie.domain.model.MessageType
import com.vokie.domain.model.TransportType
import org.json.JSONObject

/** Versioned, compact JSON envelope. Audio is never sent over the wire. */
object VokieProtocol {
    const val VERSION = 1
    const val SERVICE_NAME = "Vokie Emergency Communication"
    val SERVICE_UUID = java.util.UUID.fromString("8f6f3a10-4c5b-4d22-9f1c-2c7d9f4e1a01")
    private const val MAX_FRAME_BYTES = 64 * 1024

    fun encode(message: Message, requiresAck: Boolean = true): ByteArray {
        val json = JSONObject()
            .put("v", VERSION)
            .put("id", message.id)
            .put("sid", message.senderId)
            .put("ts", message.timestamp)
            .put("lang", message.language)
            .put("type", message.messageType.name)
            .put("payload", message.text)
            .put("hops", message.hopCount)
            .put("ack", requiresAck)
            .put("kind", "message")
        return json.toString().toByteArray(Charsets.UTF_8).also { require(it.size <= MAX_FRAME_BYTES) }
    }

    fun encodeAck(messageId: String): ByteArray = JSONObject()
        .put("v", VERSION).put("kind", "ack").put("id", messageId).toString().toByteArray(Charsets.UTF_8)

    fun decode(bytes: ByteArray): DecodedFrame {
        require(bytes.size <= MAX_FRAME_BYTES) { "Frame exceeds protocol limit" }
        val json = JSONObject(String(bytes, Charsets.UTF_8))
        require(json.optInt("v", -1) == VERSION) { "Unsupported Vokie protocol version" }
        return if (json.optString("kind") == "ack") {
            DecodedFrame.Ack(json.getString("id"))
        } else {
            DecodedFrame.MessageFrame(
                Message(
                    id = json.getString("id"), senderId = json.getString("sid"), timestamp = json.getLong("ts"),
                    text = json.getString("payload"), language = json.optString("lang", "EN"),
                    messageType = runCatching { MessageType.valueOf(json.optString("type", "TEXT")) }.getOrDefault(MessageType.TEXT),
                    deliveryState = DeliveryState.RECEIVED_BY_PEER, transport = TransportType.BLUETOOTH,
                    hopCount = json.optInt("hops", 0),
                ), requiresAck = json.optBoolean("ack", true)
            )
        }
    }

    sealed interface DecodedFrame { data class Ack(val messageId: String) : DecodedFrame; data class MessageFrame(val message: Message, val requiresAck: Boolean) : DecodedFrame }
}
