package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ModelEntity
import com.example.data.local.ProviderEntity
import com.example.data.local.ProviderType
import com.example.domain.ProbeQueueState
import com.example.network.ProviderAdapter
import com.example.ui.components.ModelHealthBadge
import com.example.ui.theme.MonoFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderDetailScreen(
    provider: ProviderEntity,
    models: List<ModelEntity>,
    probeQueueState: ProbeQueueState,
    probingKeys: Set<String>,
    loadPlainApiKey: suspend (String) -> String,
    onBack: () -> Unit,
    onSave: (ProviderEntity, String?) -> Unit,
    onDeleteCustomProvider: (String) -> Unit,
    onTestConnection: (ProviderEntity, (Result<String>) -> Unit) -> Unit,
    onSyncModels: (ProviderEntity, (Result<Int>) -> Unit) -> Unit,
    onAddManualModel: (String, String) -> Unit,
    onToggleModelSelected: (String, Boolean) -> Unit,
    onDeleteModel: (String) -> Unit,
    onProbeSingle: (String) -> Unit,
    onProbeBatch: (List<String>) -> Unit,
    onCancelBatchProbe: () -> Unit,
) {
    BackHandler { onBack() }

    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    var name by remember(provider.id) { mutableStateOf(provider.name) }
    var isEnabled by remember(provider.id) { mutableStateOf(provider.isEnabled) }
    var baseUrl by remember(provider.id) { mutableStateOf(provider.baseUrl) }
    var cfAccountId by remember(provider.id) { mutableStateOf(provider.cfAccountId) }
    var cfGatewayId by remember(provider.id) { mutableStateOf(provider.cfGatewayId) }
    var openRouterFreeOnly by remember(provider.id) { mutableStateOf(provider.openRouterFreeOnly) }
    var allowEmptyKey by remember(provider.id) { mutableStateOf(provider.allowEmptyKey) }
    var customHeadersJson by remember(provider.id) { mutableStateOf(provider.customHeadersJson) }

    var apiKeyInput by remember(provider.id) { mutableStateOf("") }
    var keyLoaded by remember(provider.id) { mutableStateOf(false) }
    var showKeyPlain by remember { mutableStateOf(false) }
    var showBaseUrlSection by remember { mutableStateOf(provider.providerType() == ProviderType.CUSTOM) }
    var manualModelId by remember { mutableStateOf("") }

    var isTesting by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var statusBanner by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(provider.id) {
        apiKeyInput = loadPlainApiKey(provider.id)
        keyLoaded = true
    }

    fun currentDraftEntity(): ProviderEntity = provider.copy(
        name = name.ifBlank { provider.name },
        isEnabled = isEnabled,
        baseUrl = baseUrl.trim(),
        cfAccountId = cfAccountId.trim(),
        cfGatewayId = cfGatewayId.trim(),
        openRouterFreeOnly = openRouterFreeOnly,
        allowEmptyKey = allowEmptyKey,
        customHeadersJson = customHeadersJson.trim(),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(provider.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回設定總覽")
                    }
                },
                actions = {
                    if (provider.providerType() == ProviderType.CUSTOM) {
                        IconButton(onClick = { onDeleteCustomProvider(provider.id) }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "刪除自訂供應商")
                        }
                    }
                    Button(
                        onClick = { onSave(currentDraftEntity(), if (keyLoaded) apiKeyInput else null) },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("save_provider_detail_btn"),
                    ) {
                        Text("儲存")
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Gemini Privacy Warning
            if (provider.providerType() == ProviderType.GEMINI) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.WarningAmber, contentDescription = null)
                            Text(
                                text = "隱私提示：Google Gemini AI Studio 免費層之請求與回應可能受 Google 資料政策審閱或用於模型改進，請避免傳送機密金鑰或個人隱私資料。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }
            }

            // Master enable + Custom display name
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("啟用此供應商", style = MaterialTheme.typography.titleSmall)
                            Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                        }

                        if (provider.providerType() == ProviderType.CUSTOM) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("自訂顯示名稱") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("允許無 API Key（本地 Ollama / LM Studio）", style = MaterialTheme.typography.bodyMedium)
                                Switch(checked = allowEmptyKey, onCheckedChange = { allowEmptyKey = it })
                            }
                        }

                        // Encrypted API Key field
                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            label = { Text("API Key（Android Keystore AES-GCM 加密儲存）") },
                            placeholder = { Text(if (allowEmptyKey) "可留空" else "貼上金鑰...") },
                            singleLine = true,
                            visualTransformation = if (showKeyPlain) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                Row {
                                    IconButton(onClick = {
                                        clipboard.getText()?.text?.let { apiKeyInput = it.trim() }
                                    }) {
                                        Icon(Icons.Outlined.ContentPaste, contentDescription = "貼上金鑰")
                                    }
                                    IconButton(onClick = { showKeyPlain = !showKeyPlain }) {
                                        Icon(
                                            if (showKeyPlain) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                            contentDescription = if (showKeyPlain) "隱藏金鑰" else "顯示金鑰",
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("provider_api_key_input"),
                        )

                        if (provider.keyPortalUrl.isNotBlank()) {
                            TextButton(
                                onClick = { runCatching { uriHandler.openUri(provider.keyPortalUrl) } },
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("前往官方申請免費 API Key")
                            }
                        }

                        // Cloudflare dedicated fields
                        if (provider.providerType() == ProviderType.CLOUDFLARE) {
                            OutlinedTextField(
                                value = cfAccountId,
                                onValueChange = { cfAccountId = it },
                                label = { Text("Cloudflare Account ID") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = cfGatewayId,
                                onValueChange = { cfGatewayId = it },
                                label = { Text("AI Gateway ID (選填，填入將走 Gateway compat 端點)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = "自動拼裝端點：${ProviderAdapter.resolveBaseUrl(currentDraftEntity())}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        // OpenRouter free filter
                        if (provider.providerType() == ProviderType.OPENROUTER) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("僅顯示免費模型 (:free)", style = MaterialTheme.typography.bodyMedium)
                                    Text("同步模型列表時自動過濾收費模型", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = openRouterFreeOnly, onCheckedChange = { openRouterFreeOnly = it })
                            }
                        }

                        // Collapsible Base URL & Custom Headers
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showBaseUrlSection = !showBaseUrlSection }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("進階端點與自訂 Headers", style = MaterialTheme.typography.labelLarge)
                            Icon(
                                if (showBaseUrlSection) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = null,
                            )
                        }
                        AnimatedVisibility(visible = showBaseUrlSection) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = baseUrl,
                                    onValueChange = { baseUrl = it },
                                    label = { Text("Base URL (需含 /v1 等版本路徑)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = customHeadersJson,
                                    onValueChange = { customHeadersJson = it },
                                    label = { Text("自訂 HTTP Headers JSON (如 {\"X-Custom\":\"1\"})") },
                                    minLines = 2,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        // Connection test & Sync actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                enabled = !isTesting,
                                onClick = {
                                    val draft = currentDraftEntity()
                                    onSave(draft, if (keyLoaded) apiKeyInput else null)
                                    isTesting = true
                                    onTestConnection(draft) { res ->
                                        isTesting = false
                                        statusBanner = res.fold(
                                            onSuccess = { it },
                                            onFailure = { "連線失敗：${it.localizedMessage}" },
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("test_connection_btn"),
                            ) {
                                if (isTesting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                else Text("測試連線")
                            }
                            Button(
                                enabled = !isSyncing,
                                onClick = {
                                    val draft = currentDraftEntity()
                                    onSave(draft, if (keyLoaded) apiKeyInput else null)
                                    isSyncing = true
                                    onSyncModels(draft) { res ->
                                        isSyncing = false
                                        statusBanner = res.fold(
                                            onSuccess = { "已同步 $it 個模型至清單" },
                                            onFailure = { "同步失敗：${it.localizedMessage}" },
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("sync_models_btn"),
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                } else {
                                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(4.dp))
                                    Text("同步模型")
                                }
                            }
                        }

                        statusBanner?.let { msg ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(10.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Manual Add Model + Batch Probe Controls
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = manualModelId,
                            onValueChange = { manualModelId = it },
                            label = { Text("手動新增模型 ID") },
                            placeholder = { Text("如 llama-3.3-70b-versatile") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("manual_model_input"),
                        )
                        Button(
                            enabled = manualModelId.isNotBlank(),
                            onClick = {
                                onAddManualModel(provider.id, manualModelId)
                                manualModelId = ""
                            },
                            modifier = Modifier.testTag("add_manual_model_btn"),
                        ) {
                            Text("新增")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("模型清單與實測 (${models.size})", style = MaterialTheme.typography.titleSmall)
                        val selectedKeys = models.filter { it.isSelected }.map { it.key }
                        if (probeQueueState.isRunning) {
                            TextButton(onClick = onCancelBatchProbe) {
                                Text("停止測速 (${probeQueueState.completedCount}/${probeQueueState.totalCount})")
                            }
                        } else {
                            OutlinedButton(
                                enabled = selectedKeys.isNotEmpty(),
                                onClick = {
                                    onSave(currentDraftEntity(), if (keyLoaded) apiKeyInput else null)
                                    onProbeBatch(selectedKeys)
                                },
                                modifier = Modifier.testTag("batch_probe_btn"),
                            ) {
                                Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("批次探測已勾選 (${selectedKeys.size})")
                            }
                        }
                    }

                    if (probeQueueState.isRunning) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            LinearProgressIndicator(
                                progress = {
                                    if (probeQueueState.totalCount > 0) {
                                        probeQueueState.completedCount.toFloat() / probeQueueState.totalCount
                                    } else 0f
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = "節流排隊探測中：${probeQueueState.currentModelName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            items(models, key = { it.key }) { model ->
                val isProbing = model.key in probingKeys
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = model.isSelected,
                            onCheckedChange = { onToggleModelSelected(model.key, it) },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = model.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = model.modelId,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ModelHealthBadge(
                                    status = model.health(),
                                    cooldownUntilMs = model.cooldownUntilMs,
                                    latencyMs = model.lastLatencyMs,
                                )
                                if (model.canonicalId.isNotBlank()) {
                                    Text(
                                        text = "家族: ${model.canonicalId}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            if (model.lastError.isNotBlank()) {
                                Text(
                                    text = model.lastError,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (isProbing) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = { onProbeSingle(model.key) }) {
                                Icon(Icons.Outlined.Bolt, contentDescription = "單獨測速", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        IconButton(onClick = { onDeleteModel(model.key) }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "移除模型")
                        }
                    }
                }
                HorizontalDivider(thickness = 0.dp)
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
