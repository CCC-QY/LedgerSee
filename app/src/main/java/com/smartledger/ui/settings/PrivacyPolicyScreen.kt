package com.smartledger.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartledger.ui.theme.SmartLedgerColors

@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize().background(SmartLedgerColors.bg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = SmartLedgerColors.fg)
                }
                Text(
                    text = "隐私政策",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = SmartLedgerColors.fg
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                // 更新日期
                Text(
                    text = "更新日期：2026年10月22日",
                    style = MaterialTheme.typography.bodySmall,
                    color = SmartLedgerColors.fgSecondary
                )
                Text(
                    text = "生效日期：2026年10月22日",
                    style = MaterialTheme.typography.bodySmall,
                    color = SmartLedgerColors.fgSecondary
                )

                Spacer(modifier = Modifier.height(20.dp))

                PolicySection("一、引言") {
                    append("SmartLedger（以下简称'我们'或'本应用'）是一款个人记账应用，致力于帮助用户轻松记录和管理日常收支。我们深知个人信息对您的重要性，将严格遵守相关法律法规，采取相应的安全保护措施来保护您的个人信息。请您在使用本应用前，仔细阅读并充分理解本政策。")
                }

                PolicySection("二、信息收集与使用") {
                    appendLine("本应用在运行过程中，可能涉及以下信息的收集和使用：")
                    appendLine()
                    appendLine("1. 通知访问权限：本应用通过 Android 系统的 NotificationListenerService 监听支付类应用（如微信、支付宝、云闪付、银行App等）的通知消息，自动识别交易金额、商户名称和支付方式。此功能仅用于自动记账，不会上传或存储您的通知原文。")
                    appendLine()
                    appendLine("2. 悬浮窗权限：用于在检测到支付行为时弹出确认窗口，方便用户快速确认记账。")
                    appendLine()
                    appendLine("3. 存储权限：用于数据备份和导出功能，将您的记账数据以 CSV 格式保存到设备本地存储（Download/SmartLedger/ 目录）。")
                    appendLine()
                    appendLine("4. 电池优化豁免：为确保后台监听服务的持续运行，本应用可能请求关闭电池优化。")
                    appendLine()
                    appendLine("5. AI 财务复盘（可选）：仅在您主动开启并明确授权后，本应用会将月度收支、分类汇总、商户名、大额交易和预算进度等聚合统计发送给 DeepSeek API，用于生成分析。不会发送通知原文、短信内容、银行卡号或备注。")
                    appendLine()
                    appendLine("6. 截图记账（可选）：您主动选择的账单截图仅在本机使用 ML Kit 做文字识别，不上传。识别结果由您确认后才会保存为账单。")
                }

                PolicySection("三、信息存储与安全") {
                    appendLine("1. 本地存储：默认情况下，您的记账数据存储在设备本地数据库中。只有在您主动开启 AI 财务复盘并明确授权后，本应用才会将聚合后的统计摘要发送给 DeepSeek API，用于生成分析。")
                    appendLine()
                    appendLine("2. 数据备份：备份文件保存在您设备的 Download/SmartLedger/ 目录下，由您自行管理。")
                    appendLine()
                    appendLine("3. 数据安全：我们采用行业通用的安全措施保护您存储在设备上的数据。AI 功能的 API Key 使用 Android Keystore 加密保存在本机，不写入日志或 CSV。")
                }

                PolicySection("四、信息共享与披露") {
                    appendLine("除您明确授权的 AI 服务外，我们不会将您的个人信息共享、转让或披露给任何第三方，但以下情况除外：")
                    appendLine()
                    appendLine("1. 您主动开启 AI 财务复盘，并同意将聚合统计发送给 DeepSeek API；")
                    appendLine("2. 根据适用的法律法规、法律程序或政府机关的强制性要求；")
                    appendLine("3. 为维护本应用的合法权益所合理必需的情况。")
                }

                PolicySection("五、第三方服务") {
                    append("本应用不集成广告 SDK、用户画像或数据分析 SDK。可选的 AI 财务复盘会调用 DeepSeek API（深度求索）生成分析，发送内容仅限聚合统计，不包含通知原文、短信、卡号或备注。DeepSeek 对相关数据的处理适用其自身的隐私政策。您可以随时在 AI 页面清除 API Key、撤回授权并停止使用该功能。")
                }

                PolicySection("六、您的权利") {
                    appendLine("您对您的个人信息享有以下权利：")
                    appendLine()
                    appendLine("1. 查看权：您可以随时在本应用中查看您的所有记账数据。")
                    appendLine("2. 删除权：您可以删除单条记录、分类或清空所有数据。")
                    appendLine("3. 导出权：您可以随时将数据导出为 CSV 格式文件。")
                    appendLine("4. 备份与恢复：您可以通过备份功能保存数据，并在需要时恢复。")
                    appendLine("5. 权限管理：您可以在系统设置中随时关闭本应用的各项权限。")
                    appendLine("6. AI 数据控制：您可以随时在 AI 财务复盘页面清除 API Key、撤回授权并停止使用；已生成的复盘报告保存在本机，可通过清除应用数据删除。")
                }

                PolicySection("七、未成年人保护") {
                    append("我们非常重视对未成年人个人信息的保护。如果您是18周岁以下的未成年人，请在您的监护人的陪同下仔细阅读本政策，并在征得您的监护人的同意后使用本应用。")
                }

                PolicySection("八、政策更新") {
                    append("我们可能会适时修订本政策的条款，该等修订构成本政策的一部分。如修订造成您在本政策下权利的实质减少，我们将在修订生效前通过应用内通知的方式通知您。")
                }

                PolicySection("九、联系我们") {
                    append("如您对本政策有任何疑问、意见或建议，您可以通过以下方式与我们联系：\n\n邮箱：joah45@qq.com\n\n我们将在15个工作日内回复您的请求。")
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun PolicySection(title: String, contentBuilder: StringBuilder.() -> Unit) {
    val content = remember(title) {
        StringBuilder().apply(contentBuilder).toString()
    }

    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = SmartLedgerColors.fg,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
    Text(
        text = content,
        style = MaterialTheme.typography.bodyMedium,
        color = SmartLedgerColors.fgSecondary,
        lineHeight = 22.sp
    )
}
