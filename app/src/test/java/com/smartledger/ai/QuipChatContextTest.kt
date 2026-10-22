package com.smartledger.ai

import com.smartledger.data.db.entity.Category
import com.smartledger.data.db.entity.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class QuipChatContextTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private val categories = listOf(
        Category(id = 1, name = "餐饮", icon = "", color = 0, type = "expense"),
        Category(id = 2, name = "交通", icon = "", color = 0, type = "expense"),
        Category(id = 3, name = "工资", icon = "", color = 0, type = "income")
    )

    private fun at(month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, month, day, hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private fun tx(
        id: Long,
        amount: Double,
        type: String = "expense",
        categoryId: Long? = 1,
        merchant: String? = null,
        time: Long
    ) = Transaction(
        id = id,
        amount = amount,
        type = type,
        categoryId = categoryId,
        merchant = merchant,
        paymentMethod = "微信",
        note = "不该出现在摘要里的备注",
        source = "manual",
        transactionTime = time
    )

    private val now = at(10, 22, 20, 0)

    @Test
    fun `本月与上月数字正确`() {
        val text = QuipChatContext.build(
            transactions = listOf(
                tx(1, 30.0, time = at(10, 20, 12)),
                tx(2, 12.5, time = at(10, 22, 9)),
                tx(3, 8000.0, type = "income", categoryId = 3, time = at(10, 10, 9)),
                tx(4, 500.0, time = at(9, 15, 12))
            ),
            categories = categories,
            now = now,
            zone = zone
        )

        assertTrue(text.contains("支出 42.50 元，共 2 笔"))
        assertTrue(text.contains("收入 8000.00 元"))
        assertTrue(text.contains("上月整月：支出 500.00 元"))
    }

    @Test
    fun `分类按金额排序并带占比`() {
        val text = QuipChatContext.build(
            transactions = listOf(
                tx(1, 75.0, categoryId = 1, time = at(10, 20, 12)),
                tx(2, 25.0, categoryId = 2, time = at(10, 21, 12))
            ),
            categories = categories,
            now = now,
            zone = zone
        )

        assertTrue(text.indexOf("餐饮") in 1 until text.indexOf("交通"))
        assertTrue(text.contains("75%"))
        assertTrue(text.contains("25%"))
    }

    @Test
    fun `最近十笔按时间倒序且不含备注`() {
        val transactions = (1..12).map { i ->
            tx(i.toLong(), i * 10.0, time = at(10, i, 12))
        }
        val text = QuipChatContext.build(transactions, categories, now, zone)

        val lines = text.lines().filter { it.startsWith("- ") }
        assertEquals(10, lines.size)
        assertTrue(lines.first().contains("10-12"))
        assertFalse(text.contains("不该出现在摘要里的备注"))
    }

    @Test
    fun `空账本给出明确提示`() {
        val text = QuipChatContext.build(emptyList(), categories, now, zone)
        assertTrue(text.contains("还没有任何账单"))
    }
}
