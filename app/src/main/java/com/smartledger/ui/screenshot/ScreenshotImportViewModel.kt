package com.smartledger.ui.screenshot

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartledger.SmartLedgerApp
import com.smartledger.data.db.entity.Category
import com.smartledger.data.db.entity.Transaction
import com.smartledger.data.model.DiningDetails
import com.smartledger.data.model.MealType
import com.smartledger.screenshot.ScreenshotBillParser
import com.smartledger.screenshot.ScreenshotOcr
import com.smartledger.service.SmartCategorizer
import com.smartledger.util.CurrencyUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ScreenshotUiState(
    val imageUri: Uri? = null,
    val isRecognizing: Boolean = false,
    val isSaving: Boolean = false,
    val ocrText: String = "",
    val amountText: String = "",
    val merchant: String = "",
    val type: String = "expense",
    val paymentMethod: String = "微信",
    val categoryId: Long? = null,
    val note: String = "",
    val mealType: String? = null,
    val foodCategory: String? = null,
    val foodName: String = "",
    val categories: List<Category> = emptyList(),
    val transactionTime: Long = System.currentTimeMillis(),
    val timeLabel: String = "",
    val error: String? = null,
    val saved: Boolean = false
) {
    val categoriesForType: List<Category>
        get() = categories.filter { it.type == type }
}

class ScreenshotImportViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SmartLedgerApp
    private val _state = MutableStateFlow(ScreenshotUiState())
    val state: StateFlow<ScreenshotUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val categories = withContext(Dispatchers.IO) {
                app.database.categoryDao().getAllOnce()
            }
            _state.update { it.copy(categories = categories) }
        }
    }

    fun onImagePicked(uri: Uri) {
        _state.update {
            it.copy(
                imageUri = uri,
                isRecognizing = true,
                error = null,
                saved = false,
                ocrText = "",
                amountText = "",
                merchant = "",
                note = "",
                type = "expense",
                paymentMethod = "微信",
                categoryId = null,
                mealType = null,
                foodCategory = null,
                foodName = "",
                transactionTime = System.currentTimeMillis(),
                timeLabel = ""
            )
        }
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    ScreenshotOcr.recognize(app, uri)
                }
                if (text.isBlank()) {
                    _state.update {
                        it.copy(
                            isRecognizing = false,
                            error = "没有识别到文字，请换一张清晰截图，或手动填写。"
                        )
                    }
                    return@launch
                }

                val parsed = ScreenshotBillParser.parse(text)
                val categories = withContext(Dispatchers.IO) {
                    app.database.categoryDao().getAllOnce()
                }
                val categoryId = withContext(Dispatchers.IO) {
                    SmartCategorizer.categorize(
                        merchant = parsed.merchant,
                        paymentMethod = parsed.paymentMethod,
                        note = null,
                        categories = categories,
                        type = parsed.type
                    )
                }
                val transactionTime = parsed.transactionTime ?: System.currentTimeMillis()
                val categoryName = categories.find { it.id == categoryId }?.name
                val dining = DiningDetails.autoFor(
                    merchant = parsed.merchant,
                    note = parsed.foodName,
                    time = transactionTime,
                    isDining = DiningDetails.isDining(categoryName, parsed.type)
                )
                _state.update {
                    it.copy(
                        isRecognizing = false,
                        ocrText = text,
                        amountText = parsed.amount?.let { amount ->
                            CurrencyUtil.toEditableString(amount)
                        }.orEmpty(),
                        merchant = parsed.merchant.orEmpty(),
                        type = parsed.type,
                        paymentMethod = parsed.paymentMethod ?: it.paymentMethod,
                        categoryId = categoryId,
                        mealType = dining?.mealType,
                        foodCategory = dining?.foodCategory,
                        foodName = parsed.foodName.orEmpty(),
                        categories = categories,
                        transactionTime = transactionTime,
                        timeLabel = formatTime(transactionTime),
                        error = if (parsed.amount == null) {
                            "未识别到金额，请手动填写后再保存。"
                        } else {
                            null
                        }
                    )
                }
            } catch (_: Exception) {
                _state.update {
                    it.copy(
                        isRecognizing = false,
                        error = "截图识别失败，请手动填写。"
                    )
                }
            }
        }
    }

    fun setAmount(value: String) {
        if (value.isEmpty() || value.matches(Regex("^\\d{0,9}(\\.\\d{0,2})?$"))) {
            _state.update { it.copy(amountText = value, error = null) }
        }
    }

    fun setMerchant(value: String) {
        _state.update { it.copy(merchant = value.take(40)) }
    }

    fun setNote(value: String) {
        _state.update { it.copy(note = value.take(80)) }
    }

    fun setMealType(value: String) {
        _state.update { it.copy(mealType = value) }
    }

    fun setFoodCategory(value: String?) {
        _state.update { it.copy(foodCategory = value) }
    }

    fun setFoodName(value: String) {
        _state.update { it.copy(foodName = value.take(40)) }
    }

    fun setType(value: String) {
        _state.update { current ->
            if (current.type == value) return@update current
            val category = current.categories.find { it.id == current.categoryId }
            val categoryId = if (category?.type == value) {
                current.categoryId
            } else {
                current.categories.find { it.type == value && it.name == "其他" }?.id
            }
            val nextCategory = current.categories.find { it.id == categoryId }
            val dining = DiningDetails.isDining(nextCategory?.name, value)
            current.copy(
                type = value,
                categoryId = categoryId,
                mealType = if (dining) {
                    current.mealType ?: MealType.forTime(current.transactionTime).key
                } else null,
                foodCategory = if (dining) {
                    current.foodCategory
                        ?: DiningDetails.guessFoodCategory(current.merchant, current.foodName)?.key
                } else null,
                foodName = if (dining) current.foodName else ""
            )
        }
    }

    fun setCategory(category: Category) {
        _state.update { current ->
            val dining = DiningDetails.isDining(category.name, current.type)
            current.copy(
                categoryId = category.id,
                mealType = if (dining) {
                    current.mealType ?: MealType.forTime(current.transactionTime).key
                } else null,
                foodCategory = if (dining) {
                    current.foodCategory
                        ?: DiningDetails.guessFoodCategory(current.merchant, current.foodName)?.key
                } else null,
                foodName = if (dining) current.foodName else ""
            )
        }
    }

    fun setPaymentMethod(value: String) {
        _state.update { it.copy(paymentMethod = value) }
    }

    fun useCurrentTime() {
        val now = System.currentTimeMillis()
        _state.update { current ->
            val categoryName = current.categories.find { it.id == current.categoryId }?.name
            val dining = DiningDetails.isDining(categoryName, current.type)
            current.copy(
                transactionTime = now,
                timeLabel = formatTime(now),
                mealType = if (dining) MealType.forTime(now).key else current.mealType
            )
        }
    }

    fun save() {
        val current = _state.value
        if (current.isSaving) return
        val amount = current.amountText
            .replace(",", "")
            .replace("，", "")
            .toDoubleOrNull()
        if (amount == null || amount <= 0) {
            _state.update { it.copy(error = "请输入有效金额。") }
            return
        }
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                val categoryName = current.categories.find { it.id == current.categoryId }?.name
                val dining = DiningDetails.isDining(categoryName, current.type)
                withContext(Dispatchers.IO) {
                    app.transactionRepository.insert(
                        Transaction(
                            amount = amount,
                            type = current.type,
                            categoryId = current.categoryId,
                            merchant = current.merchant.trim().ifBlank { null },
                            paymentMethod = current.paymentMethod.trim().ifBlank { null },
                            note = current.note.trim().ifBlank { null },
                            mealType = if (dining) current.mealType else null,
                            foodCategory = if (dining) current.foodCategory else null,
                            foodName = if (dining) current.foodName.trim().ifBlank { null } else null,
                            source = "screenshot",
                            transactionTime = current.transactionTime
                        )
                    )
                }
                _state.update { it.copy(isSaving = false, saved = true) }
            } catch (_: Exception) {
                _state.update { it.copy(isSaving = false, error = "保存失败，请重试。") }
            }
        }
    }

    private fun formatTime(time: Long): String =
        Instant.ofEpochMilli(time)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}
