package com.smartledger.screenshot

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

data class ScreenshotBill(
    val amount: Double?,
    val type: String,
    val merchant: String?,
    val paymentMethod: String?,
    val transactionTime: Long?,
    val foodName: String? = null,
    val rawText: String
)

/**
 * 从支付截图 OCR 文本中提取一笔账单。
 * 纯规则解析，不联网；识别结果只作为建议，保存前由用户确认。
 */
object ScreenshotBillParser {

    private const val AMOUNT = "([\\d,]+(?:\\.[\\d]{1,2})?)"

    private val amountLabelPatterns = listOf(
        Regex("实付(?:金额)?[：:\\s]*[¥]?\\s*$AMOUNT"),
        Regex("退款金额[：:\\s]*[¥]?\\s*$AMOUNT"),
        Regex("(?:到账|收款)金额[：:\\s]*[¥]?\\s*$AMOUNT"),
        Regex("(?:支付|付款)金额[：:\\s]*[¥]?\\s*$AMOUNT"),
        Regex("(?:合计|总计)[：:\\s]*[¥]?\\s*$AMOUNT"),
        Regex("订单金额[：:\\s]*[¥]?\\s*$AMOUNT")
    )

    private val amountNoise = listOf(
        "余额", "优惠", "立减", "额度", "积分", "尾号", "卡号",
        "订单号", "单号", "手续费", "服务费", "已省", "省钱"
    )

    private val merchantLabels = listOf(
        "商户名称", "商户全称", "商户", "收款方", "付款给", "转账给",
        "对方", "商家"
    )

    private val itemLabels = listOf(
        "商品名称", "商品说明", "商品", "订单名称", "订单内容"
    )

    private val bankNames = listOf(
        "工商银行", "工行", "建设银行", "建行", "中国银行", "农业银行",
        "招商银行", "邮政储蓄", "邮储", "浦发银行", "民生银行",
        "光大银行", "兴业银行", "平安银行", "中信银行", "交通银行"
    )

    private val merchantNoise = listOf(
        "账单详情", "交易详情", "账单", "详情", "支付成功", "付款成功",
        "交易成功", "已完成", "微信支付", "支付宝", "云闪付", "零钱",
        "零钱通", "余额宝", "花呗", "银行卡", "储蓄卡", "信用卡",
        "退款成功", "收款方", "付款方", "订单号", "交易单号", "商户单号",
        "账单号", "时间", "方式", "备注", "客服", "收款", "付款",
        "扫一扫", "二维码", "转账", "收入", "支出", "消费"
    )

    fun parse(
        text: String,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): ScreenshotBill {
        val normalized = normalize(text)
        val amount = extractAmount(normalized)
        val type = detectType(normalized)
        val merchant = extractMerchant(normalized)
        val foodName = extractFoodName(normalized)
        val method = detectPaymentMethod(normalized)
        val time = parseTime(normalized, now, zone)
        return ScreenshotBill(
            amount = amount,
            type = type,
            merchant = merchant,
            paymentMethod = method,
            transactionTime = time,
            foodName = foodName,
            rawText = normalized
        )
    }

