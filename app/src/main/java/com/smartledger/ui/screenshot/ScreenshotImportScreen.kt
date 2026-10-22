package com.smartledger.ui.screenshot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartledger.data.db.entity.Category
import com.smartledger.ui.components.PaymentChannelPicker
import com.smartledger.ui.theme.SmartLedgerColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ScreenshotImportScreen(
    onBack: () -> Unit = {},
    onSaved: () -> Unit = {},
    viewModel: ScreenshotImportViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.onImagePicked(uri)
    }

    fun openPicker() {
        picker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    Box(modifier = Modifier.fillMaxSize().background(SmartLedgerColors.bg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Outlined.ArrowBack,
                        contentDescription = "返回",
                        tint = SmartLedgerColors.fg
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "截图记账",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = SmartLedgerColors.fg
                    )
                    Text(
                        "本地 OCR · 保存前请核对",
                        style = MaterialTheme.typography.labelMedium,
                        color = SmartLedgerColors.fgSecondary
                    )
                }
            }

            if (state.imageUri == null) {
                EmptyScreenshotState(onPick = ::openPicker)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        ScreenshotPreviewCard(
                            uri = state.imageUri!!,
                            isRecognizing = state.isRecognizing,
                            onPickAgain = ::openPicker
                        )
                    }

                    state.error?.let { error ->
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = SmartLedgerColors.expenseDim
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        Icons.Outlined.Info,
                                        contentDescription = null,
                                        tint = SmartLedgerColors.expense,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        error,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SmartLedgerColors.expense,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }

                    item {
                        FormCard(
                            state = state,
                            onAmount = viewModel::setAmount,
                            onMerchant = viewModel::setMerchant,
                            onType = viewModel::setType,
                            onCategory = viewModel::setCategory,
                            onPaymentMethod = viewModel::setPaymentMethod,
                            onNote = viewModel::setNote,
                            onUseCurrentTime = viewModel::useCurrentTime
                        )
                    }

                    item {
                        SaveButton(
                            isSaving = state.isSaving,
                            enabled = !state.isRecognizing,
                            onSave = viewModel::save
                        )
                    }

                    item {
                        OcrTextCard(text = state.ocrText)
                    }

                    item {
                        Text(
                            "识别结果可能不准确，请以截图和实际账单为准。截图只在本机处理，不上传。",
                            style = MaterialTheme.typography.bodySmall,
                            color = SmartLedgerColors.fgSecondary,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(top = 2.dp, bottom = 24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyScreenshotState(onPick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(SmartLedgerColors.accentDim, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.AddPhotoAlternate,
                contentDescription = null,
                tint = SmartLedgerColors.accent,
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            "从账单截图记一笔",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = SmartLedgerColors.fg
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "支持微信、支付宝、云闪付、银行 App 的账单截图。\n识别后可以修改金额、商户、分类，再保存。",
            style = MaterialTheme.typography.bodyMedium,
            color = SmartLedgerColors.fgSecondary,
            lineHeight = 22.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onPick,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.accent)
        ) {
            Icon(
                Icons.Outlined.AddPhotoAlternate,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("选择账单截图", fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            "截图只在本机做 OCR，不会上传。",
            style = MaterialTheme.typography.bodySmall,
            color = SmartLedgerColors.fgSecondary
        )
        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
private fun ScreenshotPreviewCard(
    uri: Uri,
    isRecognizing: Boolean,
    onPickAgain: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SmartLedgerColors.surfaceHover),
                contentAlignment = Alignment.Center
            ) {
                ScreenshotImage(uri = uri)
                if (isRecognizing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "正在识别…",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "本地识别，不上传截图",
                    style = MaterialTheme.typography.bodySmall,
                    color = SmartLedgerColors.fgSecondary,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onPickAgain, enabled = !isRecognizing) {
                    Text("重新选择", color = SmartLedgerColors.accent)
                }
            }
        }
    }
}

@Composable
private fun ScreenshotImage(uri: Uri) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            decodeSampledBitmap(context, uri)
        }
    }
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = "账单截图",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    } else {
        CircularProgressIndicator(color = SmartLedgerColors.accent)
    }
}

