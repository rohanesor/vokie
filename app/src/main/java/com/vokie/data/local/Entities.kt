package com.vokie.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val senderId: String,
    val timestamp: Long,
    val text: String,
    val language: String,
    val messageType: String,
    val deliveryState: String,
    val transport: String?,
    val hopCount: Int,
)

@Entity(tableName = "contacts")
data class ContactEntity(@PrimaryKey val id: String, val name: String, val phone: String, val isEmergency: Boolean)
@Entity(tableName = "emergency_alerts")
data class EmergencyAlertEntity(@PrimaryKey val id: String, val severity: String, val title: String, val body: String, val timestamp: Long, val source: String, val status: String)
@Entity(tableName = "offline_resources")
data class OfflineResourceEntity(@PrimaryKey val id: String, val category: String, val title: String, val content: String, val downloaded: Boolean)
@Entity(tableName = "map_regions")
data class MapRegionEntity(@PrimaryKey val id: String, val name: String, val downloaded: Boolean, val updatedAt: Long)
@Entity(tableName = "devices")
data class DeviceEntity(@PrimaryKey val id: String, val name: String, val lastSeen: Long, val transport: String)
@Entity(tableName = "app_settings")
data class AppSettingsEntity(@PrimaryKey val id: Int = 1, val language: String, val walkieTalkieMode: Boolean, val darkMode: Boolean, val haptics: Boolean)
