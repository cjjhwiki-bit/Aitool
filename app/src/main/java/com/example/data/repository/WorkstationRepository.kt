package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.CanonicalAliasEntity
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
import com.example.data.local.ProviderType
import com.example.data.local.RoundtableParticipantEntity
import com.example.data.local.SearchMessageResult
import com.example.data.model.GenerationParameters
import com.example.data.model.ParameterMerger
import com.example.data.security.AppPreferences
import com.example.data.security.SecureCredentialStore
import com.example.domain.CanonicalModelMapper
import com.example.network.AiNetworkClient
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import java.util.UUID

class WorkstationRepository(
    private val db: AppDatabase,
    private val credentialStore: SecureCredentialStore,
    private val networkClient: AiNetworkClient,
    private val canonicalMapper: CanonicalModelMapper,
) {
    private val providerDao = db.providerDao()
    private val modelDao = db.modelDao()
    private val aliasDao = db.canonicalAliasDao()
    private val conversationDao = db.conversationDao()
    private val messageDao = db.messageDao()
    private val roundtableDao = db.roundtableDao()
    private val presetDao = db.parameterPresetDao()
    private val templateDao = db.promptTemplateDao()

    val providersFlow: Flow<List<ProviderEntity>> = providerDao.observeAll()
    val modelsFlow: Flow<List<ModelEntity>> = modelDao.observeAll()
    val aliasesFlow: Flow<List<CanonicalAliasEntity>> = aliasDao.observeAll()
    val conversationsFlow: Flow<List<ConversationEntity>> = conversationDao.observeAll()
    val presetsFlow: Flow<List<ParameterPresetEntity>> = presetDao.observeAll()
    val templatesFlow: Flow<List<PromptTemplateEntity>> = templateDao.observeAll()
    val preferencesFlow: Flow<AppPreferences> = credentialStore.preferencesFlow

    fun observeProvider(id: String): Flow<ProviderEntity?> = providerDao.observeById(id)
    fun observeModelsForProvider(providerId: String): Flow<List<ModelEntity>> = modelDao.observeByProvider(providerId)
    fun observeHasApiKey(providerId: String): Flow<Boolean> = credentialStore.observeHasApiKey(providerId)
    fun observeConversation(id: String): Flow<ConversationEntity?> = conversationDao.observeById(id)
    fun observeTurns(conversationId: String): Flow<List<MessageTurnEntity>> = messageDao.observeTurns(conversationId)
    fun observeVersions(conversationId: String): Flow<List<MessageVersionEntity>> = messageDao.observeVersionsForConversation(conversationId)
    fun observeRoundtableParticipants(conversationId: String): Flow<List<RoundtableParticipantEntity>> =
        roundtableDao.observeParticipants(conversationId)

    suspend fun setSilentAutoFailover(enabled: Boolean) = credentialStore.setSilentAutoFailover(enabled)
    suspend fun setThemeMode(mode: String) = credentialStore.setThemeMode(mode)
    suspend fun setDynamicColorEnabled(enabled: Boolean) = credentialStore.setDynamicColorEnabled(enabled)

    suspend fun getApiKey(providerId: String): String = credentialStore.getApiKey(providerId)
    suspend fun getMaskedApiKey(providerId: String): String = credentialStore.getMaskedApiKey(providerId)
    suspend fun saveApiKey(providerId: String, key: String) = credentialStore.saveApiKey(providerId, key)
    suspend fun saveProxyCredentials(username: String, password: String) = credentialStore.saveProxyCredentials(username, password)
    suspend fun getProxyCredentials(): Pair<String, String> = credentialStore.getProxyCredentials()

    suspend fun upsertProvider(provider: ProviderEntity) = providerDao.upsert(provider)
    suspend fun deleteCustomProvider(providerId: String) = providerDao.deleteById(providerId)

    suspend fun addCustomProvider(name: String, baseUrl: String): ProviderEntity {
        val entity = ProviderEntity(
            id = "custom_${UUID.randomUUID().toString().take(8)}",
            name = name.ifBlank { "自訂 OpenAI 端點" },
            type = ProviderType.CUSTOM.name,
            tier = 3,
            isEnabled = true,
            baseUrl = baseUrl.trim().ifBlank { "http://127.0.0.1:11434/v1" },
            allowEmptyKey = true,
            statusNote = "自訂 OpenAI 相容／本地 Ollama / LM Studio",
            sortOrder = 50,
        )
        providerDao.upsert(entity)
        return entity
    }

    suspend fun testProviderConnection(provider: ProviderEntity): Result<String> {
        val key = getApiKey(provider.id)
        if (key.isBlank() && !provider.allowEmptyKey) {
            val res = Result.failure<String>(IllegalStateException("請先輸入 API Key"))
            providerDao.upsert(provider.copy(lastTestedAt = System.currentTimeMillis(), lastTestSuccess = false))
            return res
        }
        val params = resolveEffectiveParameters(providerId = provider.id)
        val res = networkClient.testConnection(provider, key, params, getProxyCredentials())
        providerDao.upsert(
            provider.copy(
                lastTestedAt = System.currentTimeMillis(),
                lastTestSuccess = res.isSuccess,
            )
        )
        return res
    }

    suspend fun syncModelsForProvider(provider: ProviderEntity): Result<Int> {
        val key = getApiKey(provider.id)
        if (key.isBlank() && !provider.allowEmptyKey) {
            return Result.failure(IllegalStateException("尚未設定 API Key"))
        }
        val params = resolveEffectiveParameters(providerId = provider.id)
        val result = networkClient.fetchModels(provider, key, params, getProxyCredentials())
        return result.map { fetched ->
            val aliases = aliasDao.getAll()
            val existing = modelDao.getAll().filter { it.providerId == provider.id }.associateBy { it.modelId }
            val updated = fetched.map { info ->
                val prev = existing[info.modelId]
                val canonical = prev?.canonicalId?.takeIf { it.isNotBlank() }
                    ?: canonicalMapper.resolveCanonicalId(info.modelId, aliases)
                ModelEntity(
                    key = "${provider.id}::${info.modelId}",
                    providerId = provider.id,
                    modelId = info.modelId,
                    displayName = info.displayName,
                    canonicalId = canonical,
                    isSelected = prev?.isSelected ?: true,
                    isFree = info.isFree,
                    contextLength = info.contextLength ?: prev?.contextLength,
                    healthStatus = prev?.healthStatus ?: ModelHealthStatus.UNTESTED.name,
                    cooldownUntilMs = prev?.cooldownUntilMs ?: 0L,
                    lastError = prev?.lastError ?: "",
                    lastLatencyMs = prev?.lastLatencyMs,
                    lastTestedAt = prev?.lastTestedAt ?: 0L,
                )
            }
            if (updated.isNotEmpty()) {
                modelDao.upsertAll(updated)
            }
            updated.size
        }
    }

    suspend fun addManualModel(providerId: String, modelId: String): ModelEntity {
        val trimmed = modelId.trim()
        val aliases = aliasDao.getAll()
        val canonical = canonicalMapper.resolveCanonicalId(trimmed, aliases)
        val entity = ModelEntity(
            key = "$providerId::$trimmed",
            providerId = providerId,
            modelId = trimmed,
            displayName = trimmed,
            canonicalId = canonical,
            isSelected = true,
            isFree = true,
        )
        modelDao.upsert(entity)
        return entity
    }

    suspend fun toggleModelSelected(modelKey: String, selected: Boolean) {
        val model = modelDao.getByKey(modelKey) ?: return
        modelDao.upsert(model.copy(isSelected = selected))
    }

    suspend fun deleteModel(modelKey: String) = modelDao.deleteByKey(modelKey)

    suspend fun updateModelHealth(
        modelKey: String,
        status: ModelHealthStatus,
        cooldownUntilMs: Long = 0L,
        error: String = "",
        latencyMs: Long? = null,
    ) {
        val model = modelDao.getByKey(modelKey) ?: return
        modelDao.upsert(
            model.copy(
                healthStatus = status.name,
                cooldownUntilMs = cooldownUntilMs,
                lastError = error,
                lastLatencyMs = latencyMs ?: model.lastLatencyMs,
                lastTestedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun upsertCanonicalAlias(alias: CanonicalAliasEntity) {
        aliasDao.upsert(alias)
        // Re-evaluate canonical IDs for matching models
        val aliases = aliasDao.getAll()
        val updated = modelDao.getAll().map { m ->
            m.copy(canonicalId = canonicalMapper.resolveCanonicalId(m.modelId, aliases))
        }
        modelDao.upsertAll(updated)
    }

    suspend fun deleteCanonicalAlias(id: String) = aliasDao.deleteById(id)

    suspend fun bindModelCanonicalId(modelKey: String, canonicalId: String) {
        val model = modelDao.getByKey(modelKey) ?: return
        modelDao.upsert(model.copy(canonicalId = canonicalId.trim()))
    }

    suspend fun createConversation(
        mode: ConversationMode = ConversationMode.SINGLE,
        title: String = "新對話",
        activeModelKey: String? = null,
        parallelModelKeys: List<String> = emptyList(),
    ): ConversationEntity {
        val now = System.currentTimeMillis()
        val conv = ConversationEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            mode = mode.name,
            activeModelKey = activeModelKey,
            parallelModelKeysJson = JSONArray(parallelModelKeys).toString(),
            parallelMergeStrategy = ParallelMergeStrategy.CONVERGE.name,
            createdAt = now,
            updatedAt = now,
        )
        conversationDao.upsert(conv)
        return conv
    }

    suspend fun updateConversation(conv: ConversationEntity) =
        conversationDao.upsert(conv.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteConversation(id: String) = conversationDao.deleteById(id)

    suspend fun saveRoundtableParticipants(conversationId: String, list: List<RoundtableParticipantEntity>) {
        roundtableDao.deleteForConversation(conversationId)
        roundtableDao.upsertAll(list.mapIndexed { index, p -> p.copy(conversationId = conversationId, sortOrder = index) })
    }

    suspend fun getRoundtableParticipants(conversationId: String) = roundtableDao.getParticipants(conversationId)

    suspend fun selectMessageVersion(turnId: String, branchKey: String, versionId: String) {
        messageDao.deactivateBranchVersions(turnId, branchKey)
        messageDao.activateVersion(versionId)
    }

    suspend fun selectParallelConvergeBranch(turnId: String, branchKey: String) {
        val turn = messageDao.getTurnById(turnId) ?: return
        messageDao.upsertTurn(turn.copy(selectedBranchKey = branchKey))
    }

    suspend fun editVersionContentLocally(versionId: String, newContent: String) {
        val version = messageDao.getVersionById(versionId) ?: return
        messageDao.updateVersion(version.copy(content = newContent))
    }

    suspend fun deleteTurn(turnId: String) = messageDao.deleteTurnById(turnId)

    suspend fun deleteTurnAndFollowing(conversationId: String, orderIndex: Int) {
        val turns = messageDao.getTurns(conversationId)
        turns.filter { it.orderIndex >= orderIndex }.forEach { messageDao.deleteTurnById(it.id) }
    }

    suspend fun forkConversationFromTurn(sourceConversationId: String, upToTurnId: String): ConversationEntity? {
        val source = conversationDao.getById(sourceConversationId) ?: return null
        val turns = messageDao.getTurns(sourceConversationId)
        val versions = messageDao.getVersionsForConversation(sourceConversationId).groupBy { it.turnId }
        val targetOrder = turns.firstOrNull { it.id == upToTurnId }?.orderIndex ?: return null

        val now = System.currentTimeMillis()
        val forkedConv = source.copy(
            id = UUID.randomUUID().toString(),
            title = "${source.title} (分岔)",
            createdAt = now,
            updatedAt = now,
        )
        conversationDao.upsert(forkedConv)

        val oldParticipants = roundtableDao.getParticipants(sourceConversationId)
        if (oldParticipants.isNotEmpty()) {
            roundtableDao.upsertAll(
                oldParticipants.map {
                    it.copy(id = UUID.randomUUID().toString(), conversationId = forkedConv.id)
                }
            )
        }

        turns.filter { it.orderIndex <= targetOrder }.forEach { turn ->
            val newTurnId = UUID.randomUUID().toString()
            messageDao.upsertTurn(turn.copy(id = newTurnId, conversationId = forkedConv.id))
            versions[turn.id].orEmpty().filter { it.isActiveVersion }.forEach { ver ->
                messageDao.upsertVersion(ver.copy(id = UUID.randomUUID().toString(), turnId = newTurnId))
            }
        }
        return forkedConv
    }

    suspend fun resolveEffectiveParameters(
        conversationId: String? = null,
        providerId: String? = null,
        modelKey: String? = null,
    ): GenerationParameters {
        val globalPreset = presetDao.getByScopeAndTarget("GLOBAL", "")
        val globalParams = GenerationParameters.fromJsonString(globalPreset?.paramsJson)

        val conv = conversationId?.let { conversationDao.getById(it) }
        val activePreset = conv?.activePresetId?.let { presetDao.getById(it) }
        val presetParams = GenerationParameters.fromJsonString(activePreset?.paramsJson)

        val providerPreset = providerId?.let { presetDao.getByScopeAndTarget("PROVIDER", it) }
        val providerParams = GenerationParameters.fromJsonString(providerPreset?.paramsJson)

        val modelPreset = modelKey?.let { presetDao.getByScopeAndTarget("MODEL", it) }
        val modelParams = GenerationParameters.fromJsonString(modelPreset?.paramsJson)

        val convParams = GenerationParameters.fromJsonString(conv?.parameterOverrideJson)

        return ParameterMerger.merge(globalParams, presetParams, providerParams, modelParams, convParams)
    }

    suspend fun saveGlobalParameters(params: GenerationParameters) {
        presetDao.upsert(
            ParameterPresetEntity(
                id = "global_default",
                name = "全域預設",
                scope = "GLOBAL",
                targetKey = "",
                paramsJson = params.toJsonString(),
                isBuiltIn = true,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun saveConversationParameters(conversationId: String, params: GenerationParameters) {
        val conv = conversationDao.getById(conversationId) ?: return
        conversationDao.upsert(
            conv.copy(
                parameterOverrideJson = params.toJsonString(),
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun saveCustomPreset(name: String, params: GenerationParameters) {
        presetDao.upsert(
            ParameterPresetEntity(
                id = "preset_${UUID.randomUUID().toString().take(8)}",
                name = name.trim(),
                scope = "PRESET",
                targetKey = "",
                paramsJson = params.toJsonString(),
                isBuiltIn = false,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun deletePreset(id: String) = presetDao.deleteById(id)

    suspend fun upsertPromptTemplate(template: PromptTemplateEntity) = templateDao.upsert(template)
    suspend fun deletePromptTemplate(id: String) = templateDao.deleteById(id)

    suspend fun searchMessages(query: String, includeReasoning: Boolean): List<SearchMessageResult> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return messageDao.searchMessages(q, includeReasoning)
    }

    suspend fun getAllModelsSnapshot() = modelDao.getAll()
    suspend fun getAllProvidersSnapshot() = providerDao.getAll()
    suspend fun getAllAliasesSnapshot() = aliasDao.getAll()
    suspend fun getTurnsSnapshot(conversationId: String) = messageDao.getTurns(conversationId)
    suspend fun getVersionsSnapshot(conversationId: String) = messageDao.getVersionsForConversation(conversationId)
    suspend fun getConversationSnapshot(conversationId: String) = conversationDao.getById(conversationId)

    suspend fun addUserTurn(conversationId: String, content: String, role: MessageRole = MessageRole.USER): Pair<MessageTurnEntity, MessageVersionEntity> {
        val turns = messageDao.getTurns(conversationId)
        val nextOrder = (turns.maxOfOrNull { it.orderIndex } ?: -1) + 1
        val turn = MessageTurnEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            orderIndex = nextOrder,
            role = role.name,
        )
        val ver = MessageVersionEntity(
            id = UUID.randomUUID().toString(),
            turnId = turn.id,
            branchKey = "default",
            versionIndex = 0,
            isActiveVersion = true,
            content = content,
            status = MessageStatus.COMPLETED.name,
        )
        messageDao.upsertTurn(turn)
        messageDao.upsertVersion(ver)
        conversationDao.getById(conversationId)?.let {
            conversationDao.upsert(it.copy(updatedAt = System.currentTimeMillis()))
        }
        return turn to ver
    }

    suspend fun createAssistantTurnIfMissing(conversationId: String, orderIndex: Int? = null): MessageTurnEntity {
        val turns = messageDao.getTurns(conversationId)
        val nextOrder = orderIndex ?: ((turns.maxOfOrNull { it.orderIndex } ?: -1) + 1)
        val turn = MessageTurnEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            orderIndex = nextOrder,
            role = MessageRole.ASSISTANT.name,
        )
        messageDao.upsertTurn(turn)
        return turn
    }

    suspend fun createNewVersionForTurn(
        turnId: String,
        branchKey: String,
        provider: ProviderEntity,
        model: ModelEntity,
        participantName: String = "",
        paramSnapshotJson: String = "",
        isFailover: Boolean = false,
        failoverFromLabel: String = "",
    ): MessageVersionEntity {
        val existing = messageDao.getVersionsForTurnAndBranch(turnId, branchKey)
        val nextIdx = (existing.maxOfOrNull { it.versionIndex } ?: -1) + 1
        messageDao.deactivateBranchVersions(turnId, branchKey)
        val ver = MessageVersionEntity(
            id = UUID.randomUUID().toString(),
            turnId = turnId,
            branchKey = branchKey,
            versionIndex = nextIdx,
            isActiveVersion = true,
            providerId = provider.id,
            providerName = provider.name,
            modelId = model.modelId,
            participantName = participantName,
            isFailover = isFailover,
            failoverFromLabel = failoverFromLabel,
            status = MessageStatus.STREAMING.name,
            paramSnapshotJson = paramSnapshotJson,
        )
        messageDao.upsertVersion(ver)
        return ver
    }

    suspend fun updateVersionDirect(version: MessageVersionEntity) = messageDao.upsertVersion(version)

    suspend fun getParameterPresetsSnapshot(): List<ParameterPresetEntity> =
        listOfNotNull(presetDao.getByScopeAndTarget("GLOBAL", ""))
}