@Composable
private fun FormCard(
    state: ScreenshotUiState,
    onAmount: (String) -> Unit,
    onMerchant: (String) -> Unit,
    onType: (String) -> Unit,
    onCategory: (Category) -> Unit,
    onPaymentMethod: (String) -> Unit,
    onNote: (String) -> Unit,
    onUseCurrentTime: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.surface)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "核对账单信息",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = SmartLedgerColors.fg
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.type == "expense",
                    onClick = { onType("expense") },
                    label = { Text("支出") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SmartLedgerColors.expenseDim,
                        selectedLabelColor = SmartLedgerColors.expense,
                        containerColor = SmartLedgerColors.surfaceHover,
                        labelColor = SmartLedgerColors.fgSecondary
                    )
                )
                FilterChip(
                    selected = state.type == "income",
                    onClick = { onType("income") },
                    label = { Text("收入") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SmartLedgerColors.incomeDim,
                        selectedLabelColor = SmartLedgerColors.income,
                        containerColor = SmartLedgerColors.surfaceHover,
                        labelColor = SmartLedgerColors.fgSecondary
                    )
                )
            }

            OutlinedTextField(
                value = state.amountText,
                onValueChange = onAmount,
                label = { Text("金额") },
                prefix = { Text("¥") },
                placeholder = { Text("0.00") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = textFieldColors()
            )

            OutlinedTextField(
                value = state.merchant,
                onValueChange = onMerchant,
                label = { Text("商户（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = textFieldColors()
            )

            Text(
                "分类",
                style = MaterialTheme.typography.labelMedium,
                color = SmartLedgerColors.fgSecondary
            )
            CategoryChips(
                categories = state.categoriesForType,
                selectedId = state.categoryId,
                onSelect = onCategory
            )

            PaymentChannelPicker(
                selected = state.paymentMethod,
                onSelected = onPaymentMethod,
                label = "支付渠道"
            )

            OutlinedTextField(
                value = state.note,
                onValueChange = onNote,
                label = { Text("备注（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = textFieldColors()
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "账单时间：${state.timeLabel.ifBlank { "当前时间" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SmartLedgerColors.fgSecondary,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onUseCurrentTime,
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text("改为现在", color = SmartLedgerColors.accent)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryChips(
    categories: List<Category>,
    selectedId: Long?,
    onSelect: (Category) -> Unit
) {
    if (categories.isEmpty()) {
        Text(
            "暂无可用分类",
            style = MaterialTheme.typography.bodySmall,
            color = SmartLedgerColors.fgSecondary
        )
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        categories.forEach { category ->
            FilterChip(
                selected = selectedId == category.id,
                onClick = { onSelect(category) },
                label = { Text(category.name, fontSize = 12.sp) },
                shape = RoundedCornerShape(8.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SmartLedgerColors.accent,
                    selectedLabelColor = Color.White,
                    containerColor = SmartLedgerColors.surfaceHover,
                    labelColor = SmartLedgerColors.fgSecondary
                )
            )
        }
    }
}

@Composable
private fun SaveButton(
    isSaving: Boolean,
    enabled: Boolean,
    onSave: () -> Unit
) {
    Button(
        onClick = onSave,
        enabled = enabled && !isSaving,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = SmartLedgerColors.accent,
            disabledContainerColor = SmartLedgerColors.accentDim,
            disabledContentColor = SmartLedgerColors.accent
        )
    ) {
        if (isSaving) {
            CircularProgressIndicator(
                modifier = Modifier.size(17.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(9.dp))
            Text("保存中…", fontWeight = FontWeight.SemiBold)
        } else {
            Text("保存到账单", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun OcrTextCard(text: String) {
    var expanded by remember(text) { mutableStateOf(false) }
    Column {
        TextButton(
            onClick = { expanded = !expanded },
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            Text(
                if (expanded) "收起识别原文" else "查看识别原文",
                color = SmartLedgerColors.fgSecondary
            )
        }
        if (expanded) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.surface)
            ) {
                SelectionContainer {
                    Text(
                        text.ifBlank { "未识别到文字" },
                        style = MaterialTheme.typography.bodySmall,
                        color = SmartLedgerColors.fgSecondary,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = SmartLedgerColors.accent,
    focusedLabelColor = SmartLedgerColors.accent,
    cursorColor = SmartLedgerColors.accent,
    focusedTextColor = SmartLedgerColors.fg,
    unfocusedTextColor = SmartLedgerColors.fg,
    unfocusedBorderColor = SmartLedgerColors.border
)

private fun decodeSampledBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val sample = maxOf(
            1,
            maxOf(bounds.outWidth / 1080, bounds.outHeight / 2000)
        )
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    } catch (_: Exception) {
        null
    }
}
