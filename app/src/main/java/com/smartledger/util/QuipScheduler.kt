package com.smartledger.util

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.smartledger.ai.QuipEngine
import java.util.concurrent.TimeUnit

/**
 * 碎碎念的每日兜底。
 *
 * 它只是三条触发线之一：记账落库时与打开 App 时都会先试一次。
 * 国产 ROM 常把周期任务拖后甚至不执行，所以这里不承担主要职责，
 * 晚到无所谓，漏了也会有别的时机补上。
 */
object QuipScheduler {

    private const val WORK_NAME = "quip_daily"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<QuipWorker>(1, TimeUnit.DAYS)
            // 4 小时后首次尝试，避免装完就弹一句「最近没什么好说的」
            .setInitialDelay(4, TimeUnit.HOURS)
            .addTag(WORK_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        Log.d("QuipScheduler", "Daily quip scheduled")
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}

class QuipWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            QuipEngine.maybeSpeak(applicationContext)
        } catch (e: Exception) {
            // 娱乐功能：失败就算了，不要让系统不停重试
            Log.d("QuipWorker", "quip skipped: ${e.message}")
        }
        return Result.success()
    }
}
