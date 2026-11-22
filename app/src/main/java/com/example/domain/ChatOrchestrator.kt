package com.example.domain

import com.example.data.local.ConversationEntity
import com.example.data.local.ConversationMode
import com.example.data.local.MessageRole
import com.example.data.local.MessageStatus
import com.example.data.local.MessageTurnEntity
import com.example.data.local.MessageVersionEntity
import com.example.data.local.ModelEntity
import com.example.data.local.ModelHealthStatus
import com.example.data.local.ParallelMergeStrategy
import com.example.data.local.ProviderEntity
import com.example.data.local.RoundtableParticipantEntity
import com.example.data.model.GenerationParameters
import com.example.data.repository.WorkstationRepository
import com.example.network.AiNetworkClient
import com.example.network.ChatMessagePayload
import com.example.network.StreamEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface FailoverPrompt {
    data class SameModelAvailable(
        val conversationId: String,
        val turnId: String,
        val frozenVersionId: String,
        val branchKey: String,
        val failedProviderName: String,
        val failedModelId: String,
        val retryAfterSeconds: Long,
        val fallbackProvider: ProviderEntity,
        val fallbackModel: ModelEntity,
        val errorMessage: String,
    ) : FailoverPrompt

    data class NoSameModelFallback(
        val conversationId: String,
        val turnId: String,
        val frozenVersionId: String,
        val branchKey: String,
        val failedProviderName: String,
        val failedModelId: String,
        val retryAfterSeconds: Long,
        val errorMessage: String,
    ) : FailoverPrompt
}

data class RoundtableRuntimeState(
    val conversationId: String = "",
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val currentRound: Int = 0,
    val maxRounds: Int = 6,
    val currentSpeakerName: String = "",
    val nextSpeakerIndex: Int = 0,
)

