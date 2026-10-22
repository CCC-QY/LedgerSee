package com.smartledger.ui.ai

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartledger.ai.AiTone
import com.smartledger.data.db.entity.AiReport
import com.smartledger.ui.theme.SmartLedgerColors
import com.smartledger.util.DateUtil
import kotlinx.coroutines.delay
import java.time.YearMonth

@Composable
fun AiScreen(
    onBack: () -> Unit = {},
    viewModel: AiViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.notice) {
        if (state.notice != null) {
            delay(2600)
            viewModel.dismissNotice()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(SmartLedgerColors.bg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            AiTopBar(
                apiKeyConfigured = state.apiKeyConfigured,
                onBack = onBack,
                onSettings = viewModel::openSetup
            )

            if (!state.credentialsLoaded) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = SmartLedgerColors.accent)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        MonthAndToneCard(
                            month = state.month,
                            tone = state.tone,
                            onPrevious = viewModel::previousMonth,
                            onNext = viewModel::nextMonth,
                            onTone = viewModel::selectTone
                        )
                    }

                    state.notice?.let { notice ->
                        item {
                            Text(
                                notice,
                                style = MaterialTheme.typography.bodySmall,
                                color = SmartLedgerColors.income
                            )
                        }
                    }

                    state.error?.let { error ->
                        item {
                            ErrorCard(
                                message = error,
                                onDismiss = viewModel::dismissError,
                                onSetup = viewModel::openSetup
                            )
                        }
                    }

                    if (state.showSetup || !state.apiKeyConfigured || !state.consentGranted) {
                        item {
                            SetupCard(
                                state = state,
                                onSaveKey = viewModel::saveApiKey,
                                onGrantConsent = viewModel::grantConsent,
                                onCancel = viewModel::closeSetup,
                                onClearSetupError = viewModel::clearSetupError
                            )
                        }
                    } else {
                        item {
                            ConnectionCard(
                                onChange = viewModel::openSetup,
                                onClear = { showClearDialog = true }
                            )
                        }
                    }

                    item {
                        GenerateButton(
                            enabled = state.apiKeyConfigured && state.consentGranted,
                            loading = state.isLoading,
                            hasReport = state.report != null,
                            onGenerate = viewModel::generate
                        )
                    }

                    state.report?.let { report ->
                        item {
                            ReportCard(
                                report = report,
                                tone = state.tone,
                                month = state.month
                            )
                        }
                    }

                    item {
                        Text(
                            "AI 生成内容可能有误，请以账单数据为准。不会发送通知原文、短信或卡号。",
                            style = MaterialTheme.typography.bodySmall,
                            color = SmartLedgerColors.fgSecondary,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                        )
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清除 API Key？") },
            text = { Text("清除后需要重新输入 DeepSeek API Key 并确认授权。已生成的本地复盘会保留。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        viewModel.clearApiKey()
                    }
                ) {
                    Text("清除", color = SmartLedgerColors.expense)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消", color = SmartLedgerColors.fgSecondary)
                }
            },
            containerColor = SmartLedgerColors.surface,
            titleContentColor = SmartLedgerColors.fg,
            textContentColor = SmartLedgerColors.fgSecondary
        )
    }
}

@Composable
private fun AiTopBar(
    apiKeyConfigured: Boolean,
    onBack: () -> Unit,
    onSettings: () -> Unit
) {
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
                "AI 财务复盘",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = SmartLedgerColors.fg
            )
            Text(
                "DeepSeek · 月度分析",
                style = MaterialTheme.typography.labelMedium,
                color = SmartLedgerColors.fgSecondary
            )
        }
        if (apiKeyConfigured) {
            IconButton(onClick = onSettings) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = "AI 设置",
                    tint = SmartLedgerColors.fgSecondary
                )
            }
        }
    }
}

