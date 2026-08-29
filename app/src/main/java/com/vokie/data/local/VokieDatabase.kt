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
    @Query("UPDATE messages SET deliveryState = 'QUEUED', retryCount = 0, lastError = NULL WHERE id = :id") suspend fun resetForManualRetry(id: String)
    @Query("UPDATE messages SET deliveryState = 'QUEUED', lastError = 'Transmission interrupted; queued after restart' WHERE deliveryState = 'TRANSMITTING'") suspend fun recoverInterrupted()
    @Query("DELETE FROM messages WHERE id = :id") suspend fun delete(id: String)
}

@Dao interface PeerDao { @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(peer: PeerEntity); @Query("SELECT * FROM peers ORDER BY lastSeen DESC") fun observeAll(): Flow<List<PeerEntity>> }
@Dao interface TransportEventDao { @Insert suspend fun insert(event: TransportEventEntity); @Query("SELECT * FROM transport_events ORDER BY timestamp DESC LIMIT :limit") fun observeRecent(limit: Int = 100): Flow<List<TransportEventEntity>> }

@Database(entities = [MessageEntity::class, PeerEntity::class, TransportEventEntity::class, EmergencyAlertEntity::class, AppSettingsEntity::class], version = 2, exportSchema = true)
abstract class VokieDatabase : RoomDatabase() {
    abstract fun messages(): MessageDao
    abstract fun peers(): PeerDao
    abstract fun transportEvents(): TransportEventDao

    companion object {
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
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }
    }
}
