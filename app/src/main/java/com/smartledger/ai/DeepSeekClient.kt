package com.smartledger.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

sealed class AiCallResult {
    data class Success(val content: String) : AiCallResult()
    data class Error(val message: String) : AiCallResult()
}

/**
 * DeepSeek OpenAI-compatible Chat Completions 客户端。
 * 不打印请求体、API Key 或账单内容。
 */
object DeepSeekClient {

    suspend fun generate(apiKey: String, prompt: AiPrompt): AiCallResult =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) {
                return@withContext AiCallResult.Error("还没有配置 DeepSeek API Key。")
            }

            val body = JSONObject().apply {
                put("model", AiSettings.MODEL)
                put(
                    "messages",
                    JSONArray().apply {
                        put(
                            JSONObject().apply {
                                put("role", "system")
                                put("content", prompt.system)
                            }
                        )
                        put(
                            JSONObject().apply {
                                put("role", "user")
                                put("content", prompt.user)
                            }
                        )
                    }
                )
                put("temperature", prompt.temperature)
                put("max_tokens", prompt.maxTokens)
                put("stream", false)
                // deepseek-flash 默认开启 thinking；月报只需要最终答案，
                // 关闭后可避免推理过程耗尽 max_tokens，导致 content 为空。
                put("thinking", JSONObject().apply { put("type", "disabled") })
            }.toString().toByteArray(Charsets.UTF_8)

            var connection: HttpURLConnection? = null
            try {
                val conn = (URL(AiSettings.BASE_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 15_000
                    readTimeout = 90_000
                    setFixedLengthStreamingMode(body.size)
                    setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Accept-Encoding", "identity")
                    setRequestProperty("User-Agent", "SmartLedger-Android")
                }
                connection = conn
                conn.outputStream.use { it.write(body) }

                val code = conn.responseCode
                val responseText = (if (code in 200..299) {
                    conn.inputStream
                } else {
                    conn.errorStream
                })?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

                if (code !in 200..299) {
                    return@withContext AiCallResult.Error(httpErrorMessage(code, responseText))
                }

                val choice = runCatching {
                    JSONObject(responseText)
                        .getJSONArray("choices")
                        .getJSONObject(0)
                }.getOrNull()
                val message = choice?.optJSONObject("message")
                val content = message?.optString("content")
                    ?.takeUnless { it == "null" }
                    .orEmpty()
                    .trim()
                val finishReason = choice?.optString("finish_reason").orEmpty()
                val reasoning = message?.optString("reasoning_content")
                    ?.takeUnless { it == "null" }
                    .orEmpty()

                if (content.isBlank()) {
                    val error = when (finishReason) {
                        "length" -> "DeepSeek 输出被截断（达到 token 上限），请重试。"
                        "content_filter" -> "DeepSeek 内容安全过滤，未返回结果。"
                        "insufficient_system_resource" ->
                            "DeepSeek 资源暂时不足，请稍后重试。"
                        "aborted" -> "DeepSeek 生成被中断，请重试。"
                        else -> if (reasoning.isNotBlank()) {
                            "DeepSeek 只返回了推理过程，未给出最终答案，请重试。"
                        } else {
                            "DeepSeek 返回了空内容，请稍后重试。"
                        }
                    }
                    AiCallResult.Error(error)
                } else {
                    AiCallResult.Success(content)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: SocketTimeoutException) {
                AiCallResult.Error("请求超时。请检查网络后重试。")
            } catch (_: UnknownHostException) {
                AiCallResult.Error("网络不可用，或无法连接 DeepSeek。")
            } catch (_: SSLException) {
                AiCallResult.Error("安全连接失败，请检查网络代理或系统时间。")
            } catch (_: IOException) {
                AiCallResult.Error("网络请求失败，请稍后重试。")
            } catch (_: Exception) {
                AiCallResult.Error("生成失败，请稍后重试。")
            } finally {
                connection?.disconnect()
            }
        }

    private fun httpErrorMessage(code: Int, responseText: String): String {
        val apiMessage = runCatching {
            JSONObject(responseText).optJSONObject("error")?.optString("message")
        }.getOrNull()?.takeIf { it.isNotBlank() }?.take(160)

        return when (code) {
            401, 403 -> "DeepSeek API Key 无效或没有权限，请重新配置。"
            402 -> "DeepSeek 账户余额不足，请充值后重试。"
            404 -> "DeepSeek 接口地址不可用，请确认服务状态。"
            429 -> "请求过于频繁或额度受限，请稍后再试。"
            in 500..599 -> "DeepSeek 服务暂时不可用，请稍后重试。"
            else -> apiMessage?.let { "DeepSeek 请求失败（$code）：$it" }
                ?: "DeepSeek 请求失败（HTTP $code）。"
        }
    }
}
