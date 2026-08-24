package com.vokie.domain.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface SpeechToTextEngine { suspend fun transcribe(audio: ByteArray): String }
interface TextToSpeechEngine { suspend fun synthesize(text: String, language: String) }

enum class TransportType { BLUETOOTH, WIFI_DIRECT, ULTRASONIC }
enum class DeliveryState { QUEUED, BROADCASTING, RECEIVED_BY_PEER, RELAYED, DELIVERED, FAILED }
enum class MessageType { TEXT, SOS, SAFE_CHECK_IN }
enum class CommunicationStatus { ONLINE, OFFLINE, BLUETOOTH_READY, WIFI_READY, SEARCHING, CONNECTED, TRANSMITTING, RECEIVING, QUEUED, FAILED }
enum class VokieLanguage(val code: String, val displayName: String) { HI("HI", "Hindi"), GU("GU", "Gujarati"), MR("MR", "Marathi"), KN("KN", "Kannada"), ML("ML", "Malayalam"), TA("TA", "Tamil"), TE("TE", "Telugu"), OR("OR", "Odia"), BN("BN", "Bengali"), EN("EN", "English") }

data class Message(
    val id: String, val senderId: String, val timestamp: Long, val text: String,
    val language: String = VokieLanguage.EN.code, val messageType: MessageType = MessageType.TEXT,
    val deliveryState: DeliveryState = DeliveryState.QUEUED, val transport: TransportType? = null,
    val hopCount: Int = 0, val receiverId: String? = null, val retryCount: Int = 0,
)

data class Peer(val id: String, val name: String, val address: String, val bonded: Boolean, val rssi: Int? = null)
enum class TransportConnectionState { UNAVAILABLE, IDLE, SEARCHING, CONNECTING, CONNECTED, DISCONNECTED, FAILED }
data class SendResult(val messageId: String, val acknowledged: Boolean, val error: String? = null)

interface Transport {
    val type: TransportType
    val peers: StateFlow<List<Peer>>
    val connectionState: StateFlow<TransportConnectionState>
    suspend fun discoverPeers()
    suspend fun connect(peerId: String)
    suspend fun disconnect()
    suspend fun send(message: Message): SendResult
    fun observeMessages(): Flow<Message>
}

data class Contact(val id: String, val name: String, val phone: String = "", val isEmergency: Boolean = false)
data class EmergencyAlert(val id: String, val severity: String, val title: String, val body: String, val timestamp: Long, val source: String, val status: String)
data class OfflineResource(val id: String, val category: String, val title: String, val content: String, val downloaded: Boolean = true)
data class MapRegion(val id: String, val name: String, val downloaded: Boolean, val updatedAt: Long = 0)
data class Device(val id: String, val name: String, val lastSeen: Long, val transport: TransportType)
data class AppSettings(val language: String = "EN", val walkieTalkieMode: Boolean = true, val darkMode: Boolean = true, val haptics: Boolean = true)
