package com.smartledger.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartledger.SmartLedgerApp
import com.smartledger.ai.AiCallResult
import com.smartledger.ai.AiSettings
import com.smartledger.ai.ChatTurn
import com.smartledger.ai.DeepSeekClient
import com.smartledger.ai.QuipChatContext
import com.smartledger.ai.QuipPersona
import com.smartledger.data.db.entity.QuipMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class ChatUiState(
    val messages: List<QuipMessage> = emptyList(),
    val input: String = "",
    val sending: Boolean = false,
    /** 没有 Key 或未授权时，界面显示引导卡片而不是输入框 */
    val ready: Boolean = true,
    val checking: Boolean = true,
    val error: String? = null
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        /** 送给模型的历史条数上限：聊久了上下文会越滚越大，这里截断 */
        const val MAX_TURNS = 12
        const val LOOKBACK_DAYS = 200L
        const val TEMPERATURE = 0.9
        const val MAX_TOKENS = 400
    }

    private val app = application as SmartLedgerApp
    private val dao = app.database.quipMessageDao()
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            dao.observeAll().collect { list ->
                _state.update { it.copy(messages = list) }
            }
        }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                AiSettings.hasConsent(app) && !AiSettings.loadApiKey(app).isNullOrBlank()
            }
            _state.update { it.copy(ready = ok, checking = false) }
        }
    }

    fun onInputChange(text: String) {
        _state.update { it.copy(input = text.take(500), error = null) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    fun send(transactionId: Long? = null) {
        val text = _state.value.input.trim()
        if (text.isEmpty() || _state.value.sending) return
        _state.update { it.copy(input = "", sending = true, error = null) }

        viewModelScope.launch {
            val apiKey = withContext(Dispatchers.IO) { AiSettings.loadApiKey(app) }
            if (apiKey.isNullOrBlank() || !AiSettings.hasConsent(app)) {
                _state.update {
                    it.copy(sending = false, ready = false, input = text, error = "还没有配置 DeepSeek API Key。")
                }
                return@launch
            }

            val userMessageId = dao.insert(
                QuipMessage(
                    role = QuipMessage.ROLE_USER,
                    content = text,
                    transactionId = transactionId
                )
            )

            val system = withContext(Dispatchers.IO) { buildSystem() }
            // 只在最前面丢掉 assistant：历史被截断后可能以它的上一句开头，
            // 有些实现不接受首条为 assistant。
            val turns = withContext(Dispatchers.IO) {
                dao.recent(MAX_TURNS)
                    .asReversed()
                    .filter { it.id <= userMessageId }
                    .map { ChatTurn(it.role, it.content) }
                    .dropWhile { it.role != QuipMessage.ROLE_USER }
            }

            when (
                val result = DeepSeekClient.chat(
                    apiKey = apiKey,
                    system = system,
                    turns = turns,
                    temperature = TEMPERATURE,
                    maxTokens = MAX_TOKENS
                )
            ) {
                is AiCallResult.Success -> {
                    val content = result.content.trim()
                    if (content.isBlank()) {
                        failAndRestore(userMessageId, text, "碎碎这次没说出话，再试一次？")
                    } else {
                        dao.insert(
                            QuipMessage(
                                role = QuipMessage.ROLE_ASSISTANT,
                                content = content.take(2000),
                                transactionId = transactionId
                            )
                        )
                        _state.update { it.copy(sending = false) }
                    }
                }
                is AiCallResult.Error -> failAndRestore(userMessageId, text, result.message)
            }
        }
    }

    /** 失败时收回刚存的那条用户消息，把内容放回输入框，避免聊天记录里留下半截对话 */
    private suspend fun failAndRestore(userMessageId: Long, text: String, message: String) {
        dao.deleteById(userMessageId)
        _state.update { it.copy(sending = false, input = text, error = message) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            dao.clear()
            _state.update { it.copy(error = null) }
        }
    }

    /** 每次请求都重新取一遍账本，用户刚记的账也能被问到 */
    private suspend fun buildSystem(): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val transactions = runCatching {
            app.transactionRepository.getByTimeRangeOnce(
                now - TimeUnit.DAYS.toMillis(LOOKBACK_DAYS),
                now
            )
        }.getOrNull().orEmpty()
        val categories = runCatching {
            app.categoryRepository.getAllOnce()
        }.getOrNull().orEmpty()
        QuipPersona.buildChatSystem(QuipChatContext.build(transactions, categories, now))
    }
}
