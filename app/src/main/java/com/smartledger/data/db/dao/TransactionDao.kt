package com.smartledger.data.db.dao

import androidx.room.*
import com.smartledger.data.db.entity.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert
    suspend fun insert(transaction: Transaction): Long

    @Update
    suspend fun update(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)

    @Query("SELECT * FROM transactions ORDER BY transactionTime DESC")
    fun getAll(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): Transaction?

    @Query("SELECT * FROM transactions WHERE notificationKey = :key LIMIT 1")
    suspend fun getByNotificationKey(key: String): Transaction?

    @Query("SELECT * FROM transactions WHERE transactionTime BETWEEN :startTime AND :endTime ORDER BY transactionTime DESC")
    fun getByTimeRange(startTime: Long, endTime: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE transactionTime BETWEEN :startTime AND :endTime ORDER BY transactionTime DESC")
    suspend fun getByTimeRangeOnce(startTime: Long, endTime: Long): List<Transaction>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = 'expense' AND transactionTime BETWEEN :startTime AND :endTime")
    fun getExpenseSum(startTime: Long, endTime: Long): Flow<Double>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = 'income' AND transactionTime BETWEEN :startTime AND :endTime")
    fun getIncomeSum(startTime: Long, endTime: Long): Flow<Double>

    /** 全部支出合计（不限月份） */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = 'expense'")
    fun getTotalExpenseSum(): Flow<Double>

    /** 全部收入合计（不限月份） */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = 'income'")
    fun getTotalIncomeSum(): Flow<Double>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = 'expense' AND categoryId = :categoryId AND transactionTime BETWEEN :startTime AND :endTime")
    fun getExpenseByCategory(categoryId: Long, startTime: Long, endTime: Long): Flow<Double>

    @Query("SELECT categoryId, SUM(amount) as total FROM transactions WHERE type = 'expense' AND transactionTime BETWEEN :startTime AND :endTime GROUP BY categoryId ORDER BY total DESC")
    fun getExpenseGroupByCategory(startTime: Long, endTime: Long): Flow<List<CategoryTotal>>

    // ═══ 餐饮明细：餐次 / 品类 / 菜名 ═══

    @Query("SELECT mealType, SUM(amount) as total, COUNT(*) as itemCount FROM transactions WHERE type = 'expense' AND categoryId = :categoryId AND transactionTime BETWEEN :startTime AND :endTime GROUP BY mealType ORDER BY total DESC")
    fun getDiningByMeal(categoryId: Long, startTime: Long, endTime: Long): Flow<List<MealTotal>>

    @Query("SELECT foodCategory, SUM(amount) as total, COUNT(*) as itemCount FROM transactions WHERE type = 'expense' AND categoryId = :categoryId AND foodCategory IS NOT NULL AND foodCategory != '' AND transactionTime BETWEEN :startTime AND :endTime GROUP BY foodCategory ORDER BY total DESC")
    fun getDiningByFoodCategory(categoryId: Long, startTime: Long, endTime: Long): Flow<List<FoodCategoryTotal>>

    @Query("SELECT foodName, SUM(amount) as total, COUNT(*) as itemCount FROM transactions WHERE type = 'expense' AND categoryId = :categoryId AND foodName IS NOT NULL AND foodName != '' AND transactionTime BETWEEN :startTime AND :endTime GROUP BY foodName ORDER BY itemCount DESC, total DESC LIMIT 5")
    fun getTopDiningFoods(categoryId: Long, startTime: Long, endTime: Long): Flow<List<FoodNameTotal>>

    @Query("SELECT * FROM transactions WHERE merchant LIKE '%' || :keyword || '%' OR note LIKE '%' || :keyword || '%' OR foodName LIKE '%' || :keyword || '%' ORDER BY transactionTime DESC")
    fun search(keyword: String): Flow<List<Transaction>>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun getCount(): Int

    @Query("SELECT MIN(transactionTime) FROM transactions")
    suspend fun getFirstTransactionTime(): Long?
}

data class CategoryTotal(
    val categoryId: Long?,
    val total: Double
)

data class MealTotal(
    val mealType: String?,
    val total: Double,
    val itemCount: Int
)

data class FoodCategoryTotal(
    val foodCategory: String?,
    val total: Double,
    val itemCount: Int
)

data class FoodNameTotal(
    val foodName: String?,
    val total: Double,
    val itemCount: Int
)