@Composable
private fun MonthAndToneCard(
    month: String,
    tone: AiTone,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTone: (AiTone) -> Unit
) {
    val currentMonth = YearMonth.now()
    val target = runCatching { YearMonth.parse(month) }.getOrDefault(currentMonth)
    val canGoNext = target < currentMonth

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.surface)
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious) {
                    Icon(
                        Icons.Outlined.ChevronLeft,
                        contentDescription = "上个月",
                        tint = SmartLedgerColors.fg
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "${target.year}年${target.monthValue}月",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = SmartLedgerColors.fg
                    )
                    Text(
                        if (target == currentMonth) "本月 · 截至今日" else "历史月份",
                        style = MaterialTheme.typography.bodySmall,
                        color = SmartLedgerColors.fgSecondary
                    )
                }
                IconButton(onClick = onNext, enabled = canGoNext) {
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = "下个月",
                        tint = if (canGoNext) SmartLedgerColors.fg else SmartLedgerColors.border
                    )
                }
            }

            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AiTone.entries.forEach { item ->
                    FilterChip(
                        selected = tone == item,
                        onClick = { onTone(item) },
                        label = { Text(item.label, fontWeight = FontWeight.Medium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SmartLedgerColors.accentDim,
                            selectedLabelColor = SmartLedgerColors.accent,
                            containerColor = SmartLedgerColors.surfaceHover,
                            labelColor = SmartLedgerColors.fgSecondary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
            Text(
                if (tone == AiTone.PROFESSIONAL) "结构化、数据先行，适合复盘" else "轻松、口语化，像朋友提醒",
                style = MaterialTheme.typography.bodySmall,
                color = SmartLedgerColors.fgSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun SetupCard(
    state: AiUiState,
    onSaveKey: (String, Boolean) -> Unit,
    onGrantConsent: () -> Unit,
    onCancel: () -> Unit,
    onClearSetupError: () -> Unit
) {
    val context = LocalContext.current
    var apiKey by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(state.consentGranted) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(state.consentGranted) {
        consent = state.consentGranted
    }
    LaunchedEffect(state.apiKeyConfigured, state.showSetup) {
        if (state.apiKeyConfigured && !state.showSetup) apiKey = ""
    }

    val consentOnly = state.apiKeyConfigured && !state.consentGranted
    val needsKey = !state.apiKeyConfigured || (state.showSetup && !consentOnly)
    val canSubmit = if (consentOnly) {
        consent
    } else {
        consent && apiKey.trim().length >= 20
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.surface)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(SmartLedgerColors.accentDim, RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = SmartLedgerColors.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        when {
                            consentOnly -> "确认 DeepSeek 授权"
                            state.apiKeyConfigured -> "更换 DeepSeek API Key"
                            else -> "连接 DeepSeek"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = SmartLedgerColors.fg
                    )
                    Text(
                        "只分析统计结果，不发送原始通知",
                        style = MaterialTheme.typography.bodySmall,
                        color = SmartLedgerColors.fgSecondary
                    )
                }
            }

            Text(
                "发送内容：月度收支、分类汇总、商户名、大额交易、预算进度。\n" +
                    "不会发送：通知原文、短信、卡号、备注。",
                style = MaterialTheme.typography.bodySmall,
                color = SmartLedgerColors.fgSecondary,
                lineHeight = 19.sp
            )

            if (needsKey) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        onClearSetupError()
                    },
                    label = { Text("DeepSeek API Key") },
                    placeholder = { Text("sk-...") },
                    singleLine = true,
                    visualTransformation = if (visible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (visible) "隐藏" else "显示",
                                tint = SmartLedgerColors.fgSecondary
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SmartLedgerColors.accent,
                        focusedLabelColor = SmartLedgerColors.accent,
                        cursorColor = SmartLedgerColors.accent,
                        focusedTextColor = SmartLedgerColors.fg,
                        unfocusedTextColor = SmartLedgerColors.fg
                    )
                )

                TextButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://platform.deepseek.com/api_keys")
                                )
                            )
                        }
                    },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text("去 DeepSeek 获取 API Key", color = SmartLedgerColors.accent)
                }
            } else {
                Text(
                    "API Key 已保存在本机。重新确认授权后即可继续使用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = SmartLedgerColors.fgSecondary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Checkbox(
                    checked = consent,
                    onCheckedChange = { consent = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = SmartLedgerColors.accent,
                        uncheckedColor = SmartLedgerColors.fgSecondary
                    )
                )
                Text(
                    "我已了解并同意将上述聚合账单数据发送给 DeepSeek，用于生成分析。",
                    style = MaterialTheme.typography.bodySmall,
                    color = SmartLedgerColors.fgSecondary,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 12.dp, end = 4.dp)
                )
            }

            state.setupError?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = SmartLedgerColors.expense
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (consentOnly) {
                            onGrantConsent()
                        } else {
                            onSaveKey(apiKey, consent)
                        }
                    },
                    enabled = canSubmit,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(11.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SmartLedgerColors.accent
                    )
                ) {
                    Text(
                        when {
                            consentOnly -> "同意并启用"
                            state.apiKeyConfigured -> "保存新 Key"
                            else -> "保存并启用"
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (state.apiKeyConfigured && state.consentGranted && state.showSetup) {
                    TextButton(onClick = onCancel) {
                        Text("取消", color = SmartLedgerColors.fgSecondary)
                    }
                }
            }

            Text(
                "API Key 仅保存在本机，使用 Android Keystore 加密，不写入日志或 CSV。",
                style = MaterialTheme.typography.labelSmall,
                color = SmartLedgerColors.fgSecondary,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun ConnectionCard(
    onChange: () -> Unit,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.surface)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                tint = SmartLedgerColors.income,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "DeepSeek 已启用",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = SmartLedgerColors.fg
                )
                Text(
                    "DeepSeek · Key 仅存本机",
                    style = MaterialTheme.typography.bodySmall,
                    color = SmartLedgerColors.fgSecondary
                )
            }
            TextButton(onClick = onChange) {
                Text("更换", color = SmartLedgerColors.accent)
            }
            TextButton(onClick = onClear) {
                Text("清除", color = SmartLedgerColors.fgSecondary)
            }
        }
    }
}