class ChatOrchestrator(
    private val scope: CoroutineScope,
    private val repo: WorkstationRepository,
    private val networkClient: AiNetworkClient,
    private val canonicalMapper: CanonicalModelMapper,
) {
    private val _activeStreamingConvIds = MutableStateFlow<Set<String>>(emptySet())
    val activeStreamingConvIds: StateFlow<Set<String>> = _activeStreamingConvIds.asStateFlow()

    private val _failoverPrompt = MutableStateFlow<FailoverPrompt?>(null)
    val failoverPrompt: StateFlow<FailoverPrompt?> = _failoverPrompt.asStateFlow()

    private val _roundtableState = MutableStateFlow(RoundtableRuntimeState())
    val roundtableState: StateFlow<RoundtableRuntimeState> = _roundtableState.asStateFlow()

    private val activeJobs = mutableMapOf<String, Job>()
    private val pendingInterjections = mutableMapOf<String, String>()

    fun dismissFailoverPrompt() {
        _failoverPrompt.value = null
    }

    fun stopGeneration(conversationId: String) {
        activeJobs[conversationId]?.cancel()
        activeJobs.remove(conversationId)
        _activeStreamingConvIds.value = _activeStreamingConvIds.value - conversationId
        if (_roundtableState.value.conversationId == conversationId) {
            _roundtableState.value = _roundtableState.value.copy(isRunning = false, isPaused = false)
        }
    }

    fun sendUserMessage(conversationId: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        stopGeneration(conversationId)
        activeJobs[conversationId] = scope.launch {
            try {
                _activeStreamingConvIds.value = _activeStreamingConvIds.value + conversationId
                val conv = repo.getConversationSnapshot(conversationId) ?: return@launch
                repo.addUserTurn(conversationId, trimmed, MessageRole.USER)
                maybeAutoGenerateTitle(conv, trimmed)

                when (conv.conversationMode()) {
                    ConversationMode.SINGLE -> {
                        val modelKey = conv.activeModelKey ?: return@launch
                        val assistantTurn = repo.createAssistantTurnIfMissing(conversationId)
                        executeSingleCompletion(
                            conv = conv,
                            turnId = assistantTurn.id,
                            branchKey = "default",
                            modelKey = modelKey,
                        )
                    }
                    ConversationMode.PARALLEL -> {
                        val keys = parseJsonStringList(conv.parallelModelKeysJson)
                        if (keys.isEmpty()) return@launch
                        val assistantTurn = repo.createAssistantTurnIfMissing(conversationId)
                        coroutineScope {
                            keys.map { key ->
                                async {
                                    executeSingleCompletion(
                                        conv = conv,
                                        turnId = assistantTurn.id,
                                        branchKey = key,
                                        modelKey = key,
                                    )
                                }
                            }.awaitAll()
                        }
                    }
                    ConversationMode.ROUNDTABLE -> {
                        runRoundtableLoop(conversationId, singleStepOnly = false)
                    }
                }
            } finally {
                _activeStreamingConvIds.value = _activeStreamingConvIds.value - conversationId
            }
        }
    }

    fun regenerateVersion(
        conversationId: String,
        turnId: String,
        branchKey: String,
        overrideModelKey: String? = null,
    ) {
        stopGeneration(conversationId)
        activeJobs[conversationId] = scope.launch {
            try {
                _activeStreamingConvIds.value = _activeStreamingConvIds.value + conversationId
                val conv = repo.getConversationSnapshot(conversationId) ?: return@launch
                val existingVersions = repo.getVersionsSnapshot(conversationId)
                    .filter { it.turnId == turnId && it.branchKey == branchKey }
                val active = existingVersions.firstOrNull { it.isActiveVersion } ?: existingVersions.lastOrNull()
                val targetModelKey = overrideModelKey
                    ?: active?.let { "${it.providerId}::${it.modelId}" }
                    ?: conv.activeModelKey
                    ?: return@launch

                executeSingleCompletion(
                    conv = conv,
                    turnId = turnId,
                    branchKey = branchKey,
                    modelKey = targetModelKey,
                    maxOrderExclusive = repo.getTurnsSnapshot(conversationId)
                        .firstOrNull { it.id == turnId }?.orderIndex,
                )
            } finally {
                _activeStreamingConvIds.value = _activeStreamingConvIds.value - conversationId
            }
        }
    }

    fun editUserTurnAndRegenerate(conversationId: String, turnId: String, newText: String) {
        stopGeneration(conversationId)
        activeJobs[conversationId] = scope.launch {
            try {
                _activeStreamingConvIds.value = _activeStreamingConvIds.value + conversationId
                val conv = repo.getConversationSnapshot(conversationId) ?: return@launch
                val turns = repo.getTurnsSnapshot(conversationId)
                val targetTurn = turns.firstOrNull { it.id == turnId } ?: return@launch
                val userVersion = repo.getVersionsSnapshot(conversationId)
                    .firstOrNull { it.turnId == turnId && it.isActiveVersion } ?: return@launch

                repo.editVersionContentLocally(userVersion.id, newText.trim())
                // Remove subsequent turns so history stays coherent
                turns.filter { it.orderIndex > targetTurn.orderIndex }.forEach {
                    repo.deleteTurn(it.id)
                }

                when (conv.conversationMode()) {
                    ConversationMode.SINGLE -> {
                        val modelKey = conv.activeModelKey ?: return@launch
                        val assistantTurn = repo.createAssistantTurnIfMissing(conversationId)
                        executeSingleCompletion(conv, assistantTurn.id, "default", modelKey)
                    }
                    ConversationMode.PARALLEL -> {
                        val keys = parseJsonStringList(conv.parallelModelKeysJson)
                        if (keys.isEmpty()) return@launch
                        val assistantTurn = repo.createAssistantTurnIfMissing(conversationId)
                        coroutineScope {
                            keys.map { key ->
                                async { executeSingleCompletion(conv, assistantTurn.id, key, key) }
                            }.awaitAll()
                        }
                    }
                    ConversationMode.ROUNDTABLE -> {
                        _roundtableState.value = RoundtableRuntimeState(conversationId = conversationId)
                        runRoundtableLoop(conversationId, singleStepOnly = false)
                    }
                }
            } finally {
                _activeStreamingConvIds.value = _activeStreamingConvIds.value - conversationId
            }
        }
    }

    fun confirmFailoverSwitch(prompt: FailoverPrompt.SameModelAvailable) {
        _failoverPrompt.value = null
        activeJobs[prompt.conversationId] = scope.launch {
            try {
                _activeStreamingConvIds.value = _activeStreamingConvIds.value + prompt.conversationId
                val conv = repo.getConversationSnapshot(prompt.conversationId) ?: return@launch
                val turns = repo.getTurnsSnapshot(prompt.conversationId)
                val orderIndex = turns.firstOrNull { it.id == prompt.turnId }?.orderIndex

                executeSingleCompletion(
                    conv = conv,
                    turnId = prompt.turnId,
                    branchKey = prompt.branchKey,
                    modelKey = prompt.fallbackModel.key,
                    maxOrderExclusive = orderIndex,
                    isFailover = true,
                    failoverFromLabel = "${prompt.failedProviderName} · ${prompt.failedModelId}",
                )
            } finally {
                _activeStreamingConvIds.value = _activeStreamingConvIds.value - prompt.conversationId
            }
        }
    }

    fun resumeWithAlternativeModel(
        conversationId: String,
        turnId: String,
        branchKey: String,
        newModelKey: String,
        failedLabel: String,
    ) {
        _failoverPrompt.value = null
        activeJobs[conversationId] = scope.launch {
            try {
                _activeStreamingConvIds.value = _activeStreamingConvIds.value + conversationId
                val conv = repo.getConversationSnapshot(conversationId) ?: return@launch
                val turns = repo.getTurnsSnapshot(conversationId)
                val orderIndex = turns.firstOrNull { it.id == turnId }?.orderIndex
                executeSingleCompletion(
                    conv = conv,
                    turnId = turnId,
                    branchKey = branchKey,
                    modelKey = newModelKey,
                    maxOrderExclusive = orderIndex,
                    isFailover = true,
                    failoverFromLabel = failedLabel,
                )
            } finally {
                _activeStreamingConvIds.value = _activeStreamingConvIds.value - conversationId
            }
        }
    }

    // Roundtable Controls
    fun startOrResumeRoundtable(conversationId: String) {
        stopGeneration(conversationId)
        activeJobs[conversationId] = scope.launch {
            try {
                _activeStreamingConvIds.value = _activeStreamingConvIds.value + conversationId
                runRoundtableLoop(conversationId, singleStepOnly = false)
            } finally {
                _activeStreamingConvIds.value = _activeStreamingConvIds.value - conversationId
            }
        }
    }

    fun pauseRoundtable(conversationId: String) {
        activeJobs[conversationId]?.cancel()
        activeJobs.remove(conversationId)
        _activeStreamingConvIds.value = _activeStreamingConvIds.value - conversationId
        val cur = _roundtableState.value
        if (cur.conversationId == conversationId) {
            _roundtableState.value = cur.copy(isRunning = false, isPaused = true)
        }
    }

    fun stepNextRoundtableTurn(conversationId: String) {
        stopGeneration(conversationId)
        activeJobs[conversationId] = scope.launch {
            try {
                _activeStreamingConvIds.value = _activeStreamingConvIds.value + conversationId
                runRoundtableLoop(conversationId, singleStepOnly = true)
            } finally {
                _activeStreamingConvIds.value = _activeStreamingConvIds.value - conversationId
            }
        }
    }

    fun injectRoundtableUserMessage(conversationId: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val wasRunning = _roundtableState.value.let { it.conversationId == conversationId && it.isRunning }
        if (wasRunning) {
            pendingInterjections[conversationId] = trimmed
        } else {
            scope.launch {
                repo.addUserTurn(conversationId, trimmed, MessageRole.SYSTEM_INTERJECTION)
            }
        }
    }

    private suspend fun runRoundtableLoop(conversationId: String, singleStepOnly: Boolean) {
        val conv = repo.getConversationSnapshot(conversationId) ?: return
        val participants = repo.getRoundtableParticipants(conversationId)
        if (participants.isEmpty()) return

        val maxTurns = conv.roundtableMaxTurns.coerceIn(1, 30)
        val delayMs = conv.roundtableDelayMs.coerceIn(0L, 10_000L)

        val existingAiTurns = repo.getTurnsSnapshot(conversationId)
            .count { it.messageRole() == MessageRole.ASSISTANT }
        var round = existingAiTurns
        var speakerIdx = if (_roundtableState.value.conversationId == conversationId) {
            _roundtableState.value.nextSpeakerIndex % participants.size
        } else {
            existingAiTurns % participants.size
        }

        while (round < maxTurns) {
            // Drain any queued live user interjection before next speaker starts
            pendingInterjections.remove(conversationId)?.let { interjection ->
                repo.addUserTurn(conversationId, interjection, MessageRole.SYSTEM_INTERJECTION)
            }

            val speaker = participants[speakerIdx]
            round++
            _roundtableState.value = RoundtableRuntimeState(
                conversationId = conversationId,
                isRunning = true,
                isPaused = false,
                currentRound = round,
                maxRounds = maxTurns,
                currentSpeakerName = speaker.displayName,
                nextSpeakerIndex = (speakerIdx + 1) % participants.size,
            )

            val turn = repo.createAssistantTurnIfMissing(conversationId)
            val ok = executeRoundtableTurn(conv, turn.id, speaker, participants)
            if (!ok) {
                _roundtableState.value = _roundtableState.value.copy(isRunning = false, isPaused = true)
                return
            }

            speakerIdx = (speakerIdx + 1) % participants.size
            if (singleStepOnly || round >= maxTurns) {
                _roundtableState.value = _roundtableState.value.copy(
                    isRunning = false,
                    isPaused = round < maxTurns,
                    nextSpeakerIndex = speakerIdx,
                )
                return
            }

            if (delayMs > 0) delay(delayMs)
        }
    }

    private suspend fun executeRoundtableTurn(
        conv: ConversationEntity,
        turnId: String,
        speaker: RoundtableParticipantEntity,
        allParticipants: List<RoundtableParticipantEntity>,
    ): Boolean {
        val model = repo.getAllModelsSnapshot().firstOrNull { it.key == speaker.modelKey } ?: return false
        val provider = repo.getAllProvidersSnapshot().firstOrNull { it.id == model.providerId } ?: return false
        val apiKey = repo.getApiKey(provider.id)
        if (apiKey.isBlank() && !provider.allowEmptyKey) {
            val ver = repo.createNewVersionForTurn(turnId, "default", provider, model, speaker.displayName)
            repo.updateVersionDirect(
                ver.copy(
                    status = MessageStatus.ERROR.name,
                    errorMessage = "${provider.name} 尚未設定 API Key",
                )
            )
            return false
        }

        val params = repo.resolveEffectiveParameters(conv.id, provider.id, model.key)
        val turns = repo.getTurnsSnapshot(conv.id).filter { it.id != turnId }
        val versionsByTurn = repo.getVersionsSnapshot(conv.id).groupBy { it.turnId }

        // Role Wrapping:
        // Current speaker's own previous messages stay `assistant`.
        // Other participants' messages become `user` prefixed with `[SpeakerName]: `.
        val payloadMessages = mutableListOf<ChatMessagePayload>()
        val personaPrompt = buildString {
            val baseSys = interpolateVariables(params.systemPrompt.orEmpty(), model.modelId)
            if (baseSys.isNotBlank()) appendLine(baseSys)
            if (speaker.systemPrompt.isNotBlank()) {
                appendLine(interpolateVariables(speaker.systemPrompt, model.modelId))
            }
            append("你在圓桌討論中的名稱是「${speaker.displayName}」。其他參與者：${allParticipants.joinToString("、") { it.displayName }}。請直接以你的觀點接續發言。")
        }.trim()

        val sysRole = params.systemRoleName?.takeIf { it.isNotBlank() } ?: "system"
        payloadMessages.add(ChatMessagePayload(sysRole, personaPrompt))

        turns.forEach { t ->
            val ver = versionsByTurn[t.id]?.firstOrNull { it.isActiveVersion } ?: return@forEach
            if (ver.content.isBlank()) return@forEach
            when (t.messageRole()) {
                MessageRole.USER -> payloadMessages.add(ChatMessagePayload("user", ver.content))
                MessageRole.SYSTEM_INTERJECTION -> payloadMessages.add(
                    ChatMessagePayload("user", "[主持人引導]: ${ver.content}")
                )
                MessageRole.ASSISTANT -> {
                    val isSelf = ver.participantName == speaker.displayName &&
                        "${ver.providerId}::${ver.modelId}" == speaker.modelKey
                    if (isSelf) {
                        payloadMessages.add(ChatMessagePayload("assistant", ver.content))
                    } else {
                        val name = ver.participantName.ifBlank { ver.modelId }
                        payloadMessages.add(ChatMessagePayload("user", "[$name]: ${ver.content}"))
                    }
                }
            }
        }

        val truncated = applyContextTruncation(payloadMessages, params)
        return streamIntoVersion(
            conv = conv,
            turnId = turnId,
            branchKey = "default",
            provider = provider,
            model = model,
            apiKey = apiKey,
            messages = truncated,
            params = params,
            participantName = speaker.displayName,
        )
    }

    private suspend fun executeSingleCompletion(
        conv: ConversationEntity,
        turnId: String,
        branchKey: String,
        modelKey: String,
        maxOrderExclusive: Int? = null,
        isFailover: Boolean = false,
        failoverFromLabel: String = "",
    ): Boolean {
        val model = repo.getAllModelsSnapshot().firstOrNull { it.key == modelKey } ?: return false
        val provider = repo.getAllProvidersSnapshot().firstOrNull { it.id == model.providerId } ?: return false
        val apiKey = repo.getApiKey(provider.id)
        if (apiKey.isBlank() && !provider.allowEmptyKey) {
            val ver = repo.createNewVersionForTurn(
                turnId = turnId,
                branchKey = branchKey,
                provider = provider,
                model = model,
                isFailover = isFailover,
                failoverFromLabel = failoverFromLabel,
            )
            repo.updateVersionDirect(
                ver.copy(
                    status = MessageStatus.ERROR.name,
                    errorMessage = "${provider.name} 尚未設定 API Key，請前往設定頁填入。",
                )
            )
            return false
        }

        val params = repo.resolveEffectiveParameters(conv.id, provider.id, model.key)
        val messages = buildStandardHistory(conv, branchKey, model.modelId, params, maxOrderExclusive)

        return streamIntoVersion(
            conv = conv,
            turnId = turnId,
            branchKey = branchKey,
            provider = provider,
            model = model,
            apiKey = apiKey,
            messages = messages,
            params = params,
            isFailover = isFailover,
            failoverFromLabel = failoverFromLabel,
        )
    }

    private suspend fun streamIntoVersion(
        conv: ConversationEntity,
        turnId: String,
        branchKey: String,
        provider: ProviderEntity,
        model: ModelEntity,
        apiKey: String,
        messages: List<ChatMessagePayload>,
        params: GenerationParameters,
        participantName: String = "",
        isFailover: Boolean = false,
        failoverFromLabel: String = "",
    ): Boolean {
        var ver = repo.createNewVersionForTurn(
            turnId = turnId,
            branchKey = branchKey,
            provider = provider,
            model = model,
            participantName = participantName,
            paramSnapshotJson = params.toJsonString(),
            isFailover = isFailover,
            failoverFromLabel = failoverFromLabel,
        )

        val contentBuf = StringBuilder()
        val reasoningBuf = StringBuilder()
        val throttleMs = (params.streamRenderThrottleMs ?: 45L).coerceIn(16L, 300L)
        var lastFlushAt = 0L
        var success = false

        try {
            networkClient.streamChat(
                provider = provider,
                modelId = model.modelId,
                apiKey = apiKey,
                messages = messages,
                params = params,
                proxyCredentials = repo.getProxyCredentials(),
            ) { event ->
                when (event) {
                    is StreamEvent.Delta -> {
                        contentBuf.append(event.contentDelta)
                        reasoningBuf.append(event.reasoningDelta)
                        val now = System.currentTimeMillis()
                        if (now - lastFlushAt >= throttleMs) {
                            lastFlushAt = now
                            ver = ver.copy(
                                content = contentBuf.toString(),
                                reasoningContent = if (params.hideThinkingOutput == true) "" else reasoningBuf.toString(),
                                ttftMs = event.ttftMs ?: ver.ttftMs,
                            )
                            repo.updateVersionDirect(ver)
                        }
                    }

                    is StreamEvent.Completed -> {
                        success = true
                        ver = ver.copy(
                            content = event.fullContent.ifEmpty { contentBuf.toString() },
                            reasoningContent = if (params.hideThinkingOutput == true) "" else event.fullReasoning.ifEmpty { reasoningBuf.toString() },
                            status = MessageStatus.COMPLETED.name,
                            promptTokens = event.promptTokens,
                            completionTokens = event.completionTokens,
                            ttftMs = event.ttftMs,
                            tps = event.tps,
                            rateLimitInfoJson = event.rateLimitInfoJson,
                            debugRequestJson = event.debugRequestJson,
                            debugResponseHeadersJson = event.debugResponseHeadersJson,
                        )
                        repo.updateVersionDirect(ver)
                        repo.updateModelHealth(model.key, ModelHealthStatus.AVAILABLE, 0L, "", event.ttftMs)
                    }

                    is StreamEvent.QuotaExceeded -> {
                        success = false
                        val frozenContent = event.partialContent.ifEmpty { contentBuf.toString() }
                        val frozenReasoning = if (params.hideThinkingOutput == true) "" else event.partialReasoning.ifEmpty { reasoningBuf.toString() }
                        ver = ver.copy(
                            content = frozenContent,
                            reasoningContent = frozenReasoning,
                            status = MessageStatus.FROZEN_QUOTA.name,
                            errorMessage = event.errorMessage,
                            rateLimitInfoJson = event.rateLimitInfoJson,
                            debugRequestJson = event.debugRequestJson,
                            debugResponseHeadersJson = event.debugResponseHeadersJson,
                        )
                        repo.updateVersionDirect(ver)
                        val cooldownUntil = System.currentTimeMillis() + event.retryAfterSeconds * 1000L
                        repo.updateModelHealth(model.key, ModelHealthStatus.COOLDOWN, cooldownUntil, event.errorMessage)

                        handleQuotaFailover(
                            conv = conv,
                            turnId = turnId,
                            frozenVersionId = ver.id,
                            branchKey = branchKey,
                            failedProvider = provider,
                            failedModel = model,
                            retryAfterSeconds = event.retryAfterSeconds,
                            errorMessage = event.errorMessage,
                        )
                    }

                    is StreamEvent.Failed -> {
                        success = false
                        ver = ver.copy(
                            content = event.partialContent.ifEmpty { contentBuf.toString() },
                            reasoningContent = if (params.hideThinkingOutput == true) "" else event.partialReasoning.ifEmpty { reasoningBuf.toString() },
                            status = MessageStatus.ERROR.name,
                            errorMessage = event.errorMessage,
                            debugRequestJson = event.debugRequestJson,
                            debugResponseHeadersJson = event.debugResponseHeadersJson,
                        )
                        repo.updateVersionDirect(ver)
                        if (event.httpCode in listOf(401, 403, 404)) {
                            repo.updateModelHealth(model.key, ModelHealthStatus.UNAVAILABLE, 0L, event.errorMessage)
                        }
                    }
                }
            }
        } catch (ce: CancellationException) {
            ver = ver.copy(
                content = contentBuf.toString(),
                reasoningContent = if (params.hideThinkingOutput == true) "" else reasoningBuf.toString(),
                status = MessageStatus.CANCELLED.name,
            )
            repo.updateVersionDirect(ver)
            throw ce
        }

        return success
    }

    private suspend fun handleQuotaFailover(
        conv: ConversationEntity,
        turnId: String,
        frozenVersionId: String,
        branchKey: String,
        failedProvider: ProviderEntity,
        failedModel: ModelEntity,
        retryAfterSeconds: Long,
        errorMessage: String,
    ) {
        val allModels = repo.getAllModelsSnapshot()
        val allProviders = repo.getAllProvidersSnapshot()
        val aliases = repo.getAllAliasesSnapshot()
        val fallbacks = canonicalMapper.findSameModelFallbacks(failedModel, allModels, allProviders, aliases)
        val prefs = repo.preferencesFlow.first()

        if (fallbacks.isNotEmpty()) {
            val (fbProvider, fbModel) = fallbacks.first()
            if (prefs.silentAutoFailover) {
                val turns = repo.getTurnsSnapshot(conv.id)
                val orderIndex = turns.firstOrNull { it.id == turnId }?.orderIndex
                executeSingleCompletion(
                    conv = conv,
                    turnId = turnId,
                    branchKey = branchKey,
                    modelKey = fbModel.key,
                    maxOrderExclusive = orderIndex,
                    isFailover = true,
                    failoverFromLabel = "${failedProvider.name} · ${failedModel.modelId}",
                )
            } else {
                _failoverPrompt.value = FailoverPrompt.SameModelAvailable(
                    conversationId = conv.id,
                    turnId = turnId,
                    frozenVersionId = frozenVersionId,
                    branchKey = branchKey,
                    failedProviderName = failedProvider.name,
                    failedModelId = failedModel.modelId,
                    retryAfterSeconds = retryAfterSeconds,
                    fallbackProvider = fbProvider,
                    fallbackModel = fbModel,
                    errorMessage = errorMessage,
                )
            }
        } else {
            _failoverPrompt.value = FailoverPrompt.NoSameModelFallback(
                conversationId = conv.id,
                turnId = turnId,
                frozenVersionId = frozenVersionId,
                branchKey = branchKey,
                failedProviderName = failedProvider.name,
                failedModelId = failedModel.modelId,
                retryAfterSeconds = retryAfterSeconds,
                errorMessage = errorMessage,
            )
        }
    }

    private suspend fun buildStandardHistory(
        conv: ConversationEntity,
        branchKey: String,
        modelId: String,
        params: GenerationParameters,
        maxOrderExclusive: Int? = null,
    ): List<ChatMessagePayload> {
        val list = mutableListOf<ChatMessagePayload>()
        val sysPrompt = params.systemPrompt?.takeIf { it.isNotBlank() }?.let {
            interpolateVariables(it, modelId)
        }
        if (!sysPrompt.isNullOrBlank()) {
            val sysRole = params.systemRoleName?.takeIf { it.isNotBlank() } ?: "system"
            list.add(ChatMessagePayload(sysRole, sysPrompt))
        }

        val turns = repo.getTurnsSnapshot(conv.id)
            .filter { maxOrderExclusive == null || it.orderIndex < maxOrderExclusive }
        val versionsByTurn = repo.getVersionsSnapshot(conv.id).groupBy { it.turnId }
        val includeThinking = params.includeThinkingInHistory == true

        turns.forEach { turn ->
            val turnVersions = versionsByTurn[turn.id].orEmpty()
            when (turn.messageRole()) {
                MessageRole.USER, MessageRole.SYSTEM_INTERJECTION -> {
                    val v = turnVersions.firstOrNull { it.isActiveVersion }
                    if (v != null && v.content.isNotBlank()) {
                        list.add(ChatMessagePayload("user", v.content))
                    }
                }
                MessageRole.ASSISTANT -> {
                    val chosen = when (conv.conversationMode()) {
                        ConversationMode.PARALLEL -> {
                            if (conv.mergeStrategy() == ParallelMergeStrategy.CONVERGE) {
                                val adoptedBranch = turn.selectedBranchKey ?: branchKey
                                turnVersions.firstOrNull { it.branchKey == adoptedBranch && it.isActiveVersion }
                                    ?: turnVersions.firstOrNull { it.isActiveVersion }
                            } else {
                                turnVersions.firstOrNull { it.branchKey == branchKey && it.isActiveVersion }
                            }
                        }
                        else -> turnVersions.firstOrNull { it.isActiveVersion }
                    }
                    if (chosen != null && chosen.content.isNotBlank()) {
                        val content = if (includeThinking && chosen.reasoningContent.isNotBlank()) {
                            "<think>\n${chosen.reasoningContent}\n</think>\n${chosen.content}"
                        } else {
                            chosen.content
                        }
                        list.add(ChatMessagePayload("assistant", content))
                    }
                }
            }
        }

        return applyContextTruncation(list, params)
    }

    private fun applyContextTruncation(
        messages: List<ChatMessagePayload>,
        params: GenerationParameters,
    ): List<ChatMessagePayload> {
        val mode = params.contextTruncationMode ?: return messages
        if (mode == "NONE") return messages

        val systemHead = messages.firstOrNull()?.takeIf { it.role == "system" || it.role == "developer" }
        val rest = if (systemHead != null) messages.drop(1) else messages

        val trimmed = when (mode) {
            "COUNT" -> {
                val maxMsg = (params.maxContextMessages ?: 20).coerceAtLeast(1)
                if (rest.size > maxMsg) rest.takeLast(maxMsg) else rest
            }
            "TOKENS" -> {
                val maxTokens = (params.maxContextTokens ?: 4096).coerceAtLeast(256)
                val sysCost = systemHead?.let { estimateTokens(it.content) } ?: 0
                var budget = (maxTokens - sysCost).coerceAtLeast(128)
                val kept = ArrayDeque<ChatMessagePayload>()
                for (m in rest.asReversed()) {
                    val cost = estimateTokens(m.content)
                    if (kept.isEmpty() || cost <= budget) {
                        kept.addFirst(m)
                        budget -= cost
                    } else {
                        break
                    }
                }
                kept.toList()
            }
            else -> rest
        }

        return if (systemHead != null) listOf(systemHead) + trimmed else trimmed
    }

    private fun estimateTokens(text: String): Int = (text.length / 3.2).toInt().coerceAtLeast(1)

    private fun interpolateVariables(template: String, modelId: String): String {
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return template
            .replace("{{date}}", dateStr)
            .replace("{{model}}", modelId)
    }

    private suspend fun maybeAutoGenerateTitle(conv: ConversationEntity, firstPrompt: String) {
        if (conv.title != "新對話" && conv.title.isNotBlank()) return
        val fallbackTitle = firstPrompt.replace("\n", " ").trim().take(24)
        val params = repo.resolveEffectiveParameters(conv.id)
        val titleModelKey = params.titleGeneratorModelKey?.takeIf { it.isNotBlank() }
        if (titleModelKey == null) {
            repo.updateConversation(conv.copy(title = fallbackTitle.ifBlank { "新對話" }))
            return
        }

        val model = repo.getAllModelsSnapshot().firstOrNull { it.key == titleModelKey }
        val provider = model?.let { m -> repo.getAllProvidersSnapshot().firstOrNull { it.id == m.providerId } }
        val apiKey = provider?.let { repo.getApiKey(it.id) }.orEmpty()
        if (model == null || provider == null || (apiKey.isBlank() && !provider.allowEmptyKey)) {
            repo.updateConversation(conv.copy(title = fallbackTitle.ifBlank { "新對話" }))
            return
        }

        scope.launch {
            var generated = ""
            networkClient.streamChat(
                provider = provider,
                modelId = model.modelId,
                apiKey = apiKey,
                messages = listOf(
                    ChatMessagePayload("system", "請用10個繁體中文字以內總結以下提問作為對話標題，不要加引號與標點。"),
                    ChatMessagePayload("user", firstPrompt.take(300)),
                ),
                params = GenerationParameters(stream = false, maxTokens = 24, temperature = 0.3),
                proxyCredentials = repo.getProxyCredentials(),
            ) { ev ->
                if (ev is StreamEvent.Completed) {
                    generated = ev.fullContent.trim().replace("\n", " ").take(24)
                }
            }
            val latest = repo.getConversationSnapshot(conv.id) ?: return@launch
            repo.updateConversation(latest.copy(title = generated.ifBlank { fallbackTitle }))
        }
    }

    private fun parseJsonStringList(json: String): List<String> = runCatching {
        val arr = JSONArray(json)
        List(arr.length()) { arr.optString(it) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())
}
