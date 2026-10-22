package com.smartledger.ai

import android.content.Context
import android.util.Log
import com.smartledger.SmartLedgerApp
import com.smartledger.util.NotificationStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * 碎碎念的编排层：取数 → 选材 → 生成 → 记录 → 通知。
 *
 * 设计原则：
 * - **任何失败都静默**。没有素材就不说，没网就用本地台词，绝不打扰主流程。
 * - **永不让用户看到报错**。这不是记账功能，坏了也不该显得 App 出问题。
 * - 三重触发（记账落库、每日兜底、打开 App）都可能同时进来，用 Mutex 串行化，
 *   并在真正发出前再查一次间隔，避免重复。
 */
object QuipEngine {

    private const val TAG = "QuipEngine"

    /** 选材需要的历史跨度：覆盖 60 天「新分类」判断与 3 个月固定支出识别 */
    private const val LOOKBACK_DAYS = 90L

    private val mutex = Mutex()

    /**
     * @param force 忽略间隔限制（调试用），仍受开关与阈值约束
     * @return 说出的话；没说则返回 null
     */
    suspend fun maybeSpeak(context: Context, force: Boolean = false): String? =
        mutex.withLock {
            // 调用方可能是主线程（onResume），选材与偏好读取都挪到 IO
            withContext(Dispatchers.IO) { speak(context, force) }
        }

    private suspend fun speak(context: Context, force: Boolean): String? {
        if (!QuipSettings.isEnabled(context)) return null

        val now = System.currentTimeMillis()
        if (!force && !QuipSelector.shouldSpeak(now, QuipSettings.lastQuipAt(context))) return null

        val app = context.applicationContext as? SmartLedgerApp ?: return null

        val transactions = runCatching {
            app.transactionRepository.getByTimeRangeOnce(
                now - TimeUnit.DAYS.toMillis(LOOKBACK_DAYS),
                now
            )
        }.getOrNull().orEmpty()

        val categories = runCatching {
            app.categoryRepository.getAllOnce()
        }.getOrNull().orEmpty()

        val candidate = QuipSelector.select(
            transactions = transactions,
            categories = categories,
            now = now
        ) ?: return null

        val message = generate(context, candidate) ?: return null

        // 生成可能耗时数秒，落地前再确认一次间隔，避免两个触发点各说一句
        if (!force && !QuipSelector.shouldSpeak(System.currentTimeMillis(), QuipSettings.lastQuipAt(context))) {
            return null
        }

        val at = System.currentTimeMillis()
        QuipSettings.record(context, message, candidate.transactionId, at)
        NotificationStyle.notifyQuip(context, message, candidate.transactionId)
        return message
    }

    /** 优先模型，失败或没配 Key 就走本地台词 */
    private suspend fun generate(context: Context, candidate: QuipCandidate): String? {
        val apiKey = AiSettings.loadApiKey(context)
        if (!apiKey.isNullOrBlank() && AiSettings.hasConsent(context)) {
            val prompt = AiPrompt(
                system = QuipPersona.buildSystem(QuipSettings.recentMessages(context)),
                user = candidate.promptText + "\n\n" + QuipPersona.INSTRUCTION,
                temperature = QuipPersona.TEMPERATURE,
                maxTokens = QuipPersona.MAX_TOKENS
            )
            when (val result = DeepSeekClient.generate(apiKey, prompt)) {
                is AiCallResult.Success -> {
                    val text = QuipPersona.clamp(result.content)
                    if (text.isNotBlank()) return text
                }
                is AiCallResult.Error -> {
                    // 只记原因，不记内容
                    Log.d(TAG, "模型生成失败，改用本地台词：${result.message}")
                }
            }
        }
        return QuipTemplate.fallback(candidate).takeIf { it.isNotBlank() }
    }
}
