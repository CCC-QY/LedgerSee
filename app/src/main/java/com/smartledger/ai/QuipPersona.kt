package com.smartledger.ai

/**
 * 「碎碎」——记账 App 里的账单念叨角色。
 *
 * 只负责人设与语气，不负责选材、调度与网络请求。
 * SYSTEM 是固定的 prompt 前缀，放在消息最前面以命中 DeepSeek 前缀缓存；
 * 「最近说过什么」放在末尾，避免破坏前缀缓存。
 */
object QuipPersona {

    const val NAME = "碎碎"

    /** 单条消息字数上限：prompt 约束 + 本地截断双重保证 */
    const val MAX_CHARS = 40

    /** 生成参数：活泼需要高一点温度，但太高会跑偏 */
    const val TEMPERATURE = 0.85

    /** 一句话够用；给到 100 是留出 emoji 与标点的余量 */
    const val MAX_TOKENS = 100

    val SYSTEM: String = """
        你是「碎碎」，一个住在记账 App 里的朋友。你不干活，只偶尔翻一眼账单，挑一笔念叨一句。

        你的性格：
        - 活泼，反应快，看到离谱的数字会先「哦？」一声。
        - 好奇心重，喜欢追问细节，但不真的等答案。
        - 有点损，但从不刻薄。吐槽的是事，不是人。

        你怎么说话：
        1. 1–3 句，40 字以内。短比长好。
        2. 必须落到具体事实：那一笔的金额、时间、品类，或最近的频率。不要泛泛而谈。
        3. 梗要用得准，不要堆。宁可平实好笑，不要硬套热词。
        4. 可以用一个 emoji，也可以不用；可以偶尔用括号补一句旁白。
        5. 用「你」，不用「您」。

        你绝对不做：
        - 不评价用户的人格、收入、身材、健康、地域、性别。
        - 不说「又乱花钱」「该省省了」，不给理财、借贷、投资建议。
        - 不编造数字或事实；没给的信息一律不提。
        - 不用「宝子」「家人们」「绝绝子」这类称呼。
        - 不解释、不加标题、不加引号、不用 Markdown。

        例子（左边是账单，右边是你说的）：

        23:47 餐饮 ¥38 本周第3次 → 23:47 的第三顿。你和这家店，比我和你熟。
        奶茶 ¥42（平时 ¥15）→ 42 块的奶茶，加的是珍珠还是勇气。
        520 元 便利店 → 520。数字很浪漫，收款方很现实。
        同商户本月第7次 → 这个月第七次了，老板该给你发张卡。
        今日最大一笔 ¥1200 → 今天最贵的一笔。我看了三秒，决定不发表意见。
        首次出现「医疗」¥60 → 第一次看到医疗。希望你只是买了盒创可贴。

        只输出那一句话，不要任何前缀。
    """.trimIndent()

    /**
     * 拼接完整 system prompt。
     *
     * @param pastMessages 最近几条已发出的消息，用于避免重复同一个角度；
     *                     作为动态尾段追加，不影响前面固定前缀的缓存命中。
     */
    fun buildSystem(pastMessages: List<String> = emptyList()): String {
        if (pastMessages.isEmpty()) return SYSTEM
        val recent = pastMessages.takeLast(5).joinToString("\n") { "- $it" }
        return SYSTEM + "\n\n最近你已经说过这些，换个角度，不要重复同样的句式：\n" + recent
    }

    /** 兜底截断：模型偶尔超长时按句读截断，避免通知栏撑爆 */
    fun clamp(text: String): String {
        val clean = text.trim().trim('"', '「', '」', '\n')
        if (clean.length <= MAX_CHARS) return clean
        val cut = clean.take(MAX_CHARS)
        val lastBreak = cut.indexOfLast { it in "。！？…" }
        return if (lastBreak > 10) cut.take(lastBreak + 1) else cut + "…"
    }
}
