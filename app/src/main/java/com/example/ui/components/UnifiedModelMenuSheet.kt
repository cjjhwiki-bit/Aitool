package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ConversationMode
import com.example.data.local.ModelEntity
import com.example.data.local.ModelHealthStatus
import com.example.data.local.PromptTemplateEntity
import com.example.data.local.ProviderEntity
import com.example.data.local.RoundtableParticipantEntity
import java.util.UUID

data class RoundtableDraftParticipant(
    val id: String = UUID.randomUUID().toString(),
    val modelKey: String,
    val displayName: String,
    val systemPrompt: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedModelMenuSheet(
    currentMode: ConversationMode,
    activeSingleModelKey: String?,
    activeParallelKeys: List<String>,
    existingRoundtableParticipants: List<RoundtableParticipantEntity>,
    initialRoundtableMaxTurns: Int,
    initialRoundtableDelayMs: Long,
    providers: List<ProviderEntity>,
    models: List<ModelEntity>,
    templates: List<PromptTemplateEntity>,
    probingKeys: Set<String>,
    prioritizeHealthyModels: Boolean = false,
    onSelectSingleModel: (String) -> Unit,
    onApplyParallelModels: (List<String>) -> Unit,
    onApplyRoundtableConfig: (List<RoundtableParticipantEntity>, Int, Long) -> Unit,
    onProbeSingle: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val enabledProviders = remember(providers) { providers.filter { it.isEnabled }.associateBy { it.id } }

    var selectedTab by remember {
        mutableIntStateOf(
            when (currentMode) {
                ConversationMode.SINGLE -> 0
                ConversationMode.PARALLEL -> 1
                ConversationMode.ROUNDTABLE -> 2
            }
        )
    }
    var searchQuery by remember { mutableStateOf("") }
    var onlyGreenFilter by remember { mutableStateOf(prioritizeHealthyModels) }

    val selectedParallel = remember(activeParallelKeys) {
        mutableStateListOf<String>().apply { addAll(activeParallelKeys) }
    }
    val roundtableDrafts = remember(existingRoundtableParticipants) {
        mutableStateListOf<RoundtableDraftParticipant>().apply {
            existingRoundtableParticipants.forEach { p ->
                add(RoundtableDraftParticipant(p.id, p.modelKey, p.displayName, p.systemPrompt))
            }
        }
    }
    var maxTurnsText by remember { mutableStateOf(initialRoundtableMaxTurns.toString()) }
    var delayMsText by remember { mutableStateOf(initialRoundtableDelayMs.toString()) }
    var editingParticipantIdx by remember { mutableStateOf<Int?>(null) }

    val visibleModels = remember(models, enabledProviders, searchQuery, onlyGreenFilter, prioritizeHealthyModels) {
        val now = System.currentTimeMillis()
        models.filter { m ->
            val p = enabledProviders[m.providerId] ?: return@filter false
            if (!m.isSelected) return@filter false
            val isGreen = m.health() == ModelHealthStatus.AVAILABLE
            if (onlyGreenFilter && !isGreen) return@filter false
            if (searchQuery.isNotBlank()) {
                val q = searchQuery.trim().lowercase()
                m.displayName.lowercase().contains(q) ||
                    m.modelId.lowercase().contains(q) ||
                    p.name.lowercase().contains(q)
            } else {
                true
            }
        }.sortedWith(
            compareByDescending<ModelEntity> {
                if (prioritizeHealthyModels || it.health() == ModelHealthStatus.AVAILABLE) 1 else 0
            }.thenBy {
                if (it.health() == ModelHealthStatus.COOLDOWN && it.cooldownUntilMs > now) 1 else 0
            }.thenBy { it.displayName }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            // Top 3 Mode Switcher Tabs
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("普通對話") },
                    modifier = Modifier.testTag("mode_tab_single"),
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("多模型對比") },
                    modifier = Modifier.testTag("mode_tab_parallel"),
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("AI 圓桌互聊") },
                    modifier = Modifier.testTag("mode_tab_roundtable"),
                )
            }

            Spacer(Modifier.height(10.dp))

            // Search + Only Green Filter + Settings
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜尋模型或供應商...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("model_menu_search"),
                )
                FilterChip(
                    selected = onlyGreenFilter,
                    onClick = { onlyGreenFilter = !onlyGreenFilter },
                    label = { Text("只看綠燈") },
                    modifier = Modifier.testTag("only_green_filter_chip"),
                )
                IconButton(
                    onClick = {
                        onDismiss()
                        onOpenSettings()
                    },
                ) {
                    Icon(Icons.Outlined.Settings, contentDescription = "前往供應商設定")
                }
            }

            Spacer(Modifier.height(8.dp))

            // Roundtable Draft Ordering & Persona Config Panel
            if (selectedTab == 2) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "已選參與者 (${roundtableDrafts.size}/5 · 至少 2 位)",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Button(
                                enabled = roundtableDrafts.size >= 2,
                                onClick = {
                                    val maxT = maxTurnsText.toIntOrNull()?.coerceIn(1, 30) ?: 6
                                    val delayMs = delayMsText.toLongOrNull()?.coerceIn(0L, 10_000L) ?: 1000L
                                    val entities = roundtableDrafts.mapIndexed { idx, d ->
                                        RoundtableParticipantEntity(
                                            id = d.id,
                                            conversationId = "",
                                            sortOrder = idx,
                                            modelKey = d.modelKey,
                                            displayName = d.displayName,
                                            systemPrompt = d.systemPrompt,
                                        )
                                    }
                                    onApplyRoundtableConfig(entities, maxT, delayMs)
                                    onDismiss()
                                },
                                modifier = Modifier.testTag("apply_roundtable_button"),
                            ) {
                                Text("套用配置")
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = maxTurnsText,
                                onValueChange = { maxTurnsText = it.filter(Char::isDigit) },
                                label = { Text("最大回合數") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = delayMsText,
                                onValueChange = { delayMsText = it.filter(Char::isDigit) },
                                label = { Text("發言間隔 (ms)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        if (roundtableDrafts.isEmpty()) {
                            Text(
                                "請從下方點選 2～5 個模型加入圓桌順序。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 170.dp)) {
                                itemsIndexed(roundtableDrafts, key = { _, item -> item.id }) { idx, item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            "${idx + 1}.",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(end = 6.dp),
                                        )
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { editingParticipantIdx = idx },
                                        ) {
                                            Text(
                                                item.displayName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                item.systemPrompt.ifBlank { "點擊設定專屬人設 System Prompt" },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        IconButton(
                                            enabled = idx > 0,
                                            onClick = {
                                                val moved = roundtableDrafts.removeAt(idx)
                                                roundtableDrafts.add(idx - 1, moved)
                                            },
                                            modifier = Modifier.size(36.dp),
                                        ) {
                                            Icon(Icons.Outlined.ArrowUpward, contentDescription = "上移順序", modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(
                                            enabled = idx < roundtableDrafts.lastIndex,
                                            onClick = {
                                                val moved = roundtableDrafts.removeAt(idx)
                                                roundtableDrafts.add(idx + 1, moved)
                                            },
                                            modifier = Modifier.size(36.dp),
                                        ) {
                                            Icon(Icons.Outlined.ArrowDownward, contentDescription = "下移順序", modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(
                                            onClick = { roundtableDrafts.removeAt(idx) },
                                            modifier = Modifier.size(36.dp),
                                        ) {
                                            Icon(Icons.Outlined.Close, contentDescription = "移除參與者", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            } else if (selectedTab == 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "已勾選 ${selectedParallel.size} 個對比模型",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Button(
                        enabled = selectedParallel.isNotEmpty(),
                        onClick = {
                            onApplyParallelModels(selectedParallel.toList())
                            onDismiss()
                        },
                        modifier = Modifier.testTag("apply_parallel_button"),
                    ) {
                        Text("開始併發對比")
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            HorizontalDivider()

            if (visibleModels.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = if (onlyGreenFilter) "目前沒有 🟢 可用模型（可關閉「只看綠燈」並點擊 ⚡ 測速）" else "尚未同步或勾選任何可用模型",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenSettings()
                        },
                    ) {
                        Text("前往供應商設定同步模型")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                ) {
                    items(visibleModels, key = { it.key }) { model ->
                        val providerName = enabledProviders[model.providerId]?.name ?: model.providerId
                        val isProbing = model.key in probingKeys
                        val isSelectedSingle = selectedTab == 0 && model.key == activeSingleModelKey
                        val isCheckedParallel = selectedTab == 1 && model.key in selectedParallel

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    when (selectedTab) {
                                        0 -> {
                                            onSelectSingleModel(model.key)
                                            onDismiss()
                                        }
                                        1 -> {
                                            if (model.key in selectedParallel) {
                                                selectedParallel.remove(model.key)
                                            } else {
                                                selectedParallel.add(model.key)
                                            }
                                        }
                                        2 -> {
                                            if (roundtableDrafts.size < 5) {
                                                roundtableDrafts.add(
                                                    RoundtableDraftParticipant(
                                                        modelKey = model.key,
                                                        displayName = model.displayName.take(18),
                                                        systemPrompt = "",
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (selectedTab == 1) {
                                Checkbox(
                                    checked = isCheckedParallel,
                                    onCheckedChange = { checked ->
                                        if (checked && model.key !in selectedParallel) selectedParallel.add(model.key)
                                        else if (!checked) selectedParallel.remove(model.key)
                                    },
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    ModelHealthBadge(
                                        status = model.health(),
                                        cooldownUntilMs = model.cooldownUntilMs,
                                        latencyMs = model.lastLatencyMs,
                                    )
                                    Text(
                                        text = model.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelectedSingle) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                    ) {
                                        Text(
                                            text = providerName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                    if (model.canonicalId.isNotBlank()) {
                                        Text(
                                            text = model.canonicalId,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                if (model.lastError.isNotBlank() && model.health() != ModelHealthStatus.AVAILABLE) {
                                    Text(
                                        text = model.lastError,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (isSelectedSingle) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = "已選擇",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 6.dp),
                                )
                            }
                            if (isProbing) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .padding(4.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                IconButton(
                                    onClick = { onProbeSingle(model.key) },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("probe_single_${model.key}"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Bolt,
                                        contentDescription = "單獨測速模型",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    // Persona Editor Dialog for Roundtable participant
    editingParticipantIdx?.let { idx ->
        if (idx in roundtableDrafts.indices) {
            val target = roundtableDrafts[idx]
            var nameInput by remember(target.id) { mutableStateOf(target.displayName) }
            var personaInput by remember(target.id) { mutableStateOf(target.systemPrompt) }

            androidx.compose.material3.AlertDialog(
                onDismissRequest = { editingParticipantIdx = null },
                title = { Text("設定圓桌角色人設") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("發言名稱") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = personaInput,
                            onValueChange = { personaInput = it },
                            label = { Text("專屬 System Prompt 人設") },
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (templates.isNotEmpty()) {
                            Text("快速套用範本：", style = MaterialTheme.typography.labelMedium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                templates.take(2).forEach { tpl ->
                                    TextButton(onClick = { personaInput = tpl.content }) {
                                        Text(tpl.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            roundtableDrafts[idx] = target.copy(
                                displayName = nameInput.ifBlank { target.displayName },
                                systemPrompt = personaInput,
                            )
                            editingParticipantIdx = null
                        },
                    ) { Text("確定") }
                },
                dismissButton = {
                    TextButton(onClick = { editingParticipantIdx = null }) { Text("取消") }
                },
            )
        }
    }
}
