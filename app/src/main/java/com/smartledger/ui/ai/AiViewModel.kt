package com.smartledger.ui.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartledger.SmartLedgerApp
import com.smartledger.ai.AiCallResult
import com.smartledger.ai.AiPromptBuilder
import com.smartledger.ai.AiSettings
import com.smartledger.ai.AiTone
import com.smartledger.ai.DeepSeekClient
import com.smartledger.ai.LedgerFactsBuilder
import com.smartledger.data.db.entity.AiReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

data class AiUiState(
    val month: String = YearMonth.now().toString(),
    val tone: AiTone = AiTone.PROFESSIONAL,
    val credentialsLoaded: Boolean = false,
    val apiKeyConfigured: Boolean = false,
    val consentGranted: Boolean = false,
    val showSetup: Boolean = true,
    val report: AiReport? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val setupError: String? = null,
    val notice: String? = null
)

class AiViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SmartLedgerApp
    private val _state = MutableStateFlow(
        AiUiState(tone = AiSettings.defaultTone(application))
    )
    val state: StateFlow<AiUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val configured = withContext(Dispatchers.IO) {
                AiSettings.hasApiKey(app) && AiSettings.loadApiKey(app) != null
            }
            val consent = withContext(Dispatchers.IO) { AiSettings.hasConsent(app) }
            _state.update {
                it.copy(
                    credentialsLoaded = true,
                    apiKeyConfigured = configured,
                    consentGranted = consent,
                    showSetup = !configured || !consent
                )
            }
            loadCached()
        }
    }

    fun selectMonth(month: String) {
        val current = YearMonth.now()
        val target = runCatching { YearMonth.parse(month) }.getOrNull() ?: return
        if (target > current) return
        _state.update {
            it.copy(month = target.toString(), report = null, error = null, notice = null)
        }
        loadCached()
    }

    fun previousMonth() {
        val target = YearMonth.parse(_state.value.month).minusMonths(1)
        selectMonth(target.toString())
    }

    fun nextMonth() {
        val target = YearMonth.parse(_state.value.month).plusMonths(1)
        if (target <= YearMonth.now()) selectMonth(target.toString())
    }

    fun selectTone(tone: AiTone) {
        if (_state.value.tone == tone) return
        _state.update {
            it.copy(tone = tone, report = null, error = null, notice = null)
        }
        viewModelScope.launch(Dispatchers.IO) {
            AiSettings.setDefaultTone(app, tone)
        }
        loadCached()
    }

    fun generate() {
        val current = _state.value
        if (current.isLoading) return
        if (!current.apiKeyConfigured || !current.consentGranted) {
            _state.update {
                it.copy(
                    showSetup = true,
                    setupError = "请先配置 DeepSeek API Key 并确认数据授权。"
                )
            }
            return
        }

        val month = current.month
        val tone = current.tone
        _state.update { it.copy(isLoading = true, error = null, notice = null) }

        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { runGeneration(month, tone) }
            // 用户可能在请求期间切换了月份或语气，旧结果只写库、不覆盖当前页面
            if (_state.value.month != month || _state.value.tone != tone) {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            when (outcome) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        isLoading = false,
                        report = outcome.report,
                        error = null,
                        showSetup = false
                    )
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoading = false, error = outcome.message)
                }

                Outcome.MissingKey -> _state.update {
                    it.copy(
                        isLoading = false,
                        apiKeyConfigured = false,
                        showSetup = true,
                        error = "API Key 读取失败，请重新配置。"
                    )
                }
            }
        }
    }

    fun saveApiKey(rawKey: String, consent: Boolean) {
        val key = rawKey.trim()
        if (!consent) {
            _state.update { it.copy(setupError = "请先确认数据授权。") }
            return
        }
        if (key.length < 20) {
            _state.update { it.copy(setupError = "请输入有效的 DeepSeek API Key。") }
            return
        }
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                AiSettings.saveApiKey(app, key).also {
                    if (it) AiSettings.grantConsent(app)
                }
            }
            if (saved) {
                _state.update {
                    it.copy(
                        apiKeyConfigured = true,
                        consentGranted = true,
                        showSetup = false,
                        setupError = null,
                        error = null,
                        notice = "已启用 DeepSeek"
                    )
                }
            } else {
                _state.update { it.copy(setupError = "保存 API Key 失败，请重试。") }
            }
        }
    }

    fun grantConsent() {
        if (!_state.value.apiKeyConfigured) {
            _state.update { it.copy(showSetup = true) }
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { AiSettings.grantConsent(app) }
            _state.update {
                it.copy(
                    consentGranted = true,
                    showSetup = false,
                    setupError = null,
                    error = null,
                    notice = "已确认授权"
                )
            }
        }
    }

    fun clearApiKey() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { AiSettings.clearApiKey(app) }
            _state.update {
                it.copy(
                    apiKeyConfigured = false,
                    consentGranted = false,
                    showSetup = true,
                    error = null,
                    setupError = null,
                    notice = "已清除 API Key"
                )
            }
        }
    }

    fun openSetup() {
        _state.update { it.copy(showSetup = true, setupError = null) }
    }

    fun closeSetup() {
        if (_state.value.apiKeyConfigured && _state.value.consentGranted) {
            _state.update { it.copy(showSetup = false, setupError = null) }
        }
    }

    fun clearSetupError() {
        if (_state.value.setupError != null) {
            _state.update { it.copy(setupError = null) }
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    private fun loadCached() {
        val month = _state.value.month
        val tone = _state.value.tone
        viewModelScope.launch {
            val report = withContext(Dispatchers.IO) {
                app.aiReportRepository.getLatest(month, tone.key)
            }
            if (_state.value.month == month && _state.value.tone == tone) {
                _state.update { it.copy(report = report) }
            }
        }
    }

    private suspend fun runGeneration(month: String, tone: AiTone): Outcome {
        return try {
            val key = AiSettings.loadApiKey(app) ?: return Outcome.MissingKey
            val now = System.currentTimeMillis()
            val zone = ZoneId.systemDefault()
            val ym = YearMonth.parse(month)
            val rangeStart = ym.minusMonths(6)
                .atDay(1)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
            val rangeEnd = if (ym >= YearMonth.now(zone)) {
                now
            } else {
                ym.atEndOfMonth()
                    .atTime(LocalTime.MAX)
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
            }

            val db = app.database
            val transactions = db.transactionDao().getByTimeRangeOnce(rangeStart, rangeEnd)
            val categories = db.categoryDao().getAllOnce()
            val budgetLimit = app.budgetRepository.getBudgetByMonth(month)?.totalExpenseLimit
            val facts = LedgerFactsBuilder.build(
                month = month,
                transactions = transactions,
                categories = categories,
                budgetLimit = budgetLimit,
                now = now,
                zone = zone
            )
            if (!facts.hasData) {
                return Outcome.Failure("这个月还没有账单，先记几笔再来生成复盘。")
            }

            val factsText = facts.toPromptText()
            val prompt = AiPromptBuilder.build(facts, tone)
            when (val result = DeepSeekClient.generate(key, prompt)) {
                is AiCallResult.Error -> Outcome.Failure(result.message)
                is AiCallResult.Success -> {
                    val report = AiReport(
                        month = month,
                        tone = tone.key,
                        content = result.content,
                        factsText = factsText,
                        model = AiSettings.MODEL
                    )
                    val id = app.aiReportRepository.insert(report)
                    Outcome.Success(report.copy(id = id))
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            Outcome.Failure("生成失败，请稍后重试。")
        }
    }

    private sealed class Outcome {
        data class Success(val report: AiReport) : Outcome()
        data class Failure(val message: String) : Outcome()
        data object MissingKey : Outcome()
    }
}
