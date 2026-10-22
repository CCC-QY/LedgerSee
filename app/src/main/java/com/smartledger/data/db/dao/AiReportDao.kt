package com.smartledger.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.smartledger.data.db.entity.AiReport

@Dao
interface AiReportDao {

    @Insert
    suspend fun insert(report: AiReport): Long

    @Query(
        "SELECT * FROM ai_reports WHERE month = :month AND tone = :tone " +
            "ORDER BY createdAt DESC LIMIT 1"
    )
    suspend fun getLatest(month: String, tone: String): AiReport?

    @Query("DELETE FROM ai_reports WHERE month = :month AND tone = :tone")
    suspend fun deleteFor(month: String, tone: String)

    @Query("DELETE FROM ai_reports")
    suspend fun deleteAll()
}
