package com.smartledger.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.smartledger.data.db.entity.Category
import com.smartledger.data.model.DiningDetails
import com.smartledger.data.model.MealType
import com.smartledger.ui.components.DiningFields
import com.smartledger.ui.components.PaymentChannelPicker
import com.smartledger.ui.theme.SmartLedgerColors

@Composable
fun RecordScreen(
    onSaved: () -> Unit = {},
    onImportScreenshot: () -> Unit = {},
    viewModel: RecordViewModel = viewModel()
) {
    var transactionType by remember { mutableStateOf("expense") }
    var amountText by remember { mutableStateOf("0") }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var merchant by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("微信") }
    var mealType by remember {
        mutableStateOf(MealType.forTime(System.currentTimeMillis()).key)
    }
    var foodCategory by remember { mutableStateOf<String?>(null) }
    var foodName by remember { mutableStateOf("") }

    val categories by viewModel.getCategories(transactionType)
        .collectAsState(initial = emptyList())

    // 手动记账后要主动叫一次碎碎念：Compose 内部导航不会触发 Activity 的 onResume。
    // 用进程级作用域，避免页面弹栈时把还在等模型的请求一起取消。
    val appContext = LocalContext.current.applicationContext
    val appScope = (appContext as? com.smartledger.SmartLedgerApp)?.appScope

    LaunchedEffect(transactionType) {
        selectedCategory = null
        foodCategory = null
        foodName = ""
        mealType = MealType.forTime(System.currentTimeMillis()).key
    }

    val isDining = DiningDetails.isDining(selectedCategory?.name, transactionType)

    fun selectCategory(category: Category) {
        selectedCategory = category
        if (DiningDetails.isDining(category.name, transactionType)) {
            mealType = MealType.forTime(System.currentTimeMillis()).key
            foodCategory = DiningDetails.guessFoodCategory(merchant, note)?.key
        } else {
            foodCategory = null
            foodName = ""
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(SmartLedgerColors.bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // ═══ 截图导入入口 ═══
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onImportScreenshot) {
                    Icon(
                        Icons.Outlined.AddPhotoAlternate,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("从截图导入", color = SmartLedgerColors.accent)
                }
            }

            // ═══ 顶部：类型切换 ═══
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                TypeTab(
                    text = "支出",
                    selected = transactionType == "expense",
                    onClick = { transactionType = "expense" }
                )
                Spacer(modifier = Modifier.width(48.dp))
                TypeTab(
                    text = "收入",
                    selected = transactionType == "income",
                    onClick = { transactionType = "income" }
                )
            }

            // ═══ 金额显示 ═══
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "¥",
                        style = MaterialTheme.typography.titleLarge,
                        color = SmartLedgerColors.fgSecondary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Text(
                        text = amountText,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 44.sp,
                            letterSpacing = (-1).sp
                        ),
                        color = if (transactionType == "expense") SmartLedgerColors.expense else SmartLedgerColors.income
                    )
                }
            }

            // ═══ 商户名 ═══
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = null,
                    tint = SmartLedgerColors.fgSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { value ->
                        merchant = value
                        if (isDining && foodCategory == null) {
                            foodCategory = DiningDetails.guessFoodCategory(value, note)?.key
                        }
                    },
                    placeholder = {
                        Text(
                            "商户名称（如蒙牛、美团）…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SmartLedgerColors.fgSecondary
                        )
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = SmartLedgerColors.accent
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            }

            // ═══ 备注 + 日期行 ═══
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = null,
                    tint = SmartLedgerColors.fgSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = {
                        Text(
                            "添加备注…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SmartLedgerColors.fgSecondary
                        )
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = SmartLedgerColors.accent
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = SmartLedgerColors.fgSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1}月${java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH)}日",
                    style = MaterialTheme.typography.bodySmall,
                    color = SmartLedgerColors.fgSecondary
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = SmartLedgerColors.border,
                thickness = 0.5.dp
            )

            // ═══ 支付渠道 ═══
            PaymentChannelPicker(
                selected = paymentMethod,
                onSelected = { paymentMethod = it },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = SmartLedgerColors.border,
                thickness = 0.5.dp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ═══ 分类选择网格 ═══
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .heightIn(max = 280.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                userScrollEnabled = false
            ) {
                items(categories) { category ->
                    CategoryItem(
                        category = category,
                        selected = selectedCategory?.id == category.id,
                        onClick = { selectCategory(category) }
                    )
                }
            }

            if (isDining) {
                DiningFields(
                    mealType = mealType,
                    onMealTypeChange = { mealType = it },
                    foodName = foodName,
                    onFoodNameChange = { foodName = it },
                    foodCategory = foodCategory,
                    onFoodCategoryChange = { foodCategory = it },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ═══ 数字键盘 ═══
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "⌫")

                for (row in 0..3) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (col in 0..2) {
                            val key = keys[row * 3 + col]
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SmartLedgerColors.surfaceHover)
                                    .clickable { amountText = handleKeyPress(amountText, key) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (key == "⌫") {
                                    Icon(
                                        Icons.Outlined.Backspace,
                                        contentDescription = "删除",
                                        tint = SmartLedgerColors.fg,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    Text(
                                        text = key,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = SmartLedgerColors.fg
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // ═══ 记一笔按钮 ═══
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                TextButton(
                    onClick = {
                        val amount = amountText.toDoubleOrNull()
                        val channel = paymentMethod.trim().ifBlank { "其他" }
                        if (amount != null && amount > 0) {
                            viewModel.saveTransaction(
                                amount = amount,
                                type = transactionType,
                                categoryId = selectedCategory?.id,
                                merchant = merchant.ifBlank { null },
                                paymentMethod = channel,
                                note = note.ifBlank { null },
                                mealType = if (isDining) mealType else null,
                                foodCategory = if (isDining) foodCategory else null,
                                foodName = if (isDining) foodName.trim().ifBlank { null } else null,
                                onSuccess = {
                                    amountText = "0"
                                    selectedCategory = null
                                    merchant = ""
                                    note = ""
                                    foodCategory = null
                                    foodName = ""
                                    mealType = MealType.forTime(System.currentTimeMillis()).key
                                    // 保留上次渠道，连续记账更省事
                                    appScope?.launch {
                                        runCatching {
                                            com.smartledger.ai.QuipEngine.maybeSpeak(appContext)
                                        }
                                    }
                                    onSaved()
                                }
                            )
                        }
                    }
                ) {
                    Text(
                        text = "记一笔",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = SmartLedgerColors.accent
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════
// 类型切换标签
// ═══════════════════════════════════════════════════════

@Composable
private fun TypeTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) SmartLedgerColors.fg else SmartLedgerColors.fgSecondary
        )
        if (selected) {
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(2.dp)
                    .background(SmartLedgerColors.fg, RoundedCornerShape(1.dp))
            )
        }
    }
}

// ═══════════════════════════════════════════════════════
// 分类项 — 线条图标风格
// ═══════════════════════════════════════════════════════

@Composable
private fun CategoryItem(category: Category, selected: Boolean, onClick: () -> Unit) {
    val icon = getCategoryIcon(category.name)
    val iconColor = if (selected) SmartLedgerColors.accent else SmartLedgerColors.fgSecondary
    val bgColor = if (selected) SmartLedgerColors.accentDim else Color.Transparent

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(bgColor)
            .padding(vertical = 10.dp)
    ) {
        Icon(
            icon,
            contentDescription = category.name,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = category.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) SmartLedgerColors.fg else SmartLedgerColors.fgSecondary,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

// ═══════════════════════════════════════════════════════
// 分类图标映射
// ═══════════════════════════════════════════════════════

private fun getCategoryIcon(name: String): ImageVector {
    return when (name) {
        "餐饮" -> Icons.Outlined.Restaurant
        "交通" -> Icons.Outlined.DirectionsBus
        "购物" -> Icons.Outlined.ShoppingBag
        "娱乐" -> Icons.Outlined.OndemandVideo
        "居住" -> Icons.Outlined.Home
        "医疗" -> Icons.Outlined.FavoriteBorder
        "教育" -> Icons.Outlined.MenuBook
        "通讯" -> Icons.Outlined.Phone
        "日用" -> Icons.Outlined.ShoppingCart
        "工资" -> Icons.Outlined.AttachMoney
        "理财" -> Icons.Outlined.TrendingUp
        "红包" -> Icons.Outlined.CardGiftcard
        "转账" -> Icons.Outlined.SwapHoriz
        else -> Icons.Outlined.MoreHoriz
    }
}

// ═══════════════════════════════════════════════════════
// 按键处理
// ═══════════════════════════════════════════════════════

private fun handleKeyPress(current: String, key: String): String {
    return when (key) {
        "⌫" -> if (current.length <= 1) "0" else current.dropLast(1)
        "." -> if (current.contains(".")) current else "$current."
        else -> {
            if (current == "0") key
            else {
                val dotIndex = current.indexOf('.')
                if (dotIndex != -1 && current.length - dotIndex > 2) current else current + key
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// Preview
// ═══════════════════════════════════════════════════════

@androidx.compose.ui.tooling.preview.Preview(
    showBackground = true,
    widthDp = 393,
    heightDp = 852,
    name = "RecordScreen"
)
@Composable
private fun RecordScreenPreview() {
    com.smartledger.ui.theme.SmartLedgerTheme {
        RecordScreen()
    }
}
