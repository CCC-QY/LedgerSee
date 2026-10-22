package com.smartledger.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationParserTest {

    private val wechat = "com.tencent.mm"
    private val alipay = "com.eg.android.AlipayGphone"
    private val unionpay = "com.unionpay"
    private val icbc = "com.icbc"
    private val ccb = "com.ccb.start"
    private val jd = "com.jingdong.app.mall"

    private fun parse(
        title: String,
        content: String,
        packageName: String,
        isChatMessage: Boolean = false
    ): ParsedPayment? = NotificationParser.parse(title, content, packageName, isChatMessage)

    private fun requireParsed(
        title: String,
        content: String,
        packageName: String,
        isChatMessage: Boolean = false
    ): ParsedPayment {
        return parse(title, content, packageName, isChatMessage)
            ?: throw AssertionError("Expected a parsed bill for: $title / $content")
    }

    private fun assertExpense(
        title: String,
        content: String,
        packageName: String,
        amount: Double,
        confidence: ParseConfidence = ParseConfidence.HIGH
    ) {
        val parsed = requireParsed(title, content, packageName)
        assertEquals("type", "expense", parsed.type)
        assertEquals("amount", amount, parsed.amount, 0.001)
        assertEquals("confidence", confidence, parsed.confidence)
    }

    private fun assertIncome(
        title: String,
        content: String,
        packageName: String,
        amount: Double,
        confidence: ParseConfidence = ParseConfidence.HIGH
    ) {
        val parsed = requireParsed(title, content, packageName)
        assertEquals("type", "income", parsed.type)
        assertEquals("amount", amount, parsed.amount, 0.001)
        assertEquals("confidence", confidence, parsed.confidence)
    }

    private fun assertIgnored(
        title: String,
        content: String,
        packageName: String,
        isChatMessage: Boolean = false
    ) {
        assertNull(
            "Expected no bill for: $title / $content",
            parse(title, content, packageName, isChatMessage)
        )
    }

    @Test
    fun `existing payment channels still work`() {
        assertExpense("微信支付", "已支付¥16.00", wechat, 16.0)
        assertExpense("微信支付", "[3条]微信支付：已支付¥648.00", wechat, 648.0)
        assertExpense("微信支付", "付款码付款成功 已支付¥16.00", wechat, 16.0)
        assertExpense("支付宝", "你有一笔42.75元的支出", alipay, 42.75)
        assertExpense("云闪付", "消费人民币35.00元", unionpay, 35.0)
        assertExpense("工商银行", "支出(消费支付宝-某商户)79元", icbc, 79.0)
        assertExpense("抖音支付", "抖音支付：已支付¥59.90", "com.ss.android.ugc.aweme", 59.9)
    }

    @Test
    fun `income formats are not dropped or reversed`() {
        assertIncome("支付宝", "收款¥100.00", alipay, 100.0)
        assertIncome("支付宝", "收款100.00元", alipay, 100.0)
        assertIncome("支付宝", "到账¥100.00", alipay, 100.0)
        assertIncome("支付宝", "退款¥42.75", alipay, 42.75)
        assertIncome("支付宝", "退款成功，订单金额100元，退款金额50元", alipay, 50.0)
        assertIncome("支付宝", "你有一笔42.75元的收入", alipay, 42.75)
        assertIncome("微信支付", "收款¥100.00", wechat, 100.0)
        assertIncome("微信支付", "已收款¥100.00", wechat, 100.0)
        assertIncome("微信支付", "收款100.00元", wechat, 100.0)
        assertIncome("微信支付", "收到转账¥100.00", wechat, 100.0)
        assertIncome("微信支付", "红包¥100.00", wechat, 100.0)
        assertIncome("微信支付", "退款42.75元", wechat, 42.75)
        assertIncome("建设银行", "退款¥42.75已到账", ccb, 42.75)
        assertIncome("工商银行", "动账通知：收入(工资)6,064.76元", icbc, 6064.76)
        assertIncome("建设银行", "收入人民币1,234.56元", ccb, 1234.56)
    }

    @Test
    fun `refund pending and failed are ignored`() {
        assertIgnored("工商银行", "退款申请已提交，预计3个工作日到账100元", icbc)
        assertIgnored("工商银行", "退款失败，100元将原路退回", icbc)
        assertIgnored("支付宝", "退款处理中，预计100元到账", alipay)
        assertIgnored("工商银行", "工资预计3个工作日到账100元", icbc)
        assertIncome("工商银行", "退款成功，100元已到账", icbc, 100.0)
    }

    @Test
    fun `bank marketing and installments are ignored`() {
        assertIgnored("工商银行", "您尾号1234的信用卡消费1000元可享分期优惠", icbc)
        assertIgnored("工商银行", "您尾号1234信用卡本期消费1000元，可办理分期", icbc)
        assertIgnored("工商银行", "您尾号1234信用卡本期消费1000元，最低还款100元", icbc)
        assertExpense("工商银行", "您尾号1234的信用卡消费100.00元", icbc, 100.0)
        assertExpense("工商银行", "您尾号1234信用卡消费100元，积分到账100分", icbc, 100.0)
        assertExpense("工商银行", "支出人民币6,064.76元", icbc, 6064.76)
    }

    @Test
    fun `chat messages are ignored while official payment notices pass`() {
        assertIgnored("张三", "我已经支付¥100给他了", wechat, isChatMessage = true)
        assertIgnored("王五", "退款¥100已经到账了", wechat, isChatMessage = true)
        assertIgnored("李四", "收款¥100", alipay, isChatMessage = true)

        // 官方支付通知即使带 MessagingStyle 标记也必须放行
        val officialWechat = requireParsed("微信支付", "已支付¥100.00", wechat, isChatMessage = true)
        assertEquals("expense", officialWechat.type)
        assertEquals(100.0, officialWechat.amount, 0.001)

        val officialAlipay = requireParsed(
            "支付宝", "你有一笔42.75元的支出", alipay, isChatMessage = true
        )
        assertEquals("expense", officialAlipay.type)
        assertEquals(42.75, officialAlipay.amount, 0.001)
    }

    @Test
    fun `bank parenthesized amounts get correct confidence`() {
        assertExpense("工商银行", "支出(消费支付宝-某商户)79元", icbc, 79.0)
        assertExpense("工商银行", "支出(消费支付宝-某商户)79.00元", icbc, 79.0)
        assertExpense(
            "工商银行",
            "支出(消费抖音支付-某商户)35....",
            icbc,
            35.0,
            ParseConfidence.UNCERTAIN
        )
        assertExpense(
            "工商银行",
            "支出(消费支付宝-某商户)79",
            icbc,
            79.0,
            ParseConfidence.UNCERTAIN
        )
    }

    @Test
    fun `noise and credit products stay ignored`() {
        assertIgnored("微信支付", "每天5元干饭，5元请你吃外卖", wechat)
        assertIgnored("京东", "您已省回648.13元，省钱金额已达648.13元", jd)
        assertIgnored("支付宝", "花呗支付100.00元", alipay)
        assertIgnored("工商银行", "您尾号1234账户余额10000.00元", icbc)
        assertIgnored("支付宝", "积分到账1000元优惠券待领取", alipay)
    }

    @Test
    fun `thousand separators card tails and payment methods`() {
        val jdPayment = requireParsed(
            "京东", "使用【工商银行储蓄卡(7619)】支付16.90", jd
        )
        assertEquals("expense", jdPayment.type)
        assertEquals(16.90, jdPayment.amount, 0.001)
        assertEquals("工商银行", jdPayment.paymentMethod)

        assertExpense("工商银行", "尾号7619储蓄卡支出100.00元", icbc, 100.0)
    }
}
