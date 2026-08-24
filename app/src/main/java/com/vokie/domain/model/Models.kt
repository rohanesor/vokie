package com.vokie.domain.model

import kotlinx.coroutines.flow.Flow

interface SpeechToTextEngine {
    suspend fun transcribe(audio: ByteArray): String
}

interface TextToSpeechEngine {
    suspend fun synthesize(text: String, language: String)
}

enum class TransportType { BLUETOOTH, WIFI_DIRECT, ULTRASONIC }
enum class DeliveryState { QUEUED, BROADCASTING, RECEIVED_BY_PEER, RELAYED, DELIVERED, FAILED }
enum class MessageType { TEXT, SOS, SAFE_CHECK_IN }
enum class CommunicationStatus { ONLINE, OFFLINE, BLUETOOTH_READY, WIFI_READY, SEARCHING, CONNECTED, TRANSMITTING, RECEIVING, QUEUED, FAILED }

data class Message(
    val id: String,
    val senderId: String,
    val timestamp: Long,
    val text: String,
    val language: String = "en",
    val messageType: MessageType = MessageType.TEXT,
    val deliveryState: DeliveryState = DeliveryState.QUEUED,
    val transport: TransportType? = null,
    val hopCount: Int = 0,
)

interface Transport {
    val type: TransportType
    suspend fun discoverPeers()
    suspend fun connect(peerId: String)
    suspend fun send(message: Message)
    fun observeMessages(): Flow<Message>
}

data class Contact(val id: String, val name: String, val phone: String = "", val isEmergency: Boolean = false)
data class EmergencyAlert(val id: String, val severity: String, val title: String, val body: String, val timestamp: Long, val source: String, val status: String)
data class OfflineResource(val id: String, val category: String, val title: String, val content: String, val downloaded: Boolean = true)
data class MapRegion(val id: String, val name: String, val downloaded: Boolean, val updatedAt: Long = 0)
data class Device(val id: String, val name: String, val lastSeen: Long, val transport: TransportType)
data class AppSettings(val language: String = "English", val walkieTalkieMode: Boolean = true, val demoMode: Boolean = true, val darkMode: Boolean = true, val haptics: Boolean = true)