    private fun normalize(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            sb.append(
                when (ch) {
                    in '\uFF10'..'\uFF19' -> (ch - '\uFF10' + '0'.code).toChar()
                    '￥', '＄' -> '¥'
                    '\u00A0' -> ' '
                    else -> ch
                }
            )
        }
        return sb.toString()
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(Regex("[\\u200B-\\u200D\\uFEFF]"), "")
            .trim()
    }

    private fun extractAmount(text: String): Double? {
        for (pattern in amountLabelPatterns) {
            for (match in pattern.findAll(text)) {
                val raw = match.groupValues.lastOrNull { value ->
                    value.any { it.isDigit() }
                } ?: continue
                parseAmount(raw)?.let { if (it > 0) return it }
            }
        }

        // 没有明确标签时，从带货币符号/「元」的行里找，跳过余额、优惠等噪音
        val candidates = mutableListOf<Pair<Int, Double>>()
        text.lines().forEachIndexed { index, line ->
            if (amountNoise.any { line.contains(it) }) return@forEachIndexed
            Regex("[¥]\\s*$AMOUNT").findAll(line).forEach { match ->
                val raw = match.groupValues.lastOrNull { it.any { c -> c.isDigit() } }
                parseAmount(raw)?.let { if (it > 0) candidates.add(index to it) }
            }
            if (candidates.isEmpty()) {
                Regex("$AMOUNT\\s*元").findAll(line).forEach { match ->
                    val raw = match.groupValues.lastOrNull { it.any { c -> c.isDigit() } }
                    parseAmount(raw)?.let { if (it > 0) candidates.add(index to it) }
                }
            }
            // 微信/支付宝详情页经常只有一行「-35.50」或「35.50」，没有「元」和「¥」
            if (Regex("^[+-]?\\s*[¥]?\\s*$AMOUNT\\s*元?$").matches(line.trim())) {
                val strong = line.contains('¥') || line.contains('.') ||
                    line.contains('元') || line.trim().startsWith("-") ||
                    line.trim().startsWith("+")
                val digitCount = line.count { it.isDigit() }
                val looksLikeYear = Regex("^(19|20)\\d{2}$").matches(line.trim())
                if (!looksLikeYear && (strong || digitCount <= 5)) {
                    val raw = Regex("$AMOUNT").find(line)?.value
                    parseAmount(raw)?.let { if (it > 0) candidates.add(index to it) }
                }
            }
        }
        return candidates.minByOrNull { it.first }?.second
    }

    private fun parseAmount(raw: String?): Double? {
        val value = raw.orEmpty()
            .replace(",", "")
            .replace("，", "")
            .trim()
            .toDoubleOrNull() ?: return null
        return value.takeIf { it > 0 && it < 10_000_000 }
    }

    private fun detectType(text: String): String {
        val bankIncome = text.contains("收入(") || text.contains("工资")
        val bankExpense = text.contains("支出(") || text.contains("消费(")
        if (bankIncome && !bankExpense) return "income"
        if (bankExpense && !bankIncome) return "expense"

        val refundOrReceipt =
            text.contains("退款成功") ||
                text.contains("已退款") ||
                text.contains("退款金额") ||
                text.contains("已到账") ||
                text.contains("入账") ||
                text.contains("转入") ||
                text.contains("收到转账") ||
                Regex("收款(?!\\s*方)").containsMatchIn(text)

        return if (refundOrReceipt) "income" else "expense"
    }

    private fun extractMerchant(text: String): String? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

        for ((index, line) in lines.withIndex()) {
            for (label in merchantLabels) {
                val at = line.indexOf(label)
                if (at < 0) continue
                var value = line.substring(at + label.length)
                    .trimStart('：', ':', ' ', '\t')
                if (value.isBlank() && index + 1 < lines.size) {
                    value = lines[index + 1]
                }
                cleanMerchant(value)?.let { return it }
            }
        }

        // 银行动账：支出(消费京东商城) / 消费(网银在线-商户)
        Regex("(?:支出|消费|收入)\\(([^)]{1,60})\\)").findAll(text).forEach { match ->
            var value = match.groupValues[1]
            val prefixes = listOf(
                "消费", "收入", "退款", "转账", "网银在线-", "支付宝-",
                "微信支付-", "财付通-", "云闪付-", "抖音支付-"
            )
            prefixes.forEach { value = value.removePrefix(it) }
            cleanMerchant(value)?.let { return it }
        }

        // 没有标签：取第一条像商户名的非噪音文本
        for (line in lines) {
            cleanMerchant(line)?.let { return it }
        }
        return null
    }

    private fun cleanMerchant(raw: String): String? {
        var value = raw
            .replace(Regex("\\s{3,}.*$"), "")
            .trim()
            .trim('：', ':', '-', '—', '|', '·')
            .trimEnd('>', '›', '…', '.', '。')
            .trim()
        if (value.length < 2 || value.length > 40) return null
        if (value.any { it.isDigit() } && !value.any { it in '\u4e00'..'\u9fff' }) {
            // 纯数字/订单号/时间，不是商户
            if (!Regex("[\\u4e00-\\u9fff]").containsMatchIn(value)) return null
        }
        if (merchantNoise.any { value == it || value.contains(it) }) return null
        if (bankNames.any { value == it }) return null
        if (Regex("^[\\d\\s:./年-]+$").matches(value)) return null
        if (value.count { it in '\u4e00'..'\u9fff' } == 0 &&
            !Regex("[A-Za-z]{2,}").containsMatchIn(value)
        ) return null
        return value
    }

    /** 截图里的商品/订单名称，作为「吃了什么」的初始值；没有就返回 null。 */
    private fun extractFoodName(text: String): String? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        for ((index, line) in lines.withIndex()) {
            for (label in itemLabels) {
                val at = line.indexOf(label)
                if (at < 0) continue
                var value = line.substring(at + label.length)
                    .trimStart('：', ':', ' ', '\t')
                if (value.isBlank() && index + 1 < lines.size) {
                    value = lines[index + 1]
                }
                cleanFoodName(value)?.let { return it }
            }
        }
        return null
    }

    private fun cleanFoodName(raw: String): String? {
        val value = raw
            .replace(Regex("\\s{3,}.*$"), "")
            .trim()
            .trim('：', ':', '-', '—', '|', '·')
            .trimEnd('>', '›', '…', '.', '。')
            .trim()
        if (value.length !in 1..40) return null
        if (merchantNoise.any { value == it || value.contains(it) }) return null
        if (Regex("^[\\d\\s:./年-]+$").matches(value)) return null
        if (value.count { it in '\u4e00'..'\u9fff' } == 0 &&
            !Regex("[A-Za-z]{2,}").containsMatchIn(value)
        ) return null
        return value
    }

    private fun detectPaymentMethod(text: String): String? {
        bankNames.firstOrNull { text.contains(it) }?.let { bank ->
            return when (bank) {
                "工行" -> "工商银行"
                "建行" -> "建设银行"
                "邮储" -> "邮政储蓄"
                else -> bank
            }
        }
        return when {
            text.contains("微信") || text.contains("零钱") -> "微信"
            text.contains("支付宝") || text.contains("花呗") ||
                text.contains("余额宝") -> "支付宝"
            text.contains("云闪付") || text.contains("银联") -> "云闪付"
            text.contains("抖音") -> "抖音"
            text.contains("京东") -> "京东"
            text.contains("银行卡") || text.contains("储蓄卡") ||
                text.contains("信用卡") -> "银行卡"
            else -> null
        }
    }

    private fun parseTime(text: String, now: Long, zone: ZoneId): Long? {
        val nowZoned = Instant.ofEpochMilli(now).atZone(zone)

        fun build(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0): Long? =
            runCatching {
                LocalDateTime.of(year, month, day, hour, minute, second)
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
            }.getOrNull()

        Regex(
            "(20\\d{2})[-/年.](\\d{1,2})[-/月.](\\d{1,2})日?[\\sT]*(\\d{1,2})[:：](\\d{2})(?:[:：](\\d{2}))?"
        ).find(text)?.let { m ->
            build(
                m.groupValues[1].toInt(),
                m.groupValues[2].toInt(),
                m.groupValues[3].toInt(),
                m.groupValues[4].toInt(),
                m.groupValues[5].toInt(),
                m.groupValues[6].takeIf { it.isNotBlank() }?.toInt() ?: 0
            )?.let { return it }
        }

        Regex("(20\\d{2})[-/年.](\\d{1,2})[-/月.](\\d{1,2})日?")
            .find(text)?.let { m ->
                build(
                    m.groupValues[1].toInt(),
                    m.groupValues[2].toInt(),
                    m.groupValues[3].toInt(),
                    12, 0
                )?.let { return it }
            }

        Regex("(?<!\\d)(\\d{1,2})[-/月](\\d{1,2})日?[\\sT]*(\\d{1,2})[:：](\\d{2})")
            .find(text)?.let { m ->
                var year = nowZoned.year
                var time = build(
                    year,
                    m.groupValues[1].toInt(),
                    m.groupValues[2].toInt(),
                    m.groupValues[3].toInt(),
                    m.groupValues[4].toInt()
                )
                if (time != null && time > now + 24 * 60 * 60 * 1000L) {
                    year -= 1
                    time = build(
                        year,
                        m.groupValues[1].toInt(),
                        m.groupValues[2].toInt(),
                        m.groupValues[3].toInt(),
                        m.groupValues[4].toInt()
                    )
                }
                time?.let { return it }
            }

        Regex("(今天|昨天)[\\s]*([01]?\\d|2[0-3])[:：]([0-5]\\d)")
            .find(text)?.let { m ->
                val day = if (m.groupValues[1] == "昨天") nowZoned.toLocalDate().minusDays(1)
                else nowZoned.toLocalDate()
                build(
                    day.year,
                    day.monthValue,
                    day.dayOfMonth,
                    m.groupValues[2].toInt(),
                    m.groupValues[3].toInt()
                )?.let { return it }
            }

        Regex("(?<!\\d)([01]?\\d|2[0-3])[:：]([0-5]\\d)(?!\\d)")
            .find(text)?.let { m ->
                build(
                    nowZoned.year,
                    nowZoned.monthValue,
                    nowZoned.dayOfMonth,
                    m.groupValues[1].toInt(),
                    m.groupValues[2].toInt()
                )?.let { return it }
            }

        return null
    }
}
