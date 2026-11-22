package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.MessageVersionEntity
import com.example.data.local.ModelHealthStatus
import com.example.domain.FailoverPrompt
import com.example.ui.theme.HealthAvailable
import com.example.ui.theme.HealthCooldown
import com.example.ui.theme.HealthUnavailable
import com.example.ui.theme.HealthUntested
import com.example.ui.theme.MonoFontFamily
import kotlinx.coroutines.delay

@Composable
fun ModelHealthBadge(
    status: ModelHealthStatus,
    cooldownUntilMs: Long = 0L,
    latencyMs: Long? = null,
    showLabel: Boolean = true,
) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(status, cooldownUntilMs) {
        if (status == ModelHealthStatus.COOLDOWN && cooldownUntilMs > System.currentTimeMillis()) {
            while (System.currentTimeMillis() < cooldownUntilMs) {
                nowMs = System.currentTimeMillis()
                delay(1000L)
            }
        }
    }

    val effectiveStatus = if (status == ModelHealthStatus.COOLDOWN && cooldownUntilMs <= nowMs) {
        ModelHealthStatus.UNTESTED
    } else {
        status
    }

    val (color, label) = when (effectiveStatus) {
        ModelHealthStatus.AVAILABLE -> HealthAvailable to (latencyMs?.let { "可用 ${it}ms" } ?: "可用")
        ModelHealthStatus.COOLDOWN -> {
            val sec = ((cooldownUntilMs - nowMs) / 1000L).coerceAtLeast(1L)
            HealthCooldown to "冷卻 ${sec}s"
        }
        ModelHealthStatus.UNAVAILABLE -> HealthUnavailable to "不可用"
        ModelHealthStatus.UNTESTED -> HealthUntested to "未測試"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color),
        )
        if (showLabel) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
        }
    }
}

@Composable
fun FailoverAlertDialog(
    prompt: FailoverPrompt,
    onConfirmSameModelSwitch: (FailoverPrompt.SameModelAvailable) -> Unit,
    onOpenModelPicker: (FailoverPrompt) -> Unit,
    onDismiss: () -> Unit,
) {
    when (prompt) {
        is FailoverPrompt.SameModelAvailable -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.SwapHoriz,
                        contentDescription = null,
                        tint = HealthCooldown,
                    )
                },
                title = { Text("偵測到 429 額度限制 · 已凍結進度") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "${prompt.failedProviderName} 的「${prompt.failedModelId}」已達速率／額度上限（冷卻約 ${prompt.retryAfterSeconds} 秒）。目前已生成的內容與對話進度已完整保留。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "發現可用同模型備援 🟢",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Text(
                                    text = "${prompt.fallbackProvider.name} · ${prompt.fallbackModel.modelId}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { onConfirmSameModelSwitch(prompt) },
                        modifier = Modifier.testTag("failover_one_tap_switch_button"),
                    ) {
                        Text("一鍵切換至備援 API")
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { onOpenModelPicker(prompt) }) {
                            Text("改選其他模型")
                        }
                        TextButton(onClick = onDismiss) {
                            Text("暫停")
                        }
                    }
                },
            )
        }

        is FailoverPrompt.NoSameModelFallback -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = HealthUnavailable,
                    )
                },
                title = { Text("額度耗盡 · 已暫停任務") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "${prompt.failedProviderName} 的「${prompt.failedModelId}」回傳 429／額度不足（預計冷卻 ${prompt.retryAfterSeconds} 秒），且目前無其他綠燈供應商提供相同模型。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "原因：${prompt.errorMessage.take(160)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "已保留目前產出的文字，您可開啟可用模型選單切換其他健康模型接續。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { onOpenModelPicker(prompt) },
                        modifier = Modifier.testTag("failover_open_picker_button"),
                    ) {
                        Text("開啟可用模型選單")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("先保留斷點")
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugInspectorSheet(
    version: MessageVersionEntity,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("請求快照與除錯檢視器", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${version.providerName} · ${version.modelId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = {
                        val dump = buildString {
                            appendLine("=== Metrics ===")
                            appendLine("TTFT: ${version.ttftMs}ms, TPS: ${version.tps}, Prompt: ${version.promptTokens}, Completion: ${version.completionTokens}")
                            appendLine("=== Params Snapshot ===")
                            appendLine(version.paramSnapshotJson)
                            appendLine("=== Request ===")
                            appendLine(version.debugRequestJson)
                            appendLine("=== Response Headers ===")
                            appendLine(version.debugResponseHeadersJson)
                        }
                        clipboard.setText(AnnotatedString(dump))
                    },
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("複製全部")
                }
            }

            // Metrics strip
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MetricItem("TTFT", version.ttftMs?.let { "${it} ms" } ?: "-")
                    MetricItem("速度", version.tps?.let { "%.1f tok/s".format(it) } ?: "-")
                    MetricItem("輸入 Token", version.promptTokens?.toString() ?: "-")
                    MetricItem("輸出 Token", version.completionTokens?.toString() ?: "-")
                }
            }

            if (version.rateLimitInfoJson.isNotBlank()) {
                DebugCodeSection("剩餘額度標頭 (Rate Limit Headers)", version.rateLimitInfoJson)
            }
            DebugCodeSection(
                "生效參數快照 (未設定已省略)",
                version.paramSnapshotJson.ifBlank { "{}" },
            )
            DebugCodeSection(
                "完整請求 JSON (金鑰已自動遮罩)",
                version.debugRequestJson.ifBlank { "無紀錄" },
            )
            if (version.debugResponseHeadersJson.isNotBlank()) {
                DebugCodeSection("回應標頭 (Response Headers)", version.debugResponseHeadersJson)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
private fun DebugCodeSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        HorizontalDivider()
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = body,
                fontFamily = MonoFontFamily,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
            )
        }
    }
}
