package com.smartledger.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.smartledger.data.db.entity.QuipMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface QuipMessageDao {

    @Query("SELECT * FROM quip_messages ORDER BY createdAt ASC, id ASC")
    fun observeAll(): Flow<List<QuipMessage>>

    /** 送给模型的历史：只取最近 N 条，避免上下文无限增长 */
    @Query("SELECT * FROM quip_messages ORDER BY createdAt DESC, id DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<QuipMessage>

    @Insert
    suspend fun insert(message: QuipMessage): Long

    @Query("DELETE FROM quip_messages")
    suspend fun clear()

    @Query("DELETE FROM quip_messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM quip_messages")
    suspend fun count(): Int
}
