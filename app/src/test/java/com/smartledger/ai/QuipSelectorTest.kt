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

class QuipSelectorTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    /** 2026-10-22 20:00，白天已经过完，昨夜 23:47 仍在 26 小时窗口内 */
    private val now = ts(2026, 10, 22, 20)

    private var nextId = 1L

    private val categories = listOf(
        Category(id = 1, name = "餐饮", icon = "", color = 0, type = "expense"),
        Category(id = 2, name = "通讯", icon = "", color = 0, type = "expense"),
        Category(id = 3, name = "医疗", icon = "", color = 0, type = "expense")
    )

    private fun ts(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private fun expense(
        amount: Double,
        categoryId: Long,
        merchant: String?,
        time: Long,
        note: String? = null
    ) = Transaction(
        id = nextId++,
        amount = amount,
        type = "expense",
        categoryId = categoryId,
        merchant = merchant,
        paymentMethod = "微信",
        note = note,
        source = "manual",
        transactionTime = time
    )

    private fun select(transactions: List<Transaction>, excluded: Set<Long> = emptySet()) =
        QuipSelector.select(
            transactions = transactions,
            categories = categories,
            now = now,
            zone = zone,
            excludedIds = excluded
        )

    @Test
    fun `深夜大额会被选中`() {
        val txs = listOf(
            expense(15.0, 1, "楼下食堂", ts(2026, 10, 10, 12)),
            expense(15.0, 1, "楼下食堂", ts(2026, 10, 13, 12)),
            expense(15.0, 1, "楼下食堂", ts(2026, 10, 16, 12)),
            expense(38.0, 1, "烧烤研究所", ts(2026, 10, 21, 23, 47))
        )

        val pick = select(txs)
        assertNotNull(pick)

        assertEquals(38.0, pick!!.amount, 0.001)
        assertTrue(QuipSignal.LATE_NIGHT in pick.signals)
        assertTrue(QuipSignal.AMOUNT_SPIKE in pick.signals)
        assertTrue(pick.score >= QuipSelector.THRESHOLD)
    }

    @Test
    fun `平淡的日常支出不说话`() {
        val txs = listOf(
            expense(15.0, 1, "楼下食堂", ts(2026, 10, 10, 12)),
            expense(15.0, 1, "楼下食堂", ts(2026, 10, 13, 12)),
            expense(15.0, 1, "楼下食堂", ts(2026, 10, 16, 12)),
            expense(16.0, 1, "楼下食堂", ts(2026, 10, 22, 12, 30))
        )

        assertNull(select(txs))
    }

    @Test
    fun `短期反复光顾会被选中`() {
        val txs = listOf(
            expense(30.0, 1, "老王烧烤", ts(2026, 10, 16, 19)),
            expense(30.0, 1, "老王烧烤", ts(2026, 10, 18, 19)),
            expense(30.0, 1, "老王烧烤", ts(2026, 10, 20, 19)),
            expense(30.0, 1, "老王烧烤", ts(2026, 10, 22, 18))
        )

        val pick = select(txs)!!

        assertTrue(QuipSignal.REPEAT_MERCHANT in pick.signals)
        assertTrue(pick.facts.repeatCount7d == 4)
    }

    @Test
    fun `特殊金额会被选中`() {
        val txs = listOf(expense(520.0, 1, "便利店", ts(2026, 10, 22, 12)))

        val pick = select(txs)!!

        assertTrue(QuipSignal.SPECIAL_AMOUNT in pick.signals)
    }

    @Test
    fun `固定支出被压低而不是被调侃`() {
        val txs = listOf(
            expense(100.0, 2, "中国移动", ts(2026, 7, 22, 10)),
            expense(100.0, 2, "中国移动", ts(2026, 8, 22, 10)),
            expense(100.0, 2, "中国移动", ts(2026, 9, 22, 10)),
            expense(100.0, 2, "中国移动", ts(2026, 10, 22, 10))
        )

        assertNull(select(txs))
    }

    @Test
    fun `久未出现的分类会被识别`() {
        val history = (1..10).map {
            expense(15.0, 1, "楼下食堂", ts(2026, 9, 20 + it, 12))
        }
        val pick = select(history + expense(60.0, 3, "社区医院", ts(2026, 10, 21, 23, 30)))!!

        assertTrue(QuipSignal.NEW_CATEGORY in pick.signals)
        assertEquals("医疗", pick.category)
    }

    @Test
    fun `窗口之外的旧账单不参与`() {
        val txs = listOf(expense(999.0, 1, "某店", ts(2026, 10, 20, 12)))

        assertNull(select(txs))
    }

    @Test
    fun `已排除的账单不会再被选中`() {
        val txs = listOf(expense(520.0, 1, "便利店", ts(2026, 10, 22, 12)))
        val id = txs.first().id

        assertNull(select(txs, excluded = setOf(id)))
    }

    @Test
    fun `间隔不足时保持安静`() {
        val last = now - 19 * 3_600_000L
        assertFalse(QuipSelector.shouldSpeak(now, last))
        assertTrue(QuipSelector.shouldSpeak(now, now - 21 * 3_600_000L))
        assertTrue(QuipSelector.shouldSpeak(now, null))
    }

    @Test
    fun `prompt 不发备注内容`() {
        val txs = listOf(
            expense(520.0, 1, "便利店", ts(2026, 10, 22, 12), note = "卡号 6222 密码 1234")
        )

        val text = select(txs)!!.promptText

        assertFalse(text.contains("1234"))
        assertFalse(text.contains("6222"))
        assertTrue(text.contains("520"))
    }

    @Test
    fun `本地兜底台词不会超长`() {
        val cases = listOf(
            expense(38.0, 1, "烧烤研究所", ts(2026, 10, 21, 23, 47)),
            expense(520.0, 1, "便利店", ts(2026, 10, 22, 12)),
            expense(3000.0, 1, "某店", ts(2026, 10, 22, 12))
        )
        val history = listOf(
            expense(15.0, 1, "楼下食堂", ts(2026, 10, 10, 12)),
            expense(15.0, 1, "楼下食堂", ts(2026, 10, 13, 12)),
            expense(15.0, 1, "楼下食堂", ts(2026, 10, 16, 12))
        )

        for (c in cases) {
            val pick = select(history + c) ?: continue
            val text = QuipTemplate.fallback(pick)
            assertTrue("空台词", text.isNotBlank())
            assertTrue("超长: $text", text.length <= QuipPersona.MAX_CHARS)
        }
    }
}
