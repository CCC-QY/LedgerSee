package com.smartledger.service

import com.smartledger.data.db.entity.Transaction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DedupHelperTest {

    private val base = 1_791_000_000_000L

    private fun tx(
        amount: Double = 30.0,
        type: String = "expense",
        merchant: String? = null,
        method: String? = null,
        time: Long = base
    ) = Transaction(
        amount = amount,
        type = type,
        merchant = merchant,
        paymentMethod = method,
        source = "auto",
        transactionTime = time
    )

    private fun duplicate(
        existing: Transaction,
        merchant: String?,
        method: String?,
        now: Long = existing.transactionTime + 60_000L
    ) = DedupHelper.isDuplicate(
        existing = existing,
        amountCents = (existing.amount * 100).toLong(),
        type = existing.type,
        merchant = merchant,
        paymentMethod = method,
        now = now
    )

    @Test
    fun `wechat and bank within five minutes merge`() {
        val wechat = tx(merchant = "老王面馆", method = "微信", time = base)
        // 银行晚到 4 分钟，金额相同
        assertTrue(duplicate(wechat, merchant = "老王面馆", method = "农业银行", now = base + 240_000L))
    }

    @Test
    fun `bank merchant with channel prefix still merges`() {
        val wechat = tx(merchant = "老王面馆", method = "微信", time = base)
        assertTrue(
            duplicate(
                wechat,
                merchant = "财付通-老王面馆",
                method = "农业银行",
                now = base + 200_000L
            )
        )
    }

    @Test
    fun `one side without merchant can merge across channels`() {
        val wechat = tx(merchant = null, method = "微信", time = base)
        assertTrue(
            duplicate(
                wechat,
                merchant = "老王面馆",
                method = "工商银行",
                now = base + 180_000L
            )
        )
    }

    @Test
    fun `unrelated explicit merchants do not merge`() {
        val wechat = tx(merchant = "老王面馆", method = "微信", time = base)
        assertFalse(
            duplicate(
                wechat,
                merchant = "中石化加油站",
                method = "农业银行",
                now = base + 60_000L
            )
        )
    }

    @Test
    fun `same channel no merchant still uses ten second window`() {
        val first = tx(merchant = null, method = "微信", time = base)
        assertFalse(
            duplicate(
                first,
                merchant = null,
                method = "微信",
                now = base + 30_000L
            )
        )
        assertTrue(
            duplicate(
                first,
                merchant = null,
                method = "微信",
                now = base + 5_000L
            )
        )
    }

    @Test
    fun `same channel with merchant keeps two minute window`() {
        val first = tx(merchant = "老王面馆", method = "微信", time = base)
        assertTrue(
            duplicate(
                first,
                merchant = "老王面馆",
                method = "微信",
                now = base + 90_000L
            )
        )
    }

    @Test
    fun `different amount never merges`() {
        val first = tx(amount = 30.0, merchant = "老王面馆", method = "微信", time = base)
        assertFalse(
            DedupHelper.isDuplicate(
                existing = first,
                amountCents = 3001L,
                type = "expense",
                merchant = "老王面馆",
                paymentMethod = "农业银行",
                now = base + 10_000L
            )
        )
    }

    @Test
    fun `same amount but income vs expense never merges`() {
        val expense = tx(type = "expense", merchant = "老王面馆", method = "微信", time = base)
        assertFalse(
            DedupHelper.isDuplicate(
                existing = expense,
                amountCents = 3000L,
                type = "income",
                merchant = "老王面馆",
                paymentMethod = "农业银行",
                now = base + 10_000L
            )
        )
    }

    @Test
    fun `cross channel beyond five minutes does not merge`() {
        val wechat = tx(merchant = "老王面馆", method = "微信", time = base)
        assertFalse(
            duplicate(
                wechat,
                merchant = "老王面馆",
                method = "农业银行",
                now = base + 301_000L
            )
        )
    }
}
