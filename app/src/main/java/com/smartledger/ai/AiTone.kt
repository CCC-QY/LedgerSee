package com.smartledger.ai

enum class AiTone(
    val key: String,
    val label: String,
    val hint: String
) {
    PROFESSIONAL("professional", "专业型", "结构化、数据驱动"),
    CHAT("chat", "聊天型", "轻松、像朋友提醒");

    companion object {
        fun fromKey(key: String?): AiTone =
            entries.firstOrNull { it.key == key } ?: PROFESSIONAL
    }
}
