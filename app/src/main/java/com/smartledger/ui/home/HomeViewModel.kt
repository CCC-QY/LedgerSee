package com.smartledger.ui.home

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartledger.SmartLedgerApp
import com.smartledger.data.db.entity.Category
import com.smartledger.data.db.entity.Transaction
import com.smartledger.data.model.DiningDetails
import com.smartledger.service.SmartCategorizer
import com.smartledger.util.DateUtil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val PREFS = "smart_ledger"
        private const val KEY_INITIAL_BALANCE = "initial_balance"
        /** 合并短时间多次 Room 失效，减轻记账后首页卡顿 */
        private const val UI_DEBOUNCE_MS = 120L
    }

    private val repo = (application as SmartLedgerApp).transactionRepository
    private val categoryRepo = (application as SmartLedgerApp).categoryRepository
    private val prefs =
        application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 跨日打开仍刷新「今日」区间 */
    private val dateTick = MutableStateFlow(System.currentTimeMillis())

    /** 当前查看的月份 yyyy-MM，可切换历史 */
    private val _selectedYearMonth = MutableStateFlow(DateUtil.getCurrentYearMonth())
    val selectedYearMonth: StateFlow<String> = _selectedYearMonth.asStateFlow()

    /** 期初余额：用户设定的起始资产，总余额 = 期初 + 全部收入 - 全部支出 */
    private val _initialBalance = MutableStateFlow(loadInitialBalance())
    val initialBalance: StateFlow<Double> = _initialBalance.asStateFlow()

    private val totalIncomeFlow = repo.getTotalIncomeSum()
        .debounce(UI_DEBOUNCE_MS)
        .distinctUntilChanged()

    private val totalExpenseFlow = repo.getTotalExpenseSum()
        .debounce(UI_DEBOUNCE_MS)
        .distinctUntilChanged()

    val totalIncome: StateFlow<Double> = totalIncomeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val totalExpense: StateFlow<Double> = totalExpenseFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    /** 当前总余额（存了多少钱） */
    val totalBalance: StateFlow<Double> = combine(
        _initialBalance,
        totalIncomeFlow,
        totalExpenseFlow
    ) { initial, income, expense ->
        initial + income - expense
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _initialBalance.value)

    val todayExpense: StateFlow<Double> = dateTick.flatMapLatest {
        repo.getExpenseSum(DateUtil.getTodayStartTime(), DateUtil.getTodayEndTime())
    }
        .debounce(UI_DEBOUNCE_MS)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val monthExpense: StateFlow<Double> = combine(dateTick, _selectedYearMonth) { _, ym -> ym }
        .flatMapLatest { ym ->
            repo.getExpenseSum(DateUtil.getMonthStartTime(ym), DateUtil.getMonthEndTime(ym))
        }
        .debounce(UI_DEBOUNCE_MS)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val monthIncome: StateFlow<Double> = combine(dateTick, _selectedYearMonth) { _, ym -> ym }
        .flatMapLatest { ym ->
            repo.getIncomeSum(DateUtil.getMonthStartTime(ym), DateUtil.getMonthEndTime(ym))
        }
        .debounce(UI_DEBOUNCE_MS)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val recentTransactions: StateFlow<List<Transaction>> =
        combine(dateTick, _selectedYearMonth) { _, ym -> ym }
            .flatMapLatest { ym ->
                repo.getByTimeRange(DateUtil.getMonthStartTime(ym), DateUtil.getMonthEndTime(ym))
            }
            .debounce(UI_DEBOUNCE_MS)
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<Category>> = categoryRepo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refreshDateRange() {
        dateTick.value = System.currentTimeMillis()
    }

    fun setInitialBalance(amount: Double) {
        val safe = if (amount.isNaN() || amount.isInfinite()) 0.0 else amount
        prefs.edit().putString(KEY_INITIAL_BALANCE, safe.toString()).apply()
        _initialBalance.value = safe
    }

    private fun loadInitialBalance(): Double {
        return prefs.getString(KEY_INITIAL_BALANCE, null)?.toDoubleOrNull()
            ?: prefs.getFloat(KEY_INITIAL_BALANCE, 0f).toDouble()
    }

    fun previousMonth() {
        _selectedYearMonth.value = DateUtil.shiftYearMonth(_selectedYearMonth.value, -1)
    }

    fun nextMonth() {
        val next = DateUtil.shiftYearMonth(_selectedYearMonth.value, 1)
        // 不允许翻到未来月
        if (next <= DateUtil.getCurrentYearMonth()) {
            _selectedYearMonth.value = next
        }
    }

    fun goToCurrentMonth() {
        _selectedYearMonth.value = DateUtil.getCurrentYearMonth()
    }

    fun isCurrentMonth(): Boolean =
        _selectedYearMonth.value == DateUtil.getCurrentYearMonth()

    fun getCategoryName(categoryId: Long?, categories: List<Category>): String {
        return categories.find { it.id == categoryId }?.name ?: "其他"
    }

    fun getCategoryColor(categoryId: Long?, categories: List<Category>): Long? {
        return categories.find { it.id == categoryId }?.color
    }

    fun updateTransactionCategory(transaction: Transaction, categoryId: Long?) {
        viewModelScope.launch {
            val category = categoryId?.let { categoryRepo.getById(it) }
            val dining = DiningDetails.isDining(category?.name, transaction.type)
            val auto = DiningDetails.autoFor(
                merchant = transaction.merchant,
                note = transaction.note,
                time = transaction.transactionTime,
                isDining = dining
            )
            repo.update(
                transaction.copy(
                    categoryId = categoryId,
                    mealType = if (dining) transaction.mealType ?: auto?.mealType else null,
                    foodCategory = if (dining) transaction.foodCategory ?: auto?.foodCategory else null,
                    foodName = if (dining) transaction.foodName else null
                )
            )
            if (categoryId != null && !transaction.merchant.isNullOrBlank()) {
                SmartCategorizer.saveMerchantCategory(
                    getApplication(),
                    transaction.merchant!!,
                    categoryId
                )
            }
        }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repo.update(transaction)
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repo.delete(transaction)
        }
    }
}
