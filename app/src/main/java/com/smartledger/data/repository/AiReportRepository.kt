package com.smartledger.data.repository

import com.smartledger.data.db.dao.AiReportDao
import com.smartledger.data.db.entity.AiReport

class AiReportRepository(private val dao: AiReportDao) {

    suspend fun insert(report: AiReport): Long = dao.insert(report)

    suspend fun getLatest(month: String, tone: String): AiReport? =
        dao.getLatest(month, tone)

    suspend fun deleteFor(month: String, tone: String) = dao.deleteFor(month, tone)

    suspend fun deleteAll() = dao.deleteAll()
}
