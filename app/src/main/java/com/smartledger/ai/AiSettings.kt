package com.smartledger.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * DeepSeek 配置与授权状态。
 *
 * API Key 使用 Android Keystore 里的 AES-GCM 密钥加密后存入本机 SharedPreferences；
 * 不写入日志、不进入 CSV 备份。
 */
object AiSettings {

    const val BASE_URL = "https://api.deepseek.com/chat/completions"
    /** DeepSeek 当前推荐的模型名；如账户仍使用旧别名，可在这里替换 */
    const val MODEL = "deepseek-flash"
    const val CONSENT_VERSION = 1

    private const val PREFS = "ai_prefs"
    private const val KEY_API_KEY = "deepseek_api_key"
    private const val KEY_CONSENT = "consent_version"
    private const val KEY_TONE = "default_tone"

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "smartledger_ai_api_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasApiKey(context: Context): Boolean =
        !prefs(context).getString(KEY_API_KEY, null).isNullOrBlank()

    /** 读取并解密；失败时清掉失效密文，返回 null，让用户重新输入 */
    fun loadApiKey(context: Context): String? {
        val payload = prefs(context).getString(KEY_API_KEY, null)
        if (payload.isNullOrBlank()) return null
        return try {
            val bytes = Base64.decode(payload, Base64.NO_WRAP)
            if (bytes.size <= IV_BYTES) throw IllegalArgumentException("invalid payload")
            val iv = bytes.copyOfRange(0, IV_BYTES)
            val ciphertext = bytes.copyOfRange(IV_BYTES, bytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            prefs(context).edit().remove(KEY_API_KEY).apply()
            null
        }
    }

    /** @return true 表示已加密保存 */
    fun saveApiKey(context: Context, rawKey: String): Boolean {
        val key = rawKey.trim()
        if (key.isBlank()) return false
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val encrypted = cipher.doFinal(key.toByteArray(StandardCharsets.UTF_8))
            val payload = cipher.iv + encrypted
            prefs(context).edit()
                .putString(KEY_API_KEY, Base64.encodeToString(payload, Base64.NO_WRAP))
                .apply()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun clearApiKey(context: Context) {
        prefs(context).edit()
            .remove(KEY_API_KEY)
            .remove(KEY_CONSENT)
            .apply()
    }

    fun hasConsent(context: Context): Boolean =
        prefs(context).getInt(KEY_CONSENT, 0) >= CONSENT_VERSION

    fun grantConsent(context: Context) {
        prefs(context).edit().putInt(KEY_CONSENT, CONSENT_VERSION).apply()
    }

    fun defaultTone(context: Context): AiTone =
        AiTone.fromKey(prefs(context).getString(KEY_TONE, null))

    fun setDefaultTone(context: Context, tone: AiTone) {
        prefs(context).edit().putString(KEY_TONE, tone.key).apply()
    }

    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
