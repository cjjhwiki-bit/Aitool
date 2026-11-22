package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ModelEntity
import com.example.data.local.ModelHealthStatus
import com.example.data.local.ProviderEntity
import com.example.data.security.AppPreferences
import com.example.ui.theme.HealthAvailable
import com.example.ui.theme.HealthCooldown
import com.example.ui.theme.HealthUnavailable
import com.example.ui.theme.HealthUntested

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsOverviewScreen(
    providers: List<ProviderEntity>,
    models: List<ModelEntity>,
    keyStatusMap: Map<String, Boolean>,
    preferences: AppPreferences,
    onBack: () -> Unit,
    onOpenProvider: (String) -> Unit,
    onToggleProviderEnabled: (ProviderEntity, Boolean) -> Unit,
    onAddCustomProvider: (name: String, baseUrl: String) -> Unit,
    onToggleSilentFailover: (Boolean) -> Unit,
    onSetThemeMode: (String) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onOpenCanonicalMapping: () -> Unit,
) {
    BackHandler { onBack() }

    var showAddCustomDialog by remember { mutableStateOf(false) }

    val modelsByProvider = remember(models) { models.groupBy { it.providerId } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("供應商與工作站設定") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_btn")) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回對話")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "API 供應商總覽",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OutlinedButton(
                        onClick = { showAddCustomDialog = true },
                        modifier = Modifier.testTag("add_custom_provider_btn"),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("新增自訂供應商")
                    }
                }
            }

            items(providers, key = { it.id }) { provider ->
                val pModels = modelsByProvider[provider.id].orEmpty()
                val hasKey = keyStatusMap[provider.id] == true || provider.allowEmptyKey
                val greenCount = pModels.count { it.health() == ModelHealthStatus.AVAILABLE }
                val dotColor = when {
                    !provider.isEnabled -> HealthUntested
                    greenCount > 0 -> HealthAvailable
                    hasKey && provider.lastTestSuccess == false -> HealthUnavailable
                    hasKey -> HealthCooldown
                    else -> HealthUntested
                }
                val tierLabel = when (provider.tier) {
                    1 -> "主力"
                    2 -> "備援"
                    else -> "自訂"
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenProvider(provider.id) }
                        .testTag("provider_card_${provider.id}"),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(dotColor),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = provider.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                ) {
                                    Text(
                                        text = tierLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Text(
                                text = buildString {
                                    append(if (keyStatusMap[provider.id] == true) "已加密儲存金鑰" else if (provider.allowEmptyKey) "免金鑰端點" else "尚未填入 Key")
                                    append(" · ${pModels.size} 模型")
                                    if (greenCount > 0) append(" ($greenCount 綠燈)")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = provider.isEnabled,
                            onCheckedChange = { onToggleProviderEnabled(provider, it) },
                            modifier = Modifier.testTag("provider_switch_${provider.id}"),
                        )
                        Icon(Icons.Outlined.ChevronRight, contentDescription = "進入詳細設定")
                    }
                }
            }

            item {
                Spacer(Modifier.height(6.dp))
                HorizontalDivider()
                Spacer(Modifier.height(6.dp))
                Text(
                    "全域引擎與介面偏好",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("429 限流時靜默自動切換備援", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    "關閉時會彈出提示卡讓您一鍵確認切換；開啟後將在背景自動接續同模型。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = preferences.silentAutoFailover,
                                onCheckedChange = onToggleSilentFailover,
                                modifier = Modifier.testTag("silent_failover_switch"),
                            )
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenCanonicalMapping() }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(Icons.Outlined.AccountTree, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text("跨 API 同模型正規化映射", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(
                                        "管理 Llama / DeepSeek / Qwen 跨供應商別名規則",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Icon(Icons.Outlined.ChevronRight, contentDescription = null)
                        }

                        HorizontalDivider()

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("外觀主題模式", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("SYSTEM" to "跟隨系統", "LIGHT" to "淺色", "DARK" to "深色").forEach { (key, label) ->
                                    FilterChip(
                                        selected = preferences.themeMode == key,
                                        onClick = { onSetThemeMode(key) },
                                        label = { Text(label) },
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Material 3 動態取色 (Dynamic Color)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text("Android 12+ 依系統桌布自動調色", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = preferences.dynamicColorEnabled,
                                onCheckedChange = onSetDynamicColor,
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showAddCustomDialog) {
        var name by remember { mutableStateOf("") }
        var baseUrl by remember { mutableStateOf("http://127.0.0.1:11434/v1") }
        AlertDialog(
            onDismissRequest = { showAddCustomDialog = false },
            title = { Text("新增自訂 OpenAI 相容供應商") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("顯示名稱 (如 本地 Ollama / 中轉站)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("Base URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = baseUrl.isNotBlank(),
                    onClick = {
                        showAddCustomDialog = false
                        onAddCustomProvider(name, baseUrl)
                    },
                ) { Text("建立並設定") }
            },
            dismissButton = {
                TextButton(onClick = { showAddCustomDialog = false }) { Text("取消") }
            },
        )
    }
}
