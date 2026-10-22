package com.smartledger.screenshot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ScreenshotBillParserTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = LocalDateTime.of(2026, 10, 22, 15, 0)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    @Test
    fun `wechat screenshot with standalone negative amount`() {
        val text = """
            账单详情
            美团外卖
            -35.50
            支付成功
            支付方式
            零钱
            2026-10-22 14:31:23
            商户名称
            美团外卖
            交易单号 4200001234567890
        """.trimIndent()

        val bill = ScreenshotBillParser.parse(text, now, zone)
        assertEquals(35.50, bill.amount ?: 0.0, 0.001)
        assertEquals("expense", bill.type)
        assertEquals("美团外卖", bill.merchant)
        assertEquals("微信", bill.paymentMethod)
        assertNotNull(bill.transactionTime)
    }

    @Test
    fun `alipay screenshot with labeled amount and merchant`() {
        val text = """
            账单详情
            麦当劳
            ¥32.00
            交易成功
            付款方式
            花呗
            2026年10月22日 12:30
            收款方
            麦当劳
        """.trimIndent()

        val bill = ScreenshotBillParser.parse(text, now, zone)
        assertEquals(32.0, bill.amount ?: 0.0, 0.001)
        assertEquals("expense", bill.type)
        assertEquals("麦当劳", bill.merchant)
        assertEquals("支付宝", bill.paymentMethod)
    }

    @Test
    fun `refund screenshot is income and uses refund amount`() {
        val text = """
            退款成功
            退款金额 ¥42.75
            原支付方式 微信支付
            2026-10-20 09:15
        """.trimIndent()

        val bill = ScreenshotBillParser.parse(text, now, zone)
        assertEquals(42.75, bill.amount ?: 0.0, 0.001)
        assertEquals("income", bill.type)
        assertEquals("微信", bill.paymentMethod)
    }

    @Test
    fun `bank debit screenshot extracts merchant from parentheses`() {
        val text = """
            工商银行
            支出(消费京东商城)199.00元
            2026-10-20 10:00
        """.trimIndent()

        val bill = ScreenshotBillParser.parse(text, now, zone)
        assertEquals(199.0, bill.amount ?: 0.0, 0.001)
        assertEquals("expense", bill.type)
        assertEquals("京东商城", bill.merchant)
        assertEquals("工商银行", bill.paymentMethod)
    }

    @Test
    fun `order number and balance do not beat the actual amount`() {
        val text = """
            订单号 202610221234567890
            余额 ¥2000.00
            优惠 ¥2.00
            实付 ¥33.50
            2026-10-22 14:00
        """.trimIndent()

        val bill = ScreenshotBillParser.parse(text, now, zone)
        assertEquals(33.50, bill.amount ?: 0.0, 0.001)
    }

    @Test
    fun `no amount is returned instead of inventing one`() {
        val text = """
            订单详情
            支付成功
            微信支付
        """.trimIndent()

        val bill = ScreenshotBillParser.parse(text, now, zone)
        assertNull(bill.amount)
        assertEquals("expense", bill.type)
    }
}
