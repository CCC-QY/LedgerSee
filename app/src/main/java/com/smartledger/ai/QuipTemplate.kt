package com.smartledger.ai

/**
 * 本地兜底台词。
 *
 * 只在三种情况启用：没配 API Key、请求失败、模型输出被过滤。
 * 不追求比模型说得好，只保证「不联网也有话说」。
 *
 * 挑选规则与选材层共用 [QuipFacts]，所以不会出现「选了深夜账单却说金额」
 * 这种文不对题的情况。变体按账单 id 与日期取模，同一笔账单每次得到同一句，
 * 便于复现问题。
 */
object QuipTemplate {

    fun fallback(candidate: QuipCandidate): String {
        val f = candidate.facts
        val variants = when {
            f.isLateNight -> lateNight(f)
            f.isSpecialAmount -> special(candidate)
            f.repeatCount7d != null -> repeat(f)
            f.amountRatio != null -> spike(f)
            f.isDailyLargest -> dailyLargest(f)
            f.isNewCategory -> newCategory(candidate)
            else -> generic()
        }
        return QuipPersona.clamp(pick(variants, candidate.transactionId, candidate.time))
    }

    private fun lateNight(f: QuipFacts): List<String> = buildList {
        add("${f.hourLabel}。这个点还开着的店，都在等你。")
        add("深夜 ${f.hourLabel}，账单比你先睡不着。")
        // 当天只有这一笔时说「第 0 笔」会很怪，所以按实际情况决定是否给这句
        if (f.dayCount > 1) add("${f.hourLabel} 的第 ${f.dayCount} 笔。夜还长。")
    }

    private fun special(c: QuipCandidate): List<String> {
        val amount = QuipSelector.shortMoney(c.amount)
        return listOf(
            "$amount。数字挺会挑的。",
            "$amount，这笔花得很有仪式感。",
            "$amount。我懂，我都懂。"
        )
    }

    private fun repeat(f: QuipFacts): List<String> {
        val n = f.repeatCount7d ?: 3
        return listOf(
            "这周第 $n 次了。老板该记住你了。",
            "7 天第 $n 次。你和这家店有点东西。",
            "第 $n 次。熟客待遇谈上了吗。"
        )
    }

    private fun spike(f: QuipFacts): List<String> {
        val x = QuipSelector.shortRatio(f.amountRatio ?: 2.0)
        return listOf(
            "比平时贵 $x 倍。但愿它值。",
            "平时的 $x 倍。今天特殊？",
            "贵了 $x 倍。我记下了，没别的意思。"
        )
    }

    private fun dailyLargest(f: QuipFacts): List<String> = listOf(
        "今天最贵的一笔。剩下的时间请随意。",
        "今日最大。已阅，不评价。",
        "今天最贵的一笔，全天 ${QuipSelector.shortMoney(f.dayTotal)} 元了。"
    )

    private fun newCategory(c: QuipCandidate): List<String> = listOf(
        "第一次见「${c.category}」。希望是好事。",
        "「${c.category}」，新面孔。",
        "「${c.category}」第一次出现在你的账单里。"
    )

    private fun generic(): List<String> = listOf(
        "这笔记下了。我什么都没看见。",
        "哦？"
    )

    private fun pick(variants: List<String>, id: Long, time: Long): String {
        if (variants.isEmpty()) return ""
        val day = time / 86_400_000L
        val index = Math.floorMod(id * 31L + day, variants.size.toLong()).toInt()
        return variants[index]
    }
}
