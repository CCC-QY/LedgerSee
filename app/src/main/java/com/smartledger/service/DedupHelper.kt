package com.smartledger.service

import android.util.Log
import com.smartledger.data.db.dao.TransactionDao
import com.smartledger.data.db.entity.Transaction
import com.smartledger.util.CurrencyUtil

/**
 * 通知+短信统一去重
 * - 同渠道且双方无商户：仅 10s，避免连续两笔同额消费被吞
 * - 商户互相包含：窗口扩到 120s
 * - 跨渠道（微信/支付宝/云闪付/抖音/京东 + 银行卡）：窗口放到 5 分钟
 *
 * 跨渠道放宽是为了处理「微信/支付宝余额不足，实际扣银行卡」：
 * 支付 App 与银行 App 会各发一条通知，金额/方向相同，但到达时间可能差几分钟，
 * 商户名也可能一个为空、或银行侧带「财付通-/支付宝-/网银在线-」前缀。
 * 这类只在「一边是钱包、一边是银行」时放宽，同渠道仍保持短窗。
 */
object DedupHelper {

    private const val TAG = "DedupHelper"
    private const val BASE_WINDOW_MS = 60_000L
    private const val MERCHANT_WINDOW_MS = 120_000L
    private const val SAME_CHANNEL_NO_MERCHANT_MS = 10_000L
    private const val CROSS_CHANNEL_WINDOW_MS = 5 * 60_000L

    /** 查询窗口要覆盖最宽的规则，否则 5 分钟的候选根本查不出来 */
    private const val QUERY_WINDOW_MS = CROSS_CHANNEL_WINDOW_MS

    /** 支付 App / 钱包渠道；银行侧在 [BANK_CHANNELS] */
    private val WALLET_CHANNELS = setOf("微信", "支付宝", "云闪付", "抖音", "京东")

    private val BANK_CHANNELS = setOf(
        "银行卡",
        "工商银行", "邮政储蓄", "建设银行", "中国银行", "农业银行", "招商银行",
        "平安银行", "浦发银行", "民生银行", "光大银行", "兴业银行", "交通银行",
        "中信银行"
    )

    /** 银行通知里常见的渠道前缀，比较商户前先剥掉 */
    private val CHANNEL_PREFIXES = listOf(
        "财付通-", "财付通—", "支付宝-", "支付宝—", "微信支付-", "微信支付—",
        "云闪付-", "云闪付—", "抖音支付-", "抖音支付—", "网银在线-", "网银在线—"
    )

    /**
     * @return 已有的重复交易（若有），null 表示不重复
     */
    suspend fun findDuplicate(
        dao: TransactionDao,
        amountCents: Long,
        type: String,
        merchant: String?,
        paymentMethod: String? = null,
        now: Long = System.currentTimeMillis()
    ): Transaction? {
        val recentList = dao.getByTimeRangeOnce(now - QUERY_WINDOW_MS, now)
        return recentList.find { existing ->
            isDuplicate(
                existing = existing,
                amountCents = amountCents,
                type = type,
                merchant = merchant,
                paymentMethod = paymentMethod,
                now = now
            )
        }
    }

    /**
     * 纯判断，便于单测；不查库、不写日志。
     */
    fun isDuplicate(
        existing: Transaction,
        amountCents: Long,
        type: String,
        merchant: String?,
        paymentMethod: String?,
        now: Long
    ): Boolean {
        if (CurrencyUtil.toCents(existing.amount) != amountCents) return false
        if (existing.type != type) return false

        val timeDiff = now - existing.transactionTime
        if (timeDiff < 0) return false

        val sameMerchant = merchantMatch(merchant, existing.merchant)
        val sameChannel = !paymentMethod.isNullOrBlank() &&
            paymentMethod == existing.paymentMethod
        val bothNoMerchant = merchant.isNullOrBlank() && existing.merchant.isNullOrBlank()

        // 只对「钱包 + 银行」放宽；两边商户都明确且对不上时不合并，避免误伤
        val crossChannel = (isWalletChannel(paymentMethod) && isBankChannel(existing.paymentMethod)) ||
            (isBankChannel(paymentMethod) && isWalletChannel(existing.paymentMethod))
        val crossChannelCandidate = crossChannel && looseMerchantMatch(merchant, existing.merchant)

        val window = when {
            crossChannelCandidate -> CROSS_CHANNEL_WINDOW_MS
            sameMerchant -> MERCHANT_WINDOW_MS
            // 同渠道、无商户：短窗，防止 30s 内两笔 ¥15 被误并
            sameChannel && bothNoMerchant -> SAME_CHANNEL_NO_MERCHANT_MS
            else -> BASE_WINDOW_MS
        }
        return timeDiff < window
    }

    /** 严格匹配：双方都非空，且互相包含 */
    fun merchantMatch(new: String?, old: String?): Boolean {
        if (new.isNullOrBlank() || old.isNullOrBlank()) return false
        return new.contains(old) || old.contains(new)
    }

    /**
     * 宽松匹配：一方为空视为无法排除；双方都有值时，剥掉渠道前缀后互相包含。
     * 例：`财付通-老王面馆` vs `老王面馆` → 命中；`老王面馆` vs `中石化` → 不命中。
     */
    fun looseMerchantMatch(new: String?, old: String?): Boolean {
        if (new.isNullOrBlank() || old.isNullOrBlank()) return true
        val a = normalizeMerchant(new)
        val b = normalizeMerchant(old)
        if (a.isBlank() || b.isBlank()) return true
        return a.contains(b) || b.contains(a)
    }

    private fun normalizeMerchant(raw: String): String {
        var value = raw.trim()
        for (prefix in CHANNEL_PREFIXES) {
            if (value.startsWith(prefix)) {
                value = value.removePrefix(prefix).trim()
            }
        }
        return value
    }

    private fun isWalletChannel(method: String?): Boolean = method in WALLET_CHANNELS

    private fun isBankChannel(method: String?): Boolean = method in BANK_CHANNELS

    suspend fun mergeIfDuplicate(
        dao: TransactionDao,
        existing: Transaction,
        newMethod: String,
        newMerchant: String?
    ) {
        val shouldUpdateMethod = isMoreSpecificMethod(newMethod, existing.paymentMethod ?: "")
        val shouldUpdateMerchant = newMerchant != null &&
            newMerchant.length > (existing.merchant?.length ?: 0) &&
            !newMerchant.contains(existing.merchant ?: "§§§")

        if (shouldUpdateMethod || shouldUpdateMerchant) {
            dao.update(
                existing.copy(
                    paymentMethod = if (shouldUpdateMethod) newMethod else existing.paymentMethod,
                    merchant = if (shouldUpdateMerchant) newMerchant else existing.merchant
                )
            )
            Log.d(
                TAG,
                "Updated duplicate: method=${if (shouldUpdateMethod) newMethod else existing.paymentMethod}, " +
                    "merchant=${if (shouldUpdateMerchant) newMerchant else existing.merchant}"
            )
        }
    }

    private fun isMoreSpecificMethod(new: String, existing: String): Boolean {
        val priority = mapOf(
            "工商银行" to 10, "邮政储蓄" to 10, "建设银行" to 10,
            "中国银行" to 10, "农业银行" to 10, "招商银行" to 10,
            "平安银行" to 10, "浦发银行" to 10, "民生银行" to 10,
            "光大银行" to 10, "兴业银行" to 10, "交通银行" to 10,
            "银行卡" to 5, "支付宝" to 3, "微信" to 3, "云闪付" to 3, "抖音" to 2
        )
        return (priority[new] ?: 0) > (priority[existing] ?: 0)
    }
}
