package com.smartledger.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 「碎碎」的对话记录。
 *
 * 与账单不同：这是聊天，不是账目，所以不参与统计、不进 CSV 备份，
 * 清空历史也不影响任何账单数据。
 */
@Entity(
    tableName = "quip_messages",
    indices = [Index("createdAt")]
)
data class QuipMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** "user" 或 "assistant" */
    val role: String,
    val content: String,
    /** 这条消息围绕的那笔账单；纯闲聊时为空 */
    val transactionId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
    }
}
