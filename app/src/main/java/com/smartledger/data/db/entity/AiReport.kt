package com.smartledger.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * AI 月度复盘缓存。
 * 每次生成都保留一条记录，按月份 + 语气取最新一条即可。
 */
@Entity(
    tableName = "ai_reports",
    indices = [Index(value = ["month", "tone"])]
)
data class AiReport(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val month: String,
    val tone: String,
    val content: String,
    val factsText: String,
    val model: String,
    val createdAt: Long = System.currentTimeMillis()
)
