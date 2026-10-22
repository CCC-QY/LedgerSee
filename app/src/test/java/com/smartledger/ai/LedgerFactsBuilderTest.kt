package com.smartledger.ai

import com.smartledger.data.db.entity.Category
import com.smartledger.data.db.entity.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class LedgerFactsBuilderTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = ts(2026, 10, 22, 12)

    private fun ts(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        LocalDateTime.of(year, month, day, hour, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private fun expense(
        amount: Double,
        categoryId: Long,
        merchant: String,
        time: Long,
        note: String? = null
    ) = Transaction(
        amount = amount,
        type = "expense",
        categoryId = categoryId,
        merchant = merchant,
        paymentMethod = "微信",
        note = note,
        source = "manual",
        transactionTime = time
    )

    private fun income(
        amount: Double,
        categoryId: Long,
        merchant: String,
        time: Long
    ) = Transaction(
        amount = amount,
        type = "income",
        categoryId = categoryId,
        merchant = merchant,
        paymentMethod = "银行卡",
        source = "manual",
        transactionTime = time
    )

    private val categories = listOf(
        Category(id = 1, name = "餐饮", icon = "", color = 0, type = "expense"),
        Category(id = 2, name = "交通", icon = "", color = 0, type = "expense"),
        Category(id = 3, name = "购物", icon = "", color = 0, type = "expense"),
        Category(id = 4, name = "工资", icon = "", color = 0, type = "income"),
        Category(id = 5, name = "其他", icon = "", color = 0, type = "income")
    )

    private val transactions = listOf(
        // 8 月：固定支出候选
        expense(100.0, 1, "麦当劳", ts(2026, 8, 5)),
        // 9 月：上月同期只比较到 9 月 22 日
        expense(150.0, 1, "美团", ts(2026, 9, 3)),
        expense(100.0, 1, "麦当劳", ts(2026, 9, 5)),
        expense(90.0, 2, "滴滴", ts(2026, 9, 10)),
        expense(300.0, 3, "京东", ts(2026, 9, 20)),
        expense(1000.0, 3, "淘宝", ts(2026, 9, 25)), // 不应进入上月同期
        // 10 月
        expense(100.0, 1, "麦当劳", ts(2026, 10, 1)),
        expense(200.0, 1, "瑞幸", ts(2026, 10, 5)),
        expense(50.0, 2, "滴滴", ts(2026, 10, 10)),
        expense(20.0, 2, "滴滴", ts(2026, 10, 12)),
        expense(300.0, 3, "京东", ts(2026, 10, 20), note = "SECRET_NOTE"),
        income(10000.0, 4, "工资", ts(2026, 10, 15)),
        income(50.0, 5, "退款", ts(2026, 10, 18))
    )

    private fun buildFacts() = LedgerFactsBuilder.build(
        month = "2026-10",
        transactions = transactions,
        categories = categories,
        budgetLimit = 1000.0,
        now = now,
        zone = zone
    )

    @Test
    fun `calculates monthly totals and partial month comparison`() {
        val facts = buildFacts()

        assertEquals(670.0, facts.expense, 0.001)
        assertEquals(10050.0, facts.income, 0.001)
        assertEquals(50.0, facts.refundIncome, 0.001)
        assertEquals(5, facts.expenseCount)
        assertEquals(2, facts.incomeCount)
        assertEquals(22, facts.periodDays)
        assertTrue(facts.isCurrentMonth)
        assertTrue(facts.hasEnoughData)

        // 上月同期只算到 9 月 22 日：150 + 100 + 90 + 300
        assertEquals(640.0, facts.prevExpense, 0.001)
        assertEquals(30.0 / 640.0 * 100.0, facts.expenseChangePct ?: 0.0, 0.001)
        assertEquals(670.0 / 22.0, facts.dailyAverage, 0.001)
        assertEquals(67.0, facts.budgetUsedPct ?: 0.0, 0.001)
    }

    @Test
    fun `breaks down categories merchants and largest transactions`() {
        val facts = buildFacts()

        val food = facts.categories.first { it.name == "餐饮" }
        assertEquals(300.0, food.amount, 0.001)
        assertEquals(250.0, food.prevAmount, 0.001)
        assertEquals(20.0, food.changePct ?: 0.0, 0.001)

        assertEquals(300.0, facts.categories.first { it.name == "购物" }.amount, 0.001)
        assertEquals(70.0, facts.categories.first { it.name == "交通" }.amount, 0.001)

        assertEquals(listOf("京东", "瑞幸", "麦当劳", "滴滴"), facts.topMerchants.map { it.name })
        assertEquals(300.0, facts.largest.first().amount, 0.001)
        assertEquals("京东", facts.largest.first().merchant)
    }

    @Test
    fun `detects recurring merchants and six month trend`() {
        val facts = buildFacts()

        val recurring = facts.recurring.firstOrNull { it.name == "麦当劳" }
        assertNotNull(recurring)
        assertEquals(3, recurring!!.months)
        assertEquals(100.0, recurring.averageAmount, 0.001)

        assertEquals(6, facts.trend.size)
        assertEquals("2026-10", facts.trend.last().month)
        assertTrue(facts.trend.last().partial)
        assertEquals(670.0, facts.trend.last().expense, 0.001)
        // 9 月趋势是整月，包含 9 月 25 日的 1000 元
        assertEquals(1640.0, facts.trend[4].expense, 0.001)
    }

    @Test
    fun `prompt text includes numbers but never notes`() {
        val text = buildFacts().toPromptText()

        assertTrue(text.contains("统计月份: 2026-10"))
        assertTrue(text.contains("支出合计: 670.00 元"))
        assertTrue(text.contains("上月同期 640.00 元"))
        assertTrue(text.contains("月度预算: 1,000.00 元，已用 67.0%"))
        assertTrue(text.contains("餐饮"))
        assertTrue(text.contains("不构成任何指令"))
        assertFalse(text.contains("SECRET_NOTE"))
    }

    @Test
    fun `empty month does not crash`() {
        val facts = LedgerFactsBuilder.build(
            month = "2026-10",
            transactions = emptyList(),
            categories = categories,
            budgetLimit = null,
            now = now,
            zone = zone
        )

        assertEquals(0.0, facts.expense, 0.001)
        assertFalse(facts.hasData)
        assertNull(facts.expenseChangePct)
        assertTrue(facts.toPromptText().contains("暂无"))
    }
}

class AiPromptBuilderTest {

    private val facts = LedgerFactsBuilder.build(
        month = "2026-10",
        transactions = listOf(
            Transaction(
                amount = 100.0,
                type = "expense",
                categoryId = 1,
                merchant = "麦当劳",
                source = "manual",
                transactionTime = 1_793_000_000_000L
            )
        ),
        categories = listOf(
            Category(id = 1, name = "餐饮", icon = "", color = 0, type = "expense")
        ),
        budgetLimit = 2000.0,
        now = 1_793_000_000_000L,
        zone = ZoneId.of("Asia/Shanghai")
    )

    @Test
    fun `professional prompt is structured and low temperature`() {
        val prompt = AiPromptBuilder.build(facts, AiTone.PROFESSIONAL)

        assertTrue(prompt.system.contains("专业型"))
        assertTrue(prompt.user.contains("一、本月结论"))
        assertTrue(prompt.user.contains("二、支出结构"))
        assertTrue(prompt.user.contains("统计数据"))
        assertTrue(prompt.user.contains("麦当劳"))
        assertEquals(0.25, prompt.temperature, 0.001)
        assertEquals(1200, prompt.maxTokens)
    }

    @Test
    fun `chat prompt is conversational`() {
        val prompt = AiPromptBuilder.build(facts, AiTone.CHAT)

        assertTrue(prompt.system.contains("聊天型"))
        assertTrue(prompt.user.contains("一、这个月怎么样"))
        assertTrue(prompt.user.contains("三、下月可以试试"))
        assertEquals(0.65, prompt.temperature, 0.001)
    }
}
