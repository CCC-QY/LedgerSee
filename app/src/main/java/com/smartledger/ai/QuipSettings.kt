package com.smartledger.ai

import android.content.Context

/**
 * 碎碎念的本机状态：开关、上次开口时间、最近说过的几句。
 *
 * 只存 SharedPreferences，不进数据库、不进 CSV 备份。
 * 「最近说过的几句」用于让模型换角度，避免每天同一句式。
 */
object QuipSettings {

    private const val PREFS = "quip_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST_AT = "last_at"
    private const val KEY_LAST_MESSAGE = "last_message"
    private const val KEY_LAST_TX = "last_tx"
    private const val KEY_RECENT = "recent"

    private const val MAX_RECENT = 5

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun lastQuipAt(context: Context): Long? =
        prefs(context).getLong(KEY_LAST_AT, 0L).takeIf { it > 0L }

    fun lastMessage(context: Context): String? =
        prefs(context).getString(KEY_LAST_MESSAGE, null)?.takeIf { it.isNotBlank() }

    fun lastTransactionId(context: Context): Long =
        prefs(context).getLong(KEY_LAST_TX, 0L)

    fun recentMessages(context: Context): List<String> =
        prefs(context).getString(KEY_RECENT, null)
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            .orEmpty()

    fun record(context: Context, message: String, transactionId: Long, at: Long) {
        val recent = (recentMessages(context) + message.replace('\n', ' '))
            .takeLast(MAX_RECENT)
        prefs(context).edit()
            .putLong(KEY_LAST_AT, at)
            .putLong(KEY_LAST_TX, transactionId)
            .putString(KEY_LAST_MESSAGE, message)
            .putString(KEY_RECENT, recent.joinToString("\n"))
            .apply()
    }
}
