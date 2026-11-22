package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.PolyChatApplication
import com.example.data.local.CanonicalAliasEntity
import com.example.data.local.ConversationEntity
import com.example.data.local.ConversationMode
import com.example.data.local.MessageTurnEntity
import com.example.data.local.MessageVersionEntity
import com.example.data.local.ModelEntity
import com.example.data.local.ParallelMergeStrategy
import com.example.data.local.ParameterPresetEntity
import com.example.data.local.PromptTemplateEntity
import com.example.data.local.ProviderEntity
import com.example.data.local.RoundtableParticipantEntity
import com.example.data.local.SearchMessageResult
import com.example.data.model.GenerationParameters
import com.example.data.security.AppPreferences
import com.example.domain.FailoverPrompt
import com.example.domain.ProbeQueueState
import com.example.domain.RoundtableRuntimeState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.UUID

sealed interface AppScreen {
    data object Chat : AppScreen
    data object SettingsOverview : AppScreen
    data class ProviderDetail(val providerId: String) : AppScreen
    data object SearchAndExport : AppScreen
    data object CanonicalMapping : AppScreen
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PolyChatApplication
    private val repo = app.repository
    private val probeQueue = app.probeQueue
    private val orchestrator = app.chatOrchestrator
    private val exportImportManager = app.exportImportManager

