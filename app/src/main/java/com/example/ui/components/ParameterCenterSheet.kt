package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.ModelEntity
import com.example.data.local.ParameterPresetEntity
import com.example.data.local.PromptTemplateEntity
import com.example.data.model.GenerationParameters
import com.example.ui.theme.MonoFontFamily
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParameterCenterSheet(
    initialConversationParams: GenerationParameters,
    initialGlobalParams: GenerationParameters,
    activePresetId: String?,
    presets: List<ParameterPresetEntity>,
    templates: List<PromptTemplateEntity>,
    models: List<ModelEntity>,
    onApplyPreset: (String?) -> Unit,
    onSaveConversationParams: (GenerationParameters) -> Unit,
    onSaveGlobalParams: (GenerationParameters) -> Unit,
    onSaveCustomPreset: (String, GenerationParameters) -> Unit,
    onSavePromptTemplate: (String, String, String) -> Unit,
    onSaveProxyCredentials: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editingGlobal by remember { mutableStateOf(false) }
    var convDraft by remember(initialConversationParams) { mutableStateOf(initialConversationParams) }
    var globalDraft by remember(initialGlobalParams) { mutableStateOf(initialGlobalParams) }
    var selectedModuleTab by remember { mutableIntStateOf(0) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var showNewTemplateDialog by remember { mutableStateOf(false) }
    var proxyUser by remember { mutableStateOf("") }
    var proxyPass by remember { mutableStateOf("") }

    val current = if (editingGlobal) globalDraft else convDraft
    fun update(block: GenerationParameters.() -> GenerationParameters) {
        if (editingGlobal) globalDraft = globalDraft.block() else convDraft = convDraft.block()
    }

    val moduleTitles = listOf(
        "1.採樣生成",
        "2.深度思考",
        "3.提示上下文",
        "4.供應商路由",
        "5.網路覆寫",
        "6.本地體驗",
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            // Header & Scope Switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("全參數控制中心", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "未設定欄位將自動省略，避免觸發 400 錯誤",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { showSavePresetDialog = true }) {
                        Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text("存 Preset")
                    }
                    Button(
                        onClick = {
                            if (editingGlobal) onSaveGlobalParams(globalDraft)
                            else onSaveConversationParams(convDraft)
                            onDismiss()
                        },
                        modifier = Modifier.testTag("save_params_button"),
                    ) {
                        Text("套用")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !editingGlobal,
                    onClick = { editingGlobal = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) {
                    Text("單一對話覆寫 (${convDraft.activeOverrideCount()})")
                }
                SegmentedButton(
                    selected = editingGlobal,
                    onClick = { editingGlobal = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) {
                    Text("全域預設層")
                }
            }

            if (!editingGlobal) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Preset：", style = MaterialTheme.typography.labelMedium)
                    FilterChip(
                        selected = activePresetId == null,
                        onClick = { onApplyPreset(null) },
                        label = { Text("無") },
                    )
                    presets.filter { it.scope == "PRESET" }.forEach { preset ->
                        FilterChip(
                            selected = activePresetId == preset.id,
                            onClick = { onApplyPreset(preset.id) },
                            label = { Text(preset.name) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            ScrollableTabRow(selectedTabIndex = selectedModuleTab, edgePadding = 0.dp) {
                moduleTitles.forEachIndexed { idx, title ->
                    Tab(
                        selected = selectedModuleTab == idx,
                        onClick = { selectedModuleTab = idx },
                        text = { Text(title) },
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (selectedModuleTab) {
                    0 -> {
                        NullableDoubleSlider(
                            label = "temperature",
                            value = current.temperature,
                            range = 0f..2f,
                            onChange = { update { copy(temperature = it) } },
                        )
                        NullableDoubleSlider(
                            label = "top_p",
                            value = current.topP,
                            range = 0f..1f,
                            onChange = { update { copy(topP = it) } },
                        )
                        NullableIntField(
                            label = "top_k",
                            value = current.topK,
                            onChange = { update { copy(topK = it) } },
                        )
                        NullableDoubleSlider(
                            label = "min_p",
                            value = current.minP,
                            range = 0f..1f,
                            onChange = { update { copy(minP = it) } },
                        )
                        NullableDoubleSlider(
                            label = "top_a",
                            value = current.topA,
                            range = 0f..1f,
                            onChange = { update { copy(topA = it) } },
                        )
                        NullableIntField(
                            label = if (current.useMaxCompletionTokensField == true) "max_completion_tokens" else "max_tokens",
                            value = current.maxTokens,
                            onChange = { update { copy(maxTokens = it) } },
                        )
                        NullableBooleanRow(
                            label = "使用 max_completion_tokens 欄位名",
                            value = current.useMaxCompletionTokensField,
                            onChange = { update { copy(useMaxCompletionTokensField = it) } },
                        )
                        NullableDoubleSlider(
                            label = "presence_penalty",
                            value = current.presencePenalty,
                            range = -2f..2f,
                            onChange = { update { copy(presencePenalty = it) } },
                        )
                        NullableDoubleSlider(
                            label = "frequency_penalty",
                            value = current.frequencyPenalty,
                            range = -2f..2f,
                            onChange = { update { copy(frequencyPenalty = it) } },
                        )
                        NullableDoubleSlider(
                            label = "repetition_penalty",
                            value = current.repetitionPenalty,
                            range = 0.5f..2f,
                            onChange = { update { copy(repetitionPenalty = it) } },
                        )
                        NullableIntField(
                            label = "seed",
                            value = current.seed,
                            onChange = { update { copy(seed = it) } },
                        )
                        NullableCsvField(
                            label = "stop 停止詞（逗號分隔）",
                            values = current.stopSequences,
                            onChange = { update { copy(stopSequences = it) } },
                        )
                        NullableBooleanRow(
                            label = "stream 串流開關",
                            value = current.stream,
                            onChange = { update { copy(stream = it) } },
                        )
                        NullableChoiceRow(
                            label = "response_format 格式",
                            options = listOf("text", "json_object", "json_schema"),
                            selected = current.responseFormatType,
                            onSelect = { update { copy(responseFormatType = it) } },
                        )
                        if (current.responseFormatType == "json_schema") {
                            NullableTextField(
                                label = "JSON Schema 定義",
                                value = current.responseJsonSchema,
                                minLines = 3,
                                onChange = { update { copy(responseJsonSchema = it) } },
                            )
                        }
                        NullableBooleanRow(
                            label = "logprobs 回傳詞元機率",
                            value = current.logprobs,
                            onChange = { update { copy(logprobs = it) } },
                        )
                        if (current.logprobs == true) {
                            NullableIntField(
                                label = "top_logprobs",
                                value = current.topLogprobs,
                                onChange = { update { copy(topLogprobs = it) } },
                            )
                        }
                    }

                    1 -> {
                        NullableChoiceRow(
                            label = "reasoning_effort 推理強度",
                            options = listOf("minimal", "low", "medium", "high"),
                            selected = current.reasoningEffort,
                            onSelect = { update { copy(reasoningEffort = it) } },
                        )
                        NullableIntField(
                            label = "thinking_budget 思考 Token 預算",
                            value = current.thinkingBudget,
                            onChange = { update { copy(thinkingBudget = it) } },
                        )
                        NullableBooleanRow(
                            label = "隱藏思考回傳開關",
                            value = current.hideThinkingOutput,
                            onChange = { update { copy(hideThinkingOutput = it) } },
                        )
                        NullableBooleanRow(
                            label = "思考氣泡預設折疊",
                            value = current.collapseThinkingByDefault,
                            onChange = { update { copy(collapseThinkingByDefault = it) } },
                        )
                        NullableBooleanRow(
                            label = "歷史對話回傳思考過程（關閉可省流）",
                            value = current.includeThinkingInHistory,
                            onChange = { update { copy(includeThinkingInHistory = it) } },
                        )
                    }

                    2 -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("System Prompt 範本庫", style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = { showNewTemplateDialog = true }) {
                                Text("+ 新增範本")
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            templates.forEach { tpl ->
                                FilterChip(
                                    selected = current.systemPrompt == tpl.content,
                                    onClick = { update { copy(systemPrompt = tpl.content) } },
                                    label = { Text(tpl.title) },
                                )
                            }
                        }
                        NullableTextField(
                            label = "System Prompt（支援 {{date}}, {{model}}）",
                            value = current.systemPrompt,
                            minLines = 3,
                            onChange = { update { copy(systemPrompt = it) } },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                val next = (current.systemPrompt.orEmpty() + " {{date}}").trim()
                                update { copy(systemPrompt = next) }
                            }) { Text("插入 {{date}}") }
                            TextButton(onClick = {
                                val next = (current.systemPrompt.orEmpty() + " {{model}}").trim()
                                update { copy(systemPrompt = next) }
                            }) { Text("插入 {{model}}") }
                        }
                        NullableChoiceRow(
                            label = "System Role 名稱覆寫",
                            options = listOf("system", "developer"),
                            selected = current.systemRoleName,
                            onSelect = { update { copy(systemRoleName = it) } },
                        )
                        NullableChoiceRow(
                            label = "上下文截斷模式",
                            options = listOf("NONE", "COUNT", "TOKENS"),
                            selected = current.contextTruncationMode,
                            onSelect = { update { copy(contextTruncationMode = it) } },
                        )
                        if (current.contextTruncationMode == "COUNT") {
                            NullableIntField(
                                label = "保留最近訊息則數",
                                value = current.maxContextMessages,
                                onChange = { update { copy(maxContextMessages = it) } },
                            )
                        }
                        if (current.contextTruncationMode == "TOKENS") {
                            NullableIntField(
                                label = "估算 Token 上限",
                                value = current.maxContextTokens,
                                onChange = { update { copy(maxContextTokens = it) } },
                            )
                        }
                        Text("指定生成標題用小模型", style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            FilterChip(
                                selected = current.titleGeneratorModelKey == null,
                                onClick = { update { copy(titleGeneratorModelKey = null) } },
                                label = { Text("自動擷取首句") },
                            )
                            models.filter { it.isSelected }.take(10).forEach { m ->
                                FilterChip(
                                    selected = current.titleGeneratorModelKey == m.key,
                                    onClick = { update { copy(titleGeneratorModelKey = m.key) } },
                                    label = { Text(m.displayName) },
                                )
                            }
                        }
                    }

                    3 -> {
                        Text("OpenRouter 專屬路由", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        NullableCsvField(
                            label = "自動備援 models 陣列（逗號分隔）",
                            values = current.openRouterFallbackModels,
                            onChange = { update { copy(openRouterFallbackModels = it) } },
                        )
                        NullableCsvField(
                            label = "provider.order 排序偏好",
                            values = current.openRouterProviderOrder,
                            onChange = { update { copy(openRouterProviderOrder = it) } },
                        )
                        NullableCsvField(
                            label = "provider.only 白名單",
                            values = current.openRouterProviderOnly,
                            onChange = { update { copy(openRouterProviderOnly = it) } },
                        )
                        NullableCsvField(
                            label = "provider.ignore 黑名單",
                            values = current.openRouterProviderIgnore,
                            onChange = { update { copy(openRouterProviderIgnore = it) } },
                        )
                        NullableCsvField(
                            label = "quantizations 量化過濾 (如 fp8, bf16)",
                            values = current.openRouterQuantizations,
                            onChange = { update { copy(openRouterQuantizations = it) } },
                        )
                        NullableBooleanRow(
                            label = "OpenRouter middle-out 上下文壓縮",
                            value = current.openRouterMiddleOut,
                            onChange = { update { copy(openRouterMiddleOut = it) } },
                        )
                        HorizontalDivider()
                        Text("Gemini / Mistral / Ollama 專屬參數", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        NullableChoiceRow(
                            label = "Gemini 安全過濾閾值",
                            options = listOf("BLOCK_NONE", "BLOCK_ONLY_HIGH", "BLOCK_MEDIUM_AND_ABOVE"),
                            selected = current.geminiSafetyThreshold,
                            onSelect = { update { copy(geminiSafetyThreshold = it) } },
                        )
                        NullableBooleanRow(
                            label = "Mistral safe_prompt",
                            value = current.mistralSafePrompt,
                            onChange = { update { copy(mistralSafePrompt = it) } },
                        )
                        NullableIntField(
                            label = "Ollama num_ctx 視窗大小",
                            value = current.ollamaNumCtx,
                            onChange = { update { copy(ollamaNumCtx = it) } },
                        )
                        NullableTextField(
                            label = "Ollama keep_alive (如 5m)",
                            value = current.ollamaKeepAlive,
                            onChange = { update { copy(ollamaKeepAlive = it) } },
                        )
                    }

                    4 -> {
                        NullableTextField(
                            label = "Request Path 覆寫 (預設 /chat/completions)",
                            value = current.requestPathOverride,
                            onChange = { update { copy(requestPathOverride = it) } },
                        )
                        NullableIntField(
                            label = "Connect 超時 (秒)",
                            value = current.connectTimeoutSec,
                            onChange = { update { copy(connectTimeoutSec = it) } },
                        )
                        NullableIntField(
                            label = "Read 超時 (秒)",
                            value = current.readTimeoutSec,
                            onChange = { update { copy(readTimeoutSec = it) } },
                        )
                        NullableIntField(
                            label = "5xx 自動重試次數",
                            value = current.retryCount,
                            onChange = { update { copy(retryCount = it) } },
                        )
                        NullableIntField(
                            label = "重試退避基準 (ms)",
                            value = current.retryBackoffMs?.toInt(),
                            onChange = { update { copy(retryBackoffMs = it?.toLong()) } },
                        )
                        NullableTextField(
                            label = "HTTP Proxy 主機",
                            value = current.proxyHost,
                            onChange = { update { copy(proxyHost = it) } },
                        )
                        NullableIntField(
                            label = "HTTP Proxy 連接埠",
                            value = current.proxyPort,
                            onChange = { update { copy(proxyPort = it) } },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = proxyUser,
                                onValueChange = { proxyUser = it },
                                label = { Text("Proxy 帳號 (加密)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = proxyPass,
                                onValueChange = { proxyPass = it },
                                label = { Text("Proxy 密碼") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        OutlinedButton(onClick = { onSaveProxyCredentials(proxyUser, proxyPass) }) {
                            Text("加密儲存 Proxy 憑證")
                        }
                        NullableJsonField(
                            label = "自訂 HTTP Headers JSON",
                            value = current.customHeadersJson,
                            onChange = { update { copy(customHeadersJson = it) } },
                        )
                        NullableJsonField(
                            label = "萬用 Extra Body JSON (深度合併至請求本體)",
                            value = current.extraBodyJson,
                            onChange = { update { copy(extraBodyJson = it) } },
                        )
                    }

                    5 -> {
                        NullableIntField(
                            label = "串流渲染節流間隔 (ms, 預設 45)",
                            value = current.streamRenderThrottleMs?.toInt(),
                            onChange = { update { copy(streamRenderThrottleMs = it?.toLong()) } },
                        )
                        NullableBooleanRow(
                            label = "生成時保持螢幕恆亮",
                            value = current.keepScreenAwake,
                            onChange = { update { copy(keepScreenAwake = it) } },
                        )
                        NullableBooleanRow(
                            label = "串流完成與按鍵震動回饋",
                            value = current.hapticFeedback,
                            onChange = { update { copy(hapticFeedback = it) } },
                        )
                        NullableBooleanRow(
                            label = "啟用 Markdown 富文本渲染",
                            value = current.renderMarkdown,
                            onChange = { update { copy(renderMarkdown = it) } },
                        )
                        NullableBooleanRow(
                            label = "啟用 LaTeX 數學符號渲染",
                            value = current.renderLatex,
                            onChange = { update { copy(renderLatex = it) } },
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showSavePresetDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            title = { Text("另存為參數 Preset") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Preset 名稱") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    enabled = name.isNotBlank(),
                    onClick = {
                        onSaveCustomPreset(name, current)
                        showSavePresetDialog = false
                    },
                ) { Text("儲存") }
            },
            dismissButton = { TextButton(onClick = { showSavePresetDialog = false }) { Text("取消") } },
        )
    }

    if (showNewTemplateDialog) {
        var title by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("自訂") }
        var content by remember { mutableStateOf(current.systemPrompt.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showNewTemplateDialog = false },
            title = { Text("新增 System Prompt 範本") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("範本名稱") }, singleLine = true)
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("分類") }, singleLine = true)
                    OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("內容") }, minLines = 3)
                }
            },
            confirmButton = {
                Button(
                    enabled = title.isNotBlank() && content.isNotBlank(),
                    onClick = {
                        onSavePromptTemplate(title, category, content)
                        showNewTemplateDialog = false
                    },
                ) { Text("新增") }
            },
            dismissButton = { TextButton(onClick = { showNewTemplateDialog = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun NullableDoubleSlider(
    label: String,
    value: Double?,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Double?) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, fontFamily = MonoFontFamily, style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = value?.let { "%.2f".format(it) } ?: "未設定 (省略)",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (value != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (value != null) {
                        IconButton(onClick = { onChange(null) }) {
                            Icon(Icons.Outlined.Close, contentDescription = "清除覆寫（省略）")
                        }
                    }
                }
            }
            Slider(
                value = (value?.toFloat() ?: ((range.start + range.endInclusive) / 2f)).coerceIn(range.start, range.endInclusive),
                onValueChange = { onChange((Math.round(it * 100) / 100.0)) },
                valueRange = range,
            )
        }
    }
}

@Composable
private fun NullableIntField(
    label: String,
    value: Int?,
    onChange: (Int?) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value?.toString().orEmpty()) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { raw ->
                val cleaned = raw.filter { it.isDigit() || it == '-' }
                text = cleaned
                onChange(cleaned.toIntOrNull())
            },
            label = { Text(label) },
            placeholder = { Text("未設定 (發送時省略)") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            TextButton(onClick = {
                text = ""
                onChange(null)
            }) { Text("省略") }
        }
    }
}

@Composable
private fun NullableTextField(
    label: String,
    value: String?,
    minLines: Int = 1,
    onChange: (String?) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.orEmpty()) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onChange(it.takeIf { s -> s.isNotBlank() })
            },
            label = { Text(label) },
            placeholder = { Text("未設定 (省略)") },
            minLines = minLines,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun NullableJsonField(
    label: String,
    value: String?,
    onChange: (String?) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.orEmpty()) }
    val isValid = text.isBlank() || runCatching { JSONObject(text) }.isSuccess
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            if (it.isBlank()) onChange(null)
            else if (runCatching { JSONObject(it) }.isSuccess) onChange(it)
        },
        isError = !isValid,
        supportingText = {
            Text(if (isValid) "留空將自動省略；支援多層巢狀物件深度合併" else "JSON 格式不正確，修正後自動套用")
        },
        label = { Text(label) },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NullableCsvField(
    label: String,
    values: List<String>?,
    onChange: (List<String>?) -> Unit,
) {
    var text by remember(values) { mutableStateOf(values?.joinToString(", ").orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw
            val parsed = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            onChange(parsed.takeIf { it.isNotEmpty() })
        },
        label = { Text(label) },
        placeholder = { Text("未設定 (省略)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NullableBooleanRow(
    label: String,
    value: Boolean?,
    onChange: (Boolean?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = when (value) {
                    true -> "已啟用 (true)"
                    false -> "已關閉 (false)"
                    null -> "未設定 (省略)"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (value != null) {
                TextButton(onClick = { onChange(null) }) { Text("省略") }
            }
            Switch(
                checked = value == true,
                onCheckedChange = { onChange(it) },
            )
        }
    }
}

@Composable
private fun NullableChoiceRow(
    label: String,
    options: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("省略") },
            )
            options.forEach { opt ->
                FilterChip(
                    selected = selected == opt,
                    onClick = { onSelect(opt) },
                    label = { Text(opt) },
                )
            }
        }
    }
}
