package com.vokie.data

import com.vokie.domain.model.Message
import kotlinx.coroutines.flow.Flow

/** Persistence boundary. Room implementation is added without changing transport/UI contracts. */
interface MessageRepository {
    fun observeMessages(): Flow<List<Message>>
    suspend fun save(message: Message)
    suspend fun update(message: Message)
}
