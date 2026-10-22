package com.smartledger.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartledger.ai.QuipPersona
import com.smartledger.data.db.entity.QuipMessage
import com.smartledger.ui.theme.SmartLedgerColors

/**
 * 「碎碎」聊天页。
 *
 * 与通知的分工：通知是它主动开口，这里是用户主动说话。
 * 没配 Key / 未授权时不假装能聊，直接给配置入口。
 */
@Composable
fun ChatScreen(
    onOpenAiSettings: () -> Unit,
    /** 作为底部 tab 使用时不需要返回箭头；传 null 即隐藏 */
    onBack: (() -> Unit)? = null,
    viewModel: ChatViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    var showClearConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.messages.size, state.sending) {
        val target = state.messages.size + if (state.sending) 1 else 0
        if (target > 0) {
            runCatching { listState.animateScrollToItem(target - 1) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SmartLedgerColors.bg)
            // 键盘高度挂在整个页面上：窗口不会随键盘缩放（未用 adjustResize），
            // 只在输入框上补 padding 会把整页顶出屏幕，顶栏也跟着消失
            .imePadding()
    ) {
        // ═══ 顶栏 ═══
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = if (onBack == null) 20.dp else 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = SmartLedgerColors.fg)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    QuipPersona.NAME,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SmartLedgerColors.fg
                )
                Text(
                    "住在账本里的朋友",
                    fontSize = 11.sp,
                    color = SmartLedgerColors.fgSecondary
                )
            }
            if (state.messages.isNotEmpty()) {
                IconButton(onClick = { showClearConfirm = true }) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = "清空对话",
                        tint = SmartLedgerColors.fgSecondary
                    )
                }
            }
        }

        // ═══ 消息区 ═══
        Box(modifier = Modifier.weight(1f)) {
            if (state.messages.isEmpty()) {
                EmptyChat(
                    ready = state.ready && !state.checking,
                    checking = state.checking,
                    onOpenAiSettings = onOpenAiSettings,
                    onPick = viewModel::onInputChange
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp,
                        vertical = 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        MessageBubble(message)
                    }
                    if (state.sending) {
                        item(key = "typing") { TypingBubble() }
                    }
                }
            }
        }

        // ═══ 错误条 ═══
        state.error?.let { error ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    error,
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    color = SmartLedgerColors.expense
                )
                TextButton(onClick = viewModel::dismissError) {
                    Text("知道了", fontSize = 12.sp, color = SmartLedgerColors.fgSecondary)
                }
            }
        }

        // ═══ 输入区 ═══
        if (state.ready) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = viewModel::onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("跟碎碎说点什么…", fontSize = 14.sp) },
                    maxLines = 4,
                    shape = RoundedCornerShape(22.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { viewModel.send() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SmartLedgerColors.accent,
                        unfocusedBorderColor = SmartLedgerColors.border,
                        cursorColor = SmartLedgerColors.accent
                    )
                )
                Spacer(modifier = Modifier.size(8.dp))
                val canSend = state.input.isNotBlank() && !state.sending
                IconButton(
                    onClick = { viewModel.send() },
                    enabled = canSend,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSend) SmartLedgerColors.accent else SmartLedgerColors.surfaceHover
                        )
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "发送",
                        tint = if (canSend) androidx.compose.ui.graphics.Color.White
                        else SmartLedgerColors.fgSecondary
                    )
                }
            }
        } else {
            // 没配 Key 时不留一个假输入框
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "配置 DeepSeek API Key 后就能聊了",
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    color = SmartLedgerColors.fgSecondary
                )
                TextButton(onClick = onOpenAiSettings) {
                    Text("去配置", fontSize = 13.sp, color = SmartLedgerColors.accent)
                }
            }
        }
    }

    if (showClearConfirm) {
        com.smartledger.ui.components.SmartLedgerDialog(
            onDismissRequest = { showClearConfirm = false },
            title = "清空对话？",
            text = "只删聊天记录，账单不受影响。",
            confirmText = "清空",
            onConfirm = {
                showClearConfirm = false
                viewModel.clearHistory()
            }
        )
    }
}

@Composable
private fun MessageBubble(message: QuipMessage) {
    val fromUser = message.role == QuipMessage.ROLE_USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (fromUser) SmartLedgerColors.accent else SmartLedgerColors.surface,
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (fromUser) 14.dp else 4.dp,
                bottomEnd = if (fromUser) 4.dp else 14.dp
            ),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = if (fromUser) androidx.compose.ui.graphics.Color.White
                else SmartLedgerColors.fg
            )
        }
    }
}

@Composable
private fun TypingBubble() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            color = SmartLedgerColors.surface,
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = SmartLedgerColors.fgSecondary
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("碎碎在想…", fontSize = 13.sp, color = SmartLedgerColors.fgSecondary)
            }
        }
    }
}

@Composable
private fun EmptyChat(
    ready: Boolean,
    checking: Boolean,
    onOpenAiSettings: () -> Unit,
    onPick: (String) -> Unit
) {
    val suggestions = listOf(
        "这个月花了多少",
        "最近哪笔最离谱",
        "我最近都在吃什么"
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        // 顶栏已经有名字了，这里不再重复一次「碎碎」
        Text(
            when {
                checking -> "正在看看你的账本…"
                ready -> "我在。账本翻过了，想聊哪笔？"
                else -> "我在这儿，但要先配个 DeepSeek Key 才能开口。"
            },
            fontSize = 16.sp,
            lineHeight = 24.sp,
            color = SmartLedgerColors.fg
        )
        if (ready) {
            Spacer(modifier = Modifier.height(20.dp))
            suggestions.forEach { text ->
                TextButton(
                    onClick = { onPick(text) },
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Text("· $text", fontSize = 13.sp, color = SmartLedgerColors.accent)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "对话会把账单摘要发给 DeepSeek，备注与通知原文不会。",
                fontSize = 11.sp,
                lineHeight = 17.sp,
                color = SmartLedgerColors.fgSecondary
            )
        } else if (!checking) {
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onOpenAiSettings) {
                Text("去配置", fontSize = 13.sp, color = SmartLedgerColors.accent)
            }
        }
    }
}
