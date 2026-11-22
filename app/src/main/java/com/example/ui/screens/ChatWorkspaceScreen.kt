package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ConversationEntity
import com.example.data.local.ConversationMode
import com.example.data.local.MessageRole
import com.example.data.local.MessageStatus
import com.example.data.local.MessageTurnEntity
import com.example.data.local.MessageVersionEntity
import com.example.data.local.ModelEntity
import com.example.data.local.ModelHealthStatus
import com.example.data.local.ParallelMergeStrategy
import com.example.data.local.ParameterPresetEntity
import com.example.data.local.PromptTemplateEntity
import com.example.data.local.ProviderEntity
import com.example.data.local.RoundtableParticipantEntity
import com.example.data.model.GenerationParameters
import com.example.data.model.ParameterMerger
import com.example.domain.FailoverPrompt
import com.example.domain.RoundtableRuntimeState
import com.example.ui.components.AiMessageCard
import com.example.ui.components.DebugInspectorSheet
import com.example.ui.components.FailoverAlertDialog
import com.example.ui.components.ModelHealthBadge
import com.example.ui.components.ParameterCenterSheet
import com.example.ui.components.RoundtableControlBar
import com.example.ui.components.UnifiedModelMenuSheet
import com.example.ui.components.UserMessageCard
import kotlinx.coroutines.launch
import org.json.JSONArray

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatWorkspaceScreen(
    conversations: List<ConversationEntity>,
    activeConversation: ConversationEntity?,
    turns: List<MessageTurnEntity>,
    versions: List<MessageVersionEntity>,
    roundtableParticipants: List<RoundtableParticipantEntity>,
    providers: List<ProviderEntity>,
    models: List<ModelEntity>,
    presets: List<ParameterPresetEntity>,
    templates: List<PromptTemplateEntity>,
    keyStatusMap: Map<String, Boolean>,
    probingKeys: Set<String>,
    isStreaming: Boolean,
    failoverPrompt: FailoverPrompt?,
    roundtableState: RoundtableRuntimeState,
    snackbarHostState: SnackbarHostState,
    onSelectConversation: (String) -> Unit,
    onCreateConversation: (ConversationMode) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onSelectSingleModel: (String) -> Unit,
    onApplyParallelModels: (List<String>) -> Unit,
    onSetParallelMergeStrategy: (ParallelMergeStrategy) -> Unit,
    onAdoptParallelBranch: (turnId: String, branchKey: String) -> Unit,
    onApplyRoundtableConfig: (List<RoundtableParticipantEntity>, Int, Long) -> Unit,
    onSendMessage: (String) -> Unit,
    onStopGeneration: () -> Unit,
    onRegenerateMessage: (turnId: String, branchKey: String, overrideModelKey: String?) -> Unit,
    onSelectMessageVersion: (turnId: String, branchKey: String, versionId: String) -> Unit,
    onEditMessageLocally: (versionId: String, content: String) -> Unit,
    onEditUserAndResend: (turnId: String, content: String) -> Unit,
    onDeleteTurn: (String) -> Unit,
    onDeleteTurnAndFollowing: (Int) -> Unit,
    onForkFromTurn: (String) -> Unit,
    onConfirmFailoverSwitch: (FailoverPrompt.SameModelAvailable) -> Unit,
    onResumeFailoverWithModel: (FailoverPrompt, String) -> Unit,
    onDismissFailover: () -> Unit,
    onStartOrResumeRoundtable: () -> Unit,
    onPauseRoundtable: () -> Unit,
    onStepNextRoundtableTurn: () -> Unit,
    onInjectRoundtableGuidance: (String) -> Unit,
    onProbeSingle: (String) -> Unit,
    onApplyPreset: (String?) -> Unit,
    onSaveConversationParams: (GenerationParameters) -> Unit,
    onSaveGlobalParams: (GenerationParameters) -> Unit,
    onSaveCustomPreset: (String, GenerationParameters) -> Unit,
    onSavePromptTemplate: (String, String, String) -> Unit,
    onSaveProxyCredentials: (String, String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearchAndExport: () -> Unit,
    onOpenCanonicalMapping: () -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current

    var showModelMenu by remember { mutableStateOf(false) }
    var prioritizeHealthyInMenu by remember { mutableStateOf(false) }
    var pendingFailoverForMenu by remember { mutableStateOf<FailoverPrompt?>(null) }
    var showParameterCenter by remember { mutableStateOf(false) }
    var inspectingVersion by remember { mutableStateOf<MessageVersionEntity?>(null) }
    var composerText by remember { mutableStateOf("") }
    var parallelVerticalLayout by remember { mutableStateOf(false) }

    val providersById = remember(providers) { providers.associateBy { it.id } }
    val modelsByKey = remember(models) { models.associateBy { it.key } }
    val enabledProviders = remember(providers) { providers.filter { it.isEnabled }.map { it.id }.toSet() }
    val availableModels = remember(models, enabledProviders) {
        models.filter { it.isSelected && it.providerId in enabledProviders }
    }

    val globalParams = remember(presets) {
        GenerationParameters.fromJsonString(presets.firstOrNull { it.scope == "GLOBAL" }?.paramsJson)
    }
    val convOverrideParams = remember(activeConversation?.parameterOverrideJson) {
        GenerationParameters.fromJsonString(activeConversation?.parameterOverrideJson)
    }
    val activePresetParams = remember(activeConversation?.activePresetId, presets) {
        val pid = activeConversation?.activePresetId
        GenerationParameters.fromJsonString(presets.firstOrNull { it.id == pid }?.paramsJson)
    }
    val effectiveParams = remember(globalParams, activePresetParams, convOverrideParams) {
        ParameterMerger.merge(globalParams, activePresetParams, convOverrideParams)
    }

    val currentMode = activeConversation?.conversationMode() ?: ConversationMode.SINGLE
    val activeParallelKeys = remember(activeConversation?.parallelModelKeysJson) {
        runCatching {
            val arr = JSONArray(activeConversation?.parallelModelKeysJson ?: "[]")
            List(arr.length()) { arr.optString(it) }.filter { it.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    // Keep screen awake while generating if configured
    DisposableEffect(isStreaming, effectiveParams.keepScreenAwake) {
        view.keepScreenOn = isStreaming && (effectiveParams.keepScreenAwake == true)
        onDispose { view.keepScreenOn = false }
    }

    // Haptic feedback when generation finishes
    var wasStreaming by remember { mutableStateOf(false) }
    LaunchedEffect(isStreaming) {
        if (wasStreaming && !isStreaming && effectiveParams.hapticFeedback != false) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        wasStreaming = isStreaming
    }

    // Auto-scroll on new turns
    LaunchedEffect(turns.size) {
        if (turns.isNotEmpty()) {
            listState.animateScrollToItem(turns.lastIndex)
        }
    }

    val versionsByTurn = remember(versions) { versions.groupBy { it.turnId } }
    val hasAnyConfiguredKey = remember(keyStatusMap, providers) {
        providers.any { it.isEnabled && (keyStatusMap[it.id] == true || it.allowEmptyKey) }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.widthIn(max = 310.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "PolyChat 智模工作站",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        FilledIconButton(
                            onClick = {
                                scope.launch { drawerState.close() }
                                onCreateConversation(currentMode)
                            },
                            modifier = Modifier.testTag("drawer_new_chat_btn"),
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = "新對話")
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(6.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(conversations, key = { it.id }) { conv ->
                            val selected = conv.id == activeConversation?.id
                            val modeTag = when (conv.conversationMode()) {
                                ConversationMode.SINGLE -> "1對1"
                                ConversationMode.PARALLEL -> "併發"
                                ConversationMode.ROUNDTABLE -> "圓桌"
                            }
                            NavigationDrawerItem(
                                selected = selected,
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    onSelectConversation(conv.id)
                                },
                                label = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                        ) {
                                            Text(
                                                text = modeTag,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                            )
                                        }
                                        Text(
                                            text = conv.title,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                },
                                badge = {
                                    IconButton(
                                        onClick = { onDeleteConversation(conv.id) },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "刪除對話", modifier = Modifier.size(16.dp))
                                    }
                                },
                            )
                        }
                    }

                    HorizontalDivider()
                    Spacer(Modifier.height(6.dp))

                    NavigationDrawerItem(
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onOpenSettings()
                        },
                        icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                        label = { Text("供應商與金鑰設定") },
                    )
                    NavigationDrawerItem(
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onOpenCanonicalMapping()
                        },
                        icon = { Icon(Icons.Outlined.AccountTree, contentDescription = null) },
                        label = { Text("模型正規化映射") },
                    )
                    NavigationDrawerItem(
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onOpenSearchAndExport()
                        },
                        icon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        label = { Text("全量搜尋與匯出入") },
                    )
                }
            }
        },
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.testTag("open_drawer_btn"),
                        ) {
                            Icon(Icons.Outlined.Menu, contentDescription = "開啟對話側邊欄")
                        }
                    },
                    title = {
                        // Top Unified Model & Mode Pill
                        val singleModel = activeConversation?.activeModelKey?.let { modelsByKey[it] }
                        val pillLabel = when (currentMode) {
                            ConversationMode.SINGLE -> singleModel?.displayName ?: "選擇模型"
                            ConversationMode.PARALLEL -> "多模型對比 (${activeParallelKeys.size})"
                            ConversationMode.ROUNDTABLE -> "AI 圓桌 (${roundtableParticipants.size})"
                        }
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .clickable {
                                    prioritizeHealthyInMenu = false
                                    pendingFailoverForMenu = null
                                    showModelMenu = true
                                }
                                .testTag("top_model_menu_pill"),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = when (currentMode) {
                                        ConversationMode.SINGLE -> Icons.Outlined.Forum
                                        ConversationMode.PARALLEL -> Icons.Outlined.ViewCarousel
                                        ConversationMode.ROUNDTABLE -> Icons.Outlined.Groups
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                if (currentMode == ConversationMode.SINGLE && singleModel != null) {
                                    ModelHealthBadge(
                                        status = singleModel.health(),
                                        cooldownUntilMs = singleModel.cooldownUntilMs,
                                        showLabel = false,
                                    )
                                }
                                Text(
                                    text = pillLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "開啟統一模型與模式選單", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    actions = {
                        val overrideCount = convOverrideParams.activeOverrideCount()
                        IconButton(
                            onClick = { showParameterCenter = true },
                            modifier = Modifier.testTag("open_params_drawer_btn"),
                        ) {
                            BadgedBox(
                                badge = {
                                    if (overrideCount > 0) {
                                        Badge { Text(overrideCount.toString()) }
                                    }
                                },
                            ) {
                                Icon(Icons.Outlined.Tune, contentDescription = "開啟即時參數抽屜")
                            }
                        }
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("open_settings_btn"),
                        ) {
                            Icon(Icons.Outlined.Settings, contentDescription = "開啟供應商設定")
                        }
                    },
                )
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
                    .navigationBarsPadding(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 880.dp),
                ) {
                    // Mode 2 Strategy & View Toggle Bar
                    if (currentMode == ConversationMode.PARALLEL) {
                        val strategy = activeConversation?.mergeStrategy() ?: ParallelMergeStrategy.CONVERGE
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = strategy == ParallelMergeStrategy.CONVERGE,
                                    onClick = { onSetParallelMergeStrategy(ParallelMergeStrategy.CONVERGE) },
                                    label = { Text("主線收斂") },
                                )
                                FilterChip(
                                    selected = strategy == ParallelMergeStrategy.BRANCH,
                                    onClick = { onSetParallelMergeStrategy(ParallelMergeStrategy.BRANCH) },
                                    label = { Text("獨立分支") },
                                )
                            }
                            IconButton(
                                onClick = { parallelVerticalLayout = !parallelVerticalLayout },
                                modifier = Modifier.testTag("toggle_parallel_layout_btn"),
                            ) {
                                Icon(
                                    imageVector = if (parallelVerticalLayout) Icons.Outlined.ViewCarousel else Icons.Outlined.ViewAgenda,
                                    contentDescription = if (parallelVerticalLayout) "切換為橫向分頁" else "切換為垂直展開對比",
                                )
                            }
                        }
                    }

                    // Main Message Feed or Empty Onboarding State
                    if (turns.isEmpty()) {
                        EmptyWorkspaceState(
                            mode = currentMode,
                            hasConfiguredKey = hasAnyConfiguredKey,
                            hasSelectedModels = availableModels.isNotEmpty(),
                            onOpenModelMenu = { showModelMenu = true },
                            onOpenSettings = onOpenSettings,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(turns, key = { it.id }) { turn ->
                                val turnVersions = versionsByTurn[turn.id].orEmpty()
                                when (turn.messageRole()) {
                                    MessageRole.USER, MessageRole.SYSTEM_INTERJECTION -> {
                                        val activeUserVer = turnVersions.firstOrNull { it.isActiveVersion }
                                            ?: turnVersions.firstOrNull()
                                        if (activeUserVer != null) {
                                            UserMessageCard(
                                                turn = turn,
                                                version = activeUserVer,
                                                isInterjection = turn.messageRole() == MessageRole.SYSTEM_INTERJECTION,
                                                onEditAndResend = { newText -> onEditUserAndResend(turn.id, newText) },
                                                onEditLocalOnly = { newText -> onEditMessageLocally(activeUserVer.id, newText) },
                                                onResendPrompt = { onEditUserAndResend(turn.id, activeUserVer.content) },
                                                onDeleteSingleTurn = { onDeleteTurn(turn.id) },
                                                onDeleteWithFollowing = { onDeleteTurnAndFollowing(turn.orderIndex) },
                                            )
                                        }
                                    }

                                    MessageRole.ASSISTANT -> {
                                        val branches = turnVersions.groupBy { it.branchKey }
                                        val branchKeys = branches.keys.toList()
                                        if (currentMode == ConversationMode.PARALLEL && branchKeys.size > 1) {
                                            ParallelTurnSection(
                                                turn = turn,
                                                branchKeys = branchKeys,
                                                branches = branches,
                                                modelsByKey = modelsByKey,
                                                providersById = providersById,
                                                availableModels = availableModels,
                                                verticalExpanded = parallelVerticalLayout,
                                                showAdoptButton = activeConversation?.mergeStrategy() == ParallelMergeStrategy.CONVERGE,
                                                collapseThinkingByDefault = effectiveParams.collapseThinkingByDefault != false,
                                                renderMarkdown = effectiveParams.renderMarkdown != false,
                                                renderLatex = effectiveParams.renderLatex != false,
                                                onSelectVersion = { bKey, verId -> onSelectMessageVersion(turn.id, bKey, verId) },
                                                onRegenerate = { bKey, overrideKey -> onRegenerateMessage(turn.id, bKey, overrideKey) },
                                                onEditLocal = onEditMessageLocally,
                                                onFork = { onForkFromTurn(turn.id) },
                                                onDeleteTurn = { onDeleteTurn(turn.id) },
                                                onAdoptBranch = { bKey -> onAdoptParallelBranch(turn.id, bKey) },
                                                onInspectDebug = { inspectingVersion = it },
                                            )
                                        } else {
                                            val bKey = branchKeys.firstOrNull() ?: "default"
                                            val listInBranch = branches[bKey].orEmpty()
                                            val active = listInBranch.firstOrNull { it.isActiveVersion } ?: listInBranch.lastOrNull()
                                            if (active != null) {
                                                AiMessageCard(
                                                    turn = turn,
                                                    versionsInBranch = listInBranch,
                                                    activeVersion = active,
                                                    collapseThinkingByDefault = effectiveParams.collapseThinkingByDefault != false,
                                                    renderMarkdown = effectiveParams.renderMarkdown != false,
                                                    renderLatex = effectiveParams.renderLatex != false,
                                                    showAdoptButton = false,
                                                    isAdoptedBranch = false,
                                                    availableModels = availableModels,
                                                    providersById = providersById,
                                                    onSelectVersion = { verId -> onSelectMessageVersion(turn.id, bKey, verId) },
                                                    onRegenerate = { overrideKey -> onRegenerateMessage(turn.id, bKey, overrideKey) },
                                                    onEditLocal = { newTxt -> onEditMessageLocally(active.id, newTxt) },
                                                    onForkFromHere = { onForkFromTurn(turn.id) },
                                                    onDeleteTurn = { onDeleteTurn(turn.id) },
                                                    onAdoptBranch = {},
                                                    onInspectDebug = { inspectingVersion = it },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Roundtable Live Control Bar
                    if (currentMode == ConversationMode.ROUNDTABLE) {
                        RoundtableControlBar(
                            state = roundtableState,
                            participantCount = roundtableParticipants.size,
                            onResumeOrStart = onStartOrResumeRoundtable,
                            onPause = onPauseRoundtable,
                            onStepNext = onStepNextRoundtableTurn,
                            onStop = onStopGeneration,
                            onInjectGuidance = onInjectRoundtableGuidance,
                        )
                    }

                    // Message Composer Bar
                    Surface(
                        tonalElevation = 3.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = composerText,
                                onValueChange = { composerText = it },
                                placeholder = {
                                    Text(
                                        when (currentMode) {
                                            ConversationMode.SINGLE -> "輸入訊息開始對話..."
                                            ConversationMode.PARALLEL -> "同時傳送給 ${activeParallelKeys.size} 個模型對比..."
                                            ConversationMode.ROUNDTABLE -> "輸入開局討論主題以啟動圓桌..."
                                        }
                                    )
                                },
                                maxLines = 5,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("chat_composer_input"),
                            )
                            if (isStreaming) {
                                FilledIconButton(
                                    onClick = onStopGeneration,
                                    modifier = Modifier
                                        .size(50.dp)
                                        .testTag("stop_streaming_btn"),
                                ) {
                                    Icon(Icons.Outlined.Stop, contentDescription = "停止生成")
                                }
                            } else {
                                FilledIconButton(
                                    enabled = composerText.isNotBlank(),
                                    onClick = {
                                        val msg = composerText
                                        composerText = ""
                                        onSendMessage(msg)
                                    },
                                    modifier = Modifier
                                        .size(50.dp)
                                        .testTag("send_message_button"),
                                ) {
                                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "傳送訊息")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Unified Model & Mode Menu Sheet
    if (showModelMenu) {
        UnifiedModelMenuSheet(
            currentMode = currentMode,
            activeSingleModelKey = activeConversation?.activeModelKey,
            activeParallelKeys = activeParallelKeys,
            existingRoundtableParticipants = roundtableParticipants,
            initialRoundtableMaxTurns = activeConversation?.roundtableMaxTurns ?: 6,
            initialRoundtableDelayMs = activeConversation?.roundtableDelayMs ?: 1000L,
            providers = providers,
            models = models,
            templates = templates,
            probingKeys = probingKeys,
            prioritizeHealthyModels = prioritizeHealthyInMenu,
            onSelectSingleModel = { chosenKey ->
                val failover = pendingFailoverForMenu
                if (failover != null) {
                    onResumeFailoverWithModel(failover, chosenKey)
                    pendingFailoverForMenu = null
                } else {
                    onSelectSingleModel(chosenKey)
                }
            },
            onApplyParallelModels = onApplyParallelModels,
            onApplyRoundtableConfig = onApplyRoundtableConfig,
            onProbeSingle = onProbeSingle,
            onOpenSettings = onOpenSettings,
            onDismiss = {
                showModelMenu = false
                pendingFailoverForMenu = null
            },
        )
    }

    // Six-Module Parameter Center Sheet
    if (showParameterCenter) {
        ParameterCenterSheet(
            initialConversationParams = convOverrideParams,
            initialGlobalParams = globalParams,
            activePresetId = activeConversation?.activePresetId,
            presets = presets,
            templates = templates,
            models = availableModels,
            onApplyPreset = onApplyPreset,
            onSaveConversationParams = onSaveConversationParams,
            onSaveGlobalParams = onSaveGlobalParams,
            onSaveCustomPreset = onSaveCustomPreset,
            onSavePromptTemplate = onSavePromptTemplate,
            onSaveProxyCredentials = onSaveProxyCredentials,
            onDismiss = { showParameterCenter = false },
        )
    }

    // Debug Inspector Sheet
    inspectingVersion?.let { ver ->
        DebugInspectorSheet(
            version = ver,
            onDismiss = { inspectingVersion = null },
        )
    }

    // 429 Failover Prompt Dialog
    failoverPrompt?.let { prompt ->
        FailoverAlertDialog(
            prompt = prompt,
            onConfirmSameModelSwitch = onConfirmFailoverSwitch,
            onOpenModelPicker = { p ->
                onDismissFailover()
                pendingFailoverForMenu = p
                prioritizeHealthyInMenu = true
                showModelMenu = true
            },
            onDismiss = onDismissFailover,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ParallelTurnSection(
    turn: MessageTurnEntity,
    branchKeys: List<String>,
    branches: Map<String, List<MessageVersionEntity>>,
    modelsByKey: Map<String, ModelEntity>,
    providersById: Map<String, ProviderEntity>,
    availableModels: List<ModelEntity>,
    verticalExpanded: Boolean,
    showAdoptButton: Boolean,
    collapseThinkingByDefault: Boolean,
    renderMarkdown: Boolean,
    renderLatex: Boolean,
    onSelectVersion: (branchKey: String, versionId: String) -> Unit,
    onRegenerate: (branchKey: String, overrideModelKey: String?) -> Unit,
    onEditLocal: (versionId: String, content: String) -> Unit,
    onFork: () -> Unit,
    onDeleteTurn: () -> Unit,
    onAdoptBranch: (String) -> Unit,
    onInspectDebug: (MessageVersionEntity) -> Unit,
) {
    if (verticalExpanded) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            branchKeys.forEach { bKey ->
                val listInBranch = branches[bKey].orEmpty()
                val active = listInBranch.firstOrNull { it.isActiveVersion } ?: listInBranch.lastOrNull()
                if (active != null) {
                    AiMessageCard(
                        turn = turn,
                        versionsInBranch = listInBranch,
                        activeVersion = active,
                        collapseThinkingByDefault = collapseThinkingByDefault,
                        renderMarkdown = renderMarkdown,
                        renderLatex = renderLatex,
                        showAdoptButton = showAdoptButton,
                        isAdoptedBranch = turn.selectedBranchKey == bKey,
                        availableModels = availableModels,
                        providersById = providersById,
                        onSelectVersion = { verId -> onSelectVersion(bKey, verId) },
                        onRegenerate = { overrideKey -> onRegenerate(bKey, overrideKey) },
                        onEditLocal = { newTxt -> onEditLocal(active.id, newTxt) },
                        onForkFromHere = onFork,
                        onDeleteTurn = onDeleteTurn,
                        onAdoptBranch = { onAdoptBranch(bKey) },
                        onInspectDebug = onInspectDebug,
                    )
                }
            }
        }
    } else {
        val pagerState = rememberPagerState(pageCount = { branchKeys.size })
        val scope = rememberCoroutineScope()

        Column(modifier = Modifier.fillMaxWidth()) {
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                edgePadding = 12.dp,
            ) {
                branchKeys.forEachIndexed { idx, bKey ->
                    val listInBranch = branches[bKey].orEmpty()
                    val active = listInBranch.firstOrNull { it.isActiveVersion } ?: listInBranch.lastOrNull()
                    val label = modelsByKey[bKey]?.displayName ?: active?.modelId ?: bKey
                    val statusMark = when (active?.messageStatus()) {
                        MessageStatus.STREAMING -> "⏳"
                        MessageStatus.FROZEN_QUOTA -> "🟡"
                        MessageStatus.ERROR -> "🔴"
                        else -> "🟢"
                    }
                    Tab(
                        selected = pagerState.currentPage == idx,
                        onClick = { scope.launch { pagerState.animateScrollToPage(idx) } },
                        text = {
                            Text(
                                text = "$statusMark $label",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
                val bKey = branchKeys[page]
                val listInBranch = branches[bKey].orEmpty()
                val active = listInBranch.firstOrNull { it.isActiveVersion } ?: listInBranch.lastOrNull()
                if (active != null) {
                    AiMessageCard(
                        turn = turn,
                        versionsInBranch = listInBranch,
                        activeVersion = active,
                        collapseThinkingByDefault = collapseThinkingByDefault,
                        renderMarkdown = renderMarkdown,
                        renderLatex = renderLatex,
                        showAdoptButton = showAdoptButton,
                        isAdoptedBranch = turn.selectedBranchKey == bKey,
                        availableModels = availableModels,
                        providersById = providersById,
                        onSelectVersion = { verId -> onSelectMessageVersionSafe(onSelectVersion, bKey, verId) },
                        onRegenerate = { overrideKey -> onRegenerate(bKey, overrideKey) },
                        onEditLocal = { newTxt -> onEditLocal(active.id, newTxt) },
                        onForkFromHere = onFork,
                        onDeleteTurn = onDeleteTurn,
                        onAdoptBranch = { onAdoptBranch(bKey) },
                        onInspectDebug = onInspectDebug,
                    )
                }
            }
        }
    }
}

private inline fun onSelectMessageVersionSafe(
    onSelectVersion: (String, String) -> Unit,
    bKey: String,
    verId: String,
) {
    onSelectVersion(bKey, verId)
}

@Composable
private fun EmptyWorkspaceState(
    mode: ConversationMode,
    hasConfiguredKey: Boolean,
    hasSelectedModels: Boolean,
    onOpenModelMenu: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp),
                )
                Text(
                    text = when (mode) {
                        ConversationMode.SINGLE -> "PolyChat 多模型工作站 · 普通對話"
                        ConversationMode.PARALLEL -> "多模型併發對比實驗室"
                        ConversationMode.ROUNDTABLE -> "AI 圓桌互聊討論室"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = when (mode) {
                        ConversationMode.SINGLE -> "Local-First 與 BYOK 本地優先架構。隨時切換供應商模型、調校 6 大參數模組，並在 429 限流時自動保留進度。"
                        ConversationMode.PARALLEL -> "單次提問同時發起多條 SSE 串流橫向比較，支援主線收斂或獨立分支續聊。"
                        ConversationMode.ROUNDTABLE -> "指派 2～5 個模型各自扮演不同人設展開自動辯論，您可隨時暫停、單步或中途插話引導。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (!hasConfiguredKey || !hasSelectedModels) {
                    Button(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("empty_state_open_settings_btn"),
                    ) {
                        Text("前往設定 API Key 並同步模型")
                    }
                } else {
                    OutlinedButton(
                        onClick = onOpenModelMenu,
                        modifier = Modifier.testTag("empty_state_open_model_menu_btn"),
                    ) {
                        Text("開啟統一模型與模式選單")
                    }
                }
            }
        }
    }
}
