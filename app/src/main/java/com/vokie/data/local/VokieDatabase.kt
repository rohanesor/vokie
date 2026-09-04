package com.vokie.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY timestamp DESC") fun observeAll(): Flow<List<MessageEntity>>
    @Query("SELECT * FROM messages WHERE deliveryState IN ('QUEUED','RETRYING') ORDER BY timestamp ASC") fun observeOutboundQueue(): Flow<List<MessageEntity>>
    @Query("SELECT * FROM messages WHERE id = :id") suspend fun find(id: String): MessageEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(entity: MessageEntity): Long
    @Update suspend fun update(entity: MessageEntity)
    @Query("UPDATE messages SET deliveryState = :state, lastError = :error WHERE id = :id") suspend fun setState(id: String, state: String, error: String? = null)
    @Query("UPDATE messages SET deliveryState = 'TRANSMITTING', transport = :transport, lastError = NULL WHERE id = :id") suspend fun markTransmitting(id: String, transport: String)
    @Query("UPDATE messages SET deliveryState = :state, retryCount = retryCount + 1, lastError = :error WHERE id = :id") suspend fun incrementRetry(id: String, state: String, error: String)
    @Query("UPDATE messages SET deliveryState = 'QUEUED', retryCount = 0, lastError = NULL, nextRetryAt = NULL WHERE id = :id") suspend fun resetForManualRetry(id: String)
    @Query("SELECT * FROM messages WHERE deliveryState = 'RETRYING' AND nextRetryAt IS NOT NULL AND nextRetryAt <= :now AND timestamp + ttlMs > :now ORDER BY timestamp ASC") suspend fun retryable(now: Long): List<MessageEntity>
    @Query("UPDATE messages SET deliveryState = 'EXPIRED', lastError = :reason WHERE deliveryState IN ('QUEUED','RETRYING','TRANSMITTING') AND timestamp + ttlMs <= :now") suspend fun expire(now: Long, reason: String = "TTL expired"): Int
    @Query("UPDATE messages SET deliveryState = 'QUEUED', lastError = 'Transmission interrupted; queued after restart' WHERE deliveryState = 'TRANSMITTING'") suspend fun recoverInterrupted()
    @Query("DELETE FROM messages WHERE id = :id") suspend fun delete(id: String)
}

@Dao
interface ReceivedPacketDao {
    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE) suspend fun insert(packet: ReceivedPacketEntity): Long
    @Query("DELETE FROM received_packets WHERE expiresAt <= :now") suspend fun deleteExpired(now: Long): Int
    @Query("SELECT COUNT(*) FROM received_packets WHERE sourceDeviceId = :source AND messageId = :message AND sequenceNumber = :sequence") suspend fun count(source: String, message: String, sequence: Long): Int
}

@Dao interface PeerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(peer: PeerEntity)
    @Query("SELECT * FROM peers ORDER BY lastSeen DESC") fun observeAll(): Flow<List<PeerEntity>>
    @Query("SELECT * FROM peers ORDER BY lastSeen DESC") suspend fun getAll(): List<PeerEntity>
    @Query("UPDATE peers SET sourceLanguage = :lang WHERE id = :id") suspend fun updateSourceLanguage(id: String, lang: String?)
    @Query("UPDATE peers SET targetLanguage = :lang WHERE id = :id") suspend fun updateTargetLanguage(id: String, lang: String?)
    @Query("UPDATE peers SET priority = :priority WHERE id = :id") suspend fun updatePriority(id: String, priority: Int)
    @Query("UPDATE peers SET connectionState = :state WHERE id = :id") suspend fun updateConnectionState(id: String, state: String)
}
@Dao interface TransportEventDao { @Insert suspend fun insert(event: TransportEventEntity); @Query("SELECT * FROM transport_events ORDER BY timestamp DESC LIMIT :limit") fun observeRecent(limit: Int = 100): Flow<List<TransportEventEntity>> }

@Database(entities = [MessageEntity::class, PeerEntity::class, TransportEventEntity::class, EmergencyAlertEntity::class, ReceivedPacketEntity::class, AppSettingsEntity::class], version = 5, exportSchema = true)
abstract class VokieDatabase : RoomDatabase() {
    abstract fun messages(): MessageDao
    abstract fun receivedPackets(): ReceivedPacketDao
    abstract fun peers(): PeerDao
    abstract fun transportEvents(): TransportEventDao

    companion object {
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE peers ADD COLUMN sourceLanguage TEXT")
                database.execSQL("ALTER TABLE peers ADD COLUMN targetLanguage TEXT")
                database.execSQL("ALTER TABLE peers ADD COLUMN priority INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) { override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) { database.execSQL("ALTER TABLE messages ADD COLUMN nextRetryAt INTEGER") } }
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) { database.execSQL("CREATE TABLE IF NOT EXISTS received_packets (sourceDeviceId TEXT NOT NULL, messageId TEXT NOT NULL, sequenceNumber INTEGER NOT NULL, receivedAt INTEGER NOT NULL, expiresAt INTEGER NOT NULL, PRIMARY KEY(sourceDeviceId, messageId, sequenceNumber))") }
        }
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE messages ADD COLUMN sequenceNumber INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE messages ADD COLUMN ttlMs INTEGER NOT NULL DEFAULT 300000")
                database.execSQL("ALTER TABLE messages ADD COLUMN priority INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE messages ADD COLUMN checksum INTEGER NOT NULL DEFAULT 0")
            }
        }
        @Volatile private var instance: VokieDatabase? = null
        fun get(context: android.content.Context): VokieDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, VokieDatabase::class.java, "vokie.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build().also { instance = it }
        }
    }
}
