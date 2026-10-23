package com.smartledger.ui.statistics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.smartledger.SmartLedgerApp
import com.smartledger.data.db.dao.CategoryTotal
import com.smartledger.data.db.dao.FoodCategoryTotal
import com.smartledger.data.db.dao.FoodNameTotal
import com.smartledger.data.db.dao.MealTotal
import com.smartledger.data.db.entity.Category
import com.smartledger.data.db.entity.Transaction
import com.smartledger.data.model.DiningDetails
import com.smartledger.util.DateUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class StatisticsViewModel(application: Application) : AndroidViewModel(application) {

    private val transactionRepo = (application as SmartLedgerApp).transactionRepository
    private val categoryRepo = (application as SmartLedgerApp).categoryRepository

    // 当前选择的周期
    private val _selectedPeriod = MutableStateFlow("month")
    val selectedPeriod: Flow<String> = _selectedPeriod

    // 根据周期计算时间范围
    private val _timeRange = MutableStateFlow(getTimeRange("month"))

    // 响应式数据 - 当周期变化时自动更新
    val expenseByCategory: Flow<List<CategoryTotal>> = _timeRange.flatMapLatest { (start, end) ->
        transactionRepo.getExpenseGroupByCategory(start, end)
    }

    val transactions: Flow<List<Transaction>> = _timeRange.flatMapLatest { (start, end) ->
        transactionRepo.getByTimeRange(start, end)
    }

    val periodExpense: Flow<Double> = _timeRange.flatMapLatest { (start, end) ->
        transactionRepo.getExpenseSum(start, end)
    }

    val periodIncome: Flow<Double> = _timeRange.flatMapLatest { (start, end) ->
        transactionRepo.getIncomeSum(start, end)
    }

    val categories: Flow<List<Category>> = categoryRepo.getAll()

    /** 「餐饮」分类 id；用户删掉/改名后为 null，餐饮明细卡自动隐藏 */
    private val diningCategoryId: Flow<Long?> = categories.map { list ->
        list.find {
            it.name == DiningDetails.DINING_CATEGORY_NAME && it.type == "expense"
        }?.id
    }

    private val diningContext: Flow<Pair<Pair<Long, Long>, Long?>> =
        combine(_timeRange, diningCategoryId) { range, id -> range to id }

    val diningByMeal: Flow<List<MealTotal>> = diningContext.flatMapLatest { (range, id) ->
        if (id == null) flowOf(emptyList<MealTotal>()) else
            transactionRepo.getDiningByMeal(id, range.first, range.second)
    }

    val diningTotal: Flow<Double> = diningContext.flatMapLatest { (range, id) ->
        if (id == null) flowOf(0.0) else
            transactionRepo.getExpenseByCategory(id, range.first, range.second)
    }

    val diningByFoodCategory: Flow<List<FoodCategoryTotal>> =
        diningContext.flatMapLatest { (range, id) ->
            if (id == null) flowOf(emptyList<FoodCategoryTotal>()) else
                transactionRepo.getDiningByFoodCategory(id, range.first, range.second)
        }

    val topDiningFoods: Flow<List<FoodNameTotal>> = diningContext.flatMapLatest { (range, id) ->
        if (id == null) flowOf(emptyList<FoodNameTotal>()) else
            transactionRepo.getTopDiningFoods(id, range.first, range.second)
    }

    fun setPeriod(period: String) {
        _selectedPeriod.value = period
        _timeRange.value = getTimeRange(period)
    }

    /** 回到前台时刷新「日/周」等相对今天的区间 */
    fun refreshTimeRange() {
        _timeRange.value = getTimeRange(_selectedPeriod.value)
    }

    private fun getTimeRange(period: String): Pair<Long, Long> {
        return when (period) {
            "day" -> Pair(DateUtil.getTodayStartTime(), DateUtil.getTodayEndTime())
            "week" -> Pair(DateUtil.getWeekStartTime(), DateUtil.getWeekEndTime())
            "year" -> Pair(DateUtil.getYearStartTime(), DateUtil.getYearEndTime())
            else -> Pair(
                DateUtil.getMonthStartTime(DateUtil.getCurrentYearMonth()),
                DateUtil.getMonthEndTime(DateUtil.getCurrentYearMonth())
            )
        }
    }
}
