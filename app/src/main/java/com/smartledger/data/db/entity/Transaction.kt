package com.smartledger.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("categoryId"), Index("transactionTime"), Index("notificationKey")]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val type: String,
    val categoryId: Long? = null,
    val merchant: String? = null,
    val paymentMethod: String? = null,
    val note: String? = null,
    /** 餐饮明细：餐次（早餐/午餐/下午茶/晚餐/夜宵），仅支出且分类为餐饮时有值 */
    val mealType: String? = null,
    /** 餐饮明细：粗粒度品类（面食/火锅/咖啡…），用于统计 */
    val foodCategory: String? = null,
    /** 餐饮明细：具体吃了什么（牛肉面…），只接受用户输入/OCR */
    val foodName: String? = null,
    val source: String,
    val notificationKey: String? = null,
    val transactionTime: Long,
    val createdAt: Long = System.currentTimeMillis()
)