    private val _screenStack = MutableStateFlow<List<AppScreen>>(listOf(AppScreen.Chat))
    val currentScreen: StateFlow<AppScreen> = _screenStack.asStateFlow()
        .let { flow ->
            MutableStateFlow<AppScreen>(AppScreen.Chat).also { out ->
                viewModelScope.launch {
                    flow.collect { out.value = it.lastOrNull() ?: AppScreen.Chat }
                }
            }
        }

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    val providers: StateFlow<List<ProviderEntity>> = repo.providersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val models: StateFlow<List<ModelEntity>> = repo.modelsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val aliases: StateFlow<List<CanonicalAliasEntity>> = repo.aliasesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val conversations: StateFlow<List<ConversationEntity>> = repo.conversationsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val presets: StateFlow<List<ParameterPresetEntity>> = repo.presetsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val templates: StateFlow<List<PromptTemplateEntity>> = repo.templatesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val preferences: StateFlow<AppPreferences> = repo.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppPreferences())

    val probeQueueState: StateFlow<ProbeQueueState> = probeQueue.queueState
    val probingKeys: StateFlow<Set<String>> = probeQueue.probingKeys
    val activeStreamingConvIds: StateFlow<Set<String>> = orchestrator.activeStreamingConvIds
    val failoverPrompt: StateFlow<FailoverPrompt?> = orchestrator.failoverPrompt
    val roundtableState: StateFlow<RoundtableRuntimeState> = orchestrator.roundtableState

    val activeConversation: StateFlow<ConversationEntity?> = _activeConversationId
        .flatMapLatest { id -> if (id == null) flowOf(null) else repo.observeConversation(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeTurns: StateFlow<List<MessageTurnEntity>> = _activeConversationId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.observeTurns(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeVersions: StateFlow<List<MessageVersionEntity>> = _activeConversationId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.observeVersions(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeRoundtableParticipants: StateFlow<List<RoundtableParticipantEntity>> = _activeConversationId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.observeRoundtableParticipants(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchResults = MutableStateFlow<List<SearchMessageResult>>(emptyList())
    val searchResults: StateFlow<List<SearchMessageResult>> = _searchResults.asStateFlow()

    private val _providerKeyStatus = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val providerKeyStatus: StateFlow<Map<String, Boolean>> = _providerKeyStatus.asStateFlow()

    init {
        viewModelScope.launch {
            conversations.collect { list ->
                if (_activeConversationId.value == null && list.isNotEmpty()) {
                    _activeConversationId.value = list.first().id
                }
            }
        }
        viewModelScope.launch {
            providers.collect { list ->
                val map = mutableMapOf<String, Boolean>()
                list.forEach { p ->
                    map[p.id] = repo.getApiKey(p.id).isNotBlank()
                }
                _providerKeyStatus.value = map
            }
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun navigateTo(screen: AppScreen) {
        _screenStack.value = _screenStack.value + screen
    }

    fun navigateBack(): Boolean {
        val stack = _screenStack.value
        if (stack.size > 1) {
            _screenStack.value = stack.dropLast(1)
            return true
        }
        return false
    }

    fun popToChat() {
        _screenStack.value = listOf(AppScreen.Chat)
    }

    fun selectConversation(id: String) {
        _activeConversationId.value = id
        popToChat()
    }

    fun createNewConversation(mode: ConversationMode = ConversationMode.SINGLE) {
        viewModelScope.launch {
            val enabledProviders = providers.value.filter { it.isEnabled }.map { it.id }.toSet()
            val candidateModels = models.value.filter { it.isSelected && it.providerId in enabledProviders }
            val defaultSingle = candidateModels.firstOrNull { it.healthStatus == "AVAILABLE" }?.key
                ?: candidateModels.firstOrNull()?.key
            val defaultParallel = candidateModels.take(2).map { it.key }
            val conv = repo.createConversation(
                mode = mode,
                activeModelKey = defaultSingle,
                parallelModelKeys = defaultParallel,
            )
            _activeConversationId.value = conv.id
            popToChat()
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            orchestrator.stopGeneration(id)
            repo.deleteConversation(id)
            if (_activeConversationId.value == id) {
                _activeConversationId.value = conversations.value.firstOrNull { it.id != id }?.id
            }
        }
    }

    fun setConversationMode(mode: ConversationMode) {
        viewModelScope.launch {
            val conv = ensureActiveConversation(mode)
            repo.updateConversation(conv.copy(mode = mode.name))
        }
    }

    fun selectSingleModel(modelKey: String) {
        viewModelScope.launch {
            val conv = ensureActiveConversation(ConversationMode.SINGLE)
            repo.updateConversation(conv.copy(mode = ConversationMode.SINGLE.name, activeModelKey = modelKey))
        }
    }

    fun updateParallelModels(modelKeys: List<String>) {
        viewModelScope.launch {
            val conv = ensureActiveConversation(ConversationMode.PARALLEL)
            repo.updateConversation(
                conv.copy(
                    mode = ConversationMode.PARALLEL.name,
                    parallelModelKeysJson = JSONArray(modelKeys).toString(),
                )
            )
        }
    }

    fun setParallelMergeStrategy(strategy: ParallelMergeStrategy) {
        viewModelScope.launch {
            val conv = activeConversation.value ?: return@launch
            repo.updateConversation(conv.copy(parallelMergeStrategy = strategy.name))
        }
    }

    fun adoptParallelBranch(turnId: String, branchKey: String) {
        viewModelScope.launch {
            repo.selectParallelConvergeBranch(turnId, branchKey)
            showMessage("已採用此模型回答作為下一輪共同脈絡")
        }
    }

    fun saveRoundtableConfig(
        participants: List<RoundtableParticipantEntity>,
        maxTurns: Int,
        delayMs: Long,
    ) {
        viewModelScope.launch {
            val conv = ensureActiveConversation(ConversationMode.ROUNDTABLE)
            repo.updateConversation(
                conv.copy(
                    mode = ConversationMode.ROUNDTABLE.name,
                    roundtableMaxTurns = maxTurns.coerceIn(1, 30),
                    roundtableDelayMs = delayMs.coerceIn(0L, 10_000L),
                )
            )
            repo.saveRoundtableParticipants(conv.id, participants)
            showMessage("圓桌配置已儲存（${participants.size} 位參與者）")
        }
    }

    private suspend fun ensureActiveConversation(defaultMode: ConversationMode): ConversationEntity {
        val current = activeConversation.value
        if (current != null) return current
        val created = repo.createConversation(mode = defaultMode)
        _activeConversationId.value = created.id
        return created
    }

    // Messaging & Orchestration
    fun sendUserMessage(text: String) {
        viewModelScope.launch {
            val conv = ensureActiveConversation(ConversationMode.SINGLE)
            orchestrator.sendUserMessage(conv.id, text)
        }
    }

    fun stopActiveGeneration() {
        val id = _activeConversationId.value ?: return
        orchestrator.stopGeneration(id)
    }

    fun regenerateMessage(turnId: String, branchKey: String, overrideModelKey: String? = null) {
        val id = _activeConversationId.value ?: return
        orchestrator.regenerateVersion(id, turnId, branchKey, overrideModelKey)
    }

    fun selectMessageVersion(turnId: String, branchKey: String, versionId: String) {
        viewModelScope.launch {
            repo.selectMessageVersion(turnId, branchKey, versionId)
        }
    }

    fun editMessageContentLocally(versionId: String, newContent: String) {
        viewModelScope.launch {
            repo.editVersionContentLocally(versionId, newContent)
            showMessage("已僅更新本地紀錄（未重送 API）")
        }
    }

    fun editUserMessageAndResend(turnId: String, newContent: String) {
        val id = _activeConversationId.value ?: return
        orchestrator.editUserTurnAndRegenerate(id, turnId, newContent)
    }

    fun deleteTurn(turnId: String) {
        viewModelScope.launch {
            repo.deleteTurn(turnId)
        }
    }

    fun deleteTurnAndFollowing(orderIndex: Int) {
        val id = _activeConversationId.value ?: return
        viewModelScope.launch {
            repo.deleteTurnAndFollowing(id, orderIndex)
        }
    }

    fun forkFromTurn(turnId: String) {
        val id = _activeConversationId.value ?: return
        viewModelScope.launch {
            val forked = repo.forkConversationFromTurn(id, turnId)
            if (forked != null) {
                _activeConversationId.value = forked.id
                showMessage("已建立分岔對話：${forked.title}")
            }
        }
    }

    // Failover actions
    fun confirmFailoverSwitch(prompt: FailoverPrompt.SameModelAvailable) {
        orchestrator.confirmFailoverSwitch(prompt)
    }

    fun resumeFailoverWithModel(prompt: FailoverPrompt, newModelKey: String) {
        when (prompt) {
            is FailoverPrompt.SameModelAvailable -> orchestrator.resumeWithAlternativeModel(
                prompt.conversationId,
                prompt.turnId,
                prompt.branchKey,
                newModelKey,
                "${prompt.failedProviderName} · ${prompt.failedModelId}",
            )
            is FailoverPrompt.NoSameModelFallback -> orchestrator.resumeWithAlternativeModel(
                prompt.conversationId,
                prompt.turnId,
                prompt.branchKey,
                newModelKey,
                "${prompt.failedProviderName} · ${prompt.failedModelId}",
            )
        }
    }

    fun dismissFailoverPrompt() = orchestrator.dismissFailoverPrompt()

    // Roundtable live intervention
    fun startOrResumeRoundtable() {
        val id = _activeConversationId.value ?: return
        orchestrator.startOrResumeRoundtable(id)
    }

    fun pauseRoundtable() {
        val id = _activeConversationId.value ?: return
        orchestrator.pauseRoundtable(id)
    }

    fun stepNextRoundtableTurn() {
        val id = _activeConversationId.value ?: return
        orchestrator.stepNextRoundtableTurn(id)
    }

    fun injectRoundtableMessage(text: String) {
        val id = _activeConversationId.value ?: return
        orchestrator.injectRoundtableUserMessage(id, text)
    }

    // Provider & Model management
    fun toggleProviderEnabled(provider: ProviderEntity, enabled: Boolean) {
        viewModelScope.launch {
            repo.upsertProvider(provider.copy(isEnabled = enabled))
        }
    }

    fun saveProviderDetail(provider: ProviderEntity, plainApiKey: String?) {
        viewModelScope.launch {
            repo.upsertProvider(provider)
            if (plainApiKey != null) {
                repo.saveApiKey(provider.id, plainApiKey)
                val map = _providerKeyStatus.value.toMutableMap()
                map[provider.id] = plainApiKey.isNotBlank()
                _providerKeyStatus.value = map
            }
            showMessage("已儲存 ${provider.name} 設定")
        }
    }

    suspend fun getMaskedApiKey(providerId: String): String = repo.getMaskedApiKey(providerId)
    suspend fun getPlainApiKey(providerId: String): String = repo.getApiKey(providerId)

    fun addCustomProvider(name: String, baseUrl: String) {
        viewModelScope.launch {
            val created = repo.addCustomProvider(name, baseUrl)
            navigateTo(AppScreen.ProviderDetail(created.id))
        }
    }

    fun deleteCustomProvider(providerId: String) {
        viewModelScope.launch {
            repo.deleteCustomProvider(providerId)
            navigateBack()
            showMessage("已刪除自訂供應商")
        }
    }

    fun testProviderConnection(provider: ProviderEntity, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            val res = repo.testProviderConnection(provider)
            onResult(res)
        }
    }

    fun syncProviderModels(provider: ProviderEntity, onResult: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            val res = repo.syncModelsForProvider(provider)
            onResult(res)
        }
    }

    fun addManualModel(providerId: String, modelId: String) {
        if (modelId.isBlank()) return
        viewModelScope.launch {
            repo.addManualModel(providerId, modelId)
            showMessage("已新增模型：$modelId")
        }
    }

    fun toggleModelSelected(modelKey: String, selected: Boolean) {
        viewModelScope.launch { repo.toggleModelSelected(modelKey, selected) }
    }

    fun deleteModel(modelKey: String) {
        viewModelScope.launch { repo.deleteModel(modelKey) }
    }

    fun probeSingleModel(modelKey: String) = probeQueue.probeSingle(modelKey)
    fun probeBatchModels(modelKeys: List<String>) = probeQueue.probeBatch(modelKeys)
    fun cancelBatchProbe() = probeQueue.cancelBatch()

    // Parameter Center & Templates
    suspend fun getGlobalParameters(): GenerationParameters {
        val global = presets.value.firstOrNull { it.scope == "GLOBAL" }
        return GenerationParameters.fromJsonString(global?.paramsJson)
    }

    fun saveGlobalParameters(params: GenerationParameters) {
        viewModelScope.launch {
            repo.saveGlobalParameters(params)
            showMessage("全域預設參數已儲存")
        }
    }

    fun saveConversationParameters(params: GenerationParameters) {
        val id = _activeConversationId.value ?: return
        viewModelScope.launch {
            repo.saveConversationParameters(id, params)
            showMessage("已套用至目前對話")
        }
    }

    fun applyPresetToActiveConversation(presetId: String?) {
        val conv = activeConversation.value ?: return
        viewModelScope.launch {
            repo.updateConversation(conv.copy(activePresetId = presetId))
            showMessage(if (presetId == null) "已清除 Preset" else "已套用 Preset")
        }
    }

    fun saveCustomPreset(name: String, params: GenerationParameters) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repo.saveCustomPreset(name, params)
            showMessage("已儲存 Preset：$name")
        }
    }

    fun deletePreset(id: String) {
        viewModelScope.launch { repo.deletePreset(id) }
    }

    fun savePromptTemplate(title: String, category: String, content: String) {
        if (title.isBlank() || content.isBlank()) return
        viewModelScope.launch {
            repo.upsertPromptTemplate(
                PromptTemplateEntity(
                    id = "tpl_${UUID.randomUUID().toString().take(8)}",
                    title = title.trim(),
                    category = category.ifBlank { "自訂" },
                    content = content.trim(),
                )
            )
            showMessage("已新增提示詞範本")
        }
    }

    fun deletePromptTemplate(id: String) {
        viewModelScope.launch { repo.deletePromptTemplate(id) }
    }

    fun saveProxyCredentials(user: String, pass: String) {
        viewModelScope.launch {
            repo.saveProxyCredentials(user, pass)
            showMessage("Proxy 驗證資訊已加密儲存")
        }
    }

    // Global Preferences & Canonical Mapping
    fun setSilentAutoFailover(enabled: Boolean) {
        viewModelScope.launch { repo.setSilentAutoFailover(enabled) }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { repo.setThemeMode(mode) }
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        viewModelScope.launch { repo.setDynamicColorEnabled(enabled) }
    }

    fun addCanonicalAlias(familyId: String, displayName: String, pattern: String) {
        if (familyId.isBlank() || pattern.isBlank()) return
        viewModelScope.launch {
            repo.upsertCanonicalAlias(
                CanonicalAliasEntity(
                    id = "user_${UUID.randomUUID().toString().take(8)}",
                    canonicalFamilyId = familyId.trim().lowercase(),
                    displayName = displayName.ifBlank { familyId },
                    matchPattern = pattern.trim(),
                    isUserDefined = true,
                )
            )
            showMessage("已新增模型正規化規則")
        }
    }

    fun deleteCanonicalAlias(id: String) {
        viewModelScope.launch { repo.deleteCanonicalAlias(id) }
    }

    fun bindModelCanonicalId(modelKey: String, canonicalId: String) {
        viewModelScope.launch {
            repo.bindModelCanonicalId(modelKey, canonicalId)
            showMessage("已更新模型標準家族 ID")
        }
    }

    // Search & Export / Import
    fun searchMessages(query: String, includeReasoning: Boolean) {
        viewModelScope.launch {
            _searchResults.value = repo.searchMessages(query, includeReasoning)
        }
    }

    suspend fun exportAllJson(): String = exportImportManager.exportAllToJson()

    fun importJsonBackup(jsonText: String) {
        viewModelScope.launch {
            val res = exportImportManager.importFromJson(jsonText)
            res.onSuccess { count ->
                showMessage("成功匯入還原 $count 筆對話")
            }.onFailure { err ->
                showMessage("匯入失敗：${err.localizedMessage}")
            }
        }
    }

    suspend fun exportActiveConversationMarkdown(): String {
        val id = _activeConversationId.value ?: return ""
        return exportImportManager.exportConversationToMarkdown(id)
    }
}