@Composable
private fun GenerateButton(
    enabled: Boolean,
    loading: Boolean,
    hasReport: Boolean,
    onGenerate: () -> Unit
) {
    Button(
        onClick = onGenerate,
        enabled = enabled && !loading,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = SmartLedgerColors.accent,
            disabledContainerColor = SmartLedgerColors.accentDim,
            disabledContentColor = SmartLedgerColors.accent
        )
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(17.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(9.dp))
            Text("正在分析…", fontWeight = FontWeight.SemiBold)
        } else {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (hasReport) "重新生成 AI 复盘" else "生成 AI 复盘",
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onDismiss: () -> Unit,
    onSetup: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.expenseDim)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = SmartLedgerColors.expense,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = SmartLedgerColors.expense,
                    lineHeight = 18.sp,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (message.contains("API Key") || message.contains("Key")) {
                    TextButton(onClick = onSetup) {
                        Text("去配置", color = SmartLedgerColors.expense)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("关闭", color = SmartLedgerColors.fgSecondary)
                }
            }
        }
    }
}

@Composable
private fun ReportCard(
    report: AiReport,
    tone: AiTone,
    month: String
) {
    var showFacts by remember(report.id) { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SmartLedgerColors.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${monthLabel(month)}复盘",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = SmartLedgerColors.fg,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    tone.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = SmartLedgerColors.accent,
                    modifier = Modifier
                        .background(SmartLedgerColors.accentDim, RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "生成于 ${DateUtil.formatDateTime(report.createdAt)} · ${report.model}",
                style = MaterialTheme.typography.bodySmall,
                color = SmartLedgerColors.fgSecondary
            )
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = SmartLedgerColors.border, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(14.dp))

            SelectionContainer {
                AiReportContent(report.content)
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = SmartLedgerColors.border, thickness = 0.5.dp)

            TextButton(
                onClick = { showFacts = !showFacts },
                contentPadding = PaddingValues(horizontal = 0.dp)
            ) {
                Text(
                    if (showFacts) "收起数据依据" else "查看本次使用的数据",
                    color = SmartLedgerColors.fgSecondary
                )
            }

            if (showFacts) {
                Text(
                    report.factsText,
                    style = MaterialTheme.typography.bodySmall,
                    color = SmartLedgerColors.fgSecondary,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun AiReportContent(content: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        content.split("\n").forEach { raw ->
            val line = raw
                .replace("**", "")
                .replace("`", "")
                .replace(Regex("^#+\\s*"), "")
                .trim()
            when {
                line.isBlank() -> Spacer(modifier = Modifier.height(6.dp))
                Regex("^[一二三四五六七八九十]+、").containsMatchIn(line) -> {
                    Text(
                        line,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = SmartLedgerColors.fg,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                    )
                }

                line.startsWith("- ") || line.startsWith("• ") -> {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            "•",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SmartLedgerColors.fgSecondary,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            line.removePrefix("- ").removePrefix("• ").trim(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = SmartLedgerColors.fg,
                            lineHeight = 23.sp
                        )
                    }
                }

                else -> Text(
                    line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SmartLedgerColors.fg,
                    lineHeight = 23.sp
                )
            }
        }
    }
}

private fun monthLabel(month: String): String =
    runCatching {
        val ym = YearMonth.parse(month)
        "${ym.year}年${ym.monthValue}月"
    }.getOrDefault(month)
