package com.example.domain

import com.example.data.local.ModelDao
import com.example.data.local.ModelEntity
import com.example.data.local.ModelHealthStatus
import com.example.data.local.ProviderDao
import com.example.data.security.SecureCredentialStore
import com.example.network.AiNetworkClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ProbeQueueState(
    val isRunning: Boolean = false,
    val totalCount: Int = 0,
    val completedCount: Int = 0,
    val currentModelName: String = "",
)

/**
 * Throttled serial probe queue to verify model availability (`max_tokens: 1`)
 * without triggering RPM rate limits, plus a 1s cooldown recovery ticker.
 */
class ModelProbeQueue(
    private val scope: CoroutineScope,
    private val modelDao: ModelDao,
    private val providerDao: ProviderDao,
    private val credentialStore: SecureCredentialStore,
    private val networkClient: AiNetworkClient,
) {
    private val _queueState = MutableStateFlow(ProbeQueueState())
    val queueState: StateFlow<ProbeQueueState> = _queueState.asStateFlow()

    private val _probingKeys = MutableStateFlow<Set<String>>(emptySet())
    val probingKeys: StateFlow<Set<String>> = _probingKeys.asStateFlow()

    private var batchJob: Job? = null

    init {
        // Periodic cooldown expiration check
        scope.launch {
            while (isActive) {
                delay(2000L)
                val now = System.currentTimeMillis()
                val models = modelDao.getAll()
                models.filter {
                    it.health() == ModelHealthStatus.COOLDOWN && it.cooldownUntilMs in 1..now
                }.forEach { expired ->
                    modelDao.upsert(
                        expired.copy(
                            healthStatus = ModelHealthStatus.UNTESTED.name,
                            cooldownUntilMs = 0L,
                            lastError = "",
                        )
                    )
                }
            }
        }
    }

    fun probeSingle(modelKey: String) {
        scope.launch {
            runProbeForKey(modelKey)
        }
    }

    fun probeBatch(modelKeys: List<String>) {
        if (modelKeys.isEmpty()) return
        batchJob?.cancel()
        batchJob = scope.launch {
            _queueState.value = ProbeQueueState(
                isRunning = true,
                totalCount = modelKeys.size,
                completedCount = 0,
            )
            modelKeys.forEachIndexed { idx, key ->
                if (!isActive) return@forEachIndexed
                val model = modelDao.getByKey(key)
                _queueState.value = _queueState.value.copy(
                    currentModelName = model?.displayName ?: key,
                    completedCount = idx,
                )
                runProbeForKey(key)
                if (idx < modelKeys.lastIndex) {
                    delay(450L) // Throttled spacing to protect free RPM quotas
                }
            }
            _queueState.value = ProbeQueueState(isRunning = false)
        }
    }

    fun cancelBatch() {
        batchJob?.cancel()
        _queueState.value = ProbeQueueState(isRunning = false)
    }

    private suspend fun runProbeForKey(modelKey: String) {
        val model = modelDao.getByKey(modelKey) ?: return
        val provider = providerDao.getById(model.providerId) ?: return
        val apiKey = credentialStore.getApiKey(provider.id)

        if (apiKey.isBlank() && !provider.allowEmptyKey) {
            modelDao.upsert(
                model.copy(
                    healthStatus = ModelHealthStatus.UNAVAILABLE.name,
                    lastError = "尚未設定 API Key",
                    lastTestedAt = System.currentTimeMillis(),
                )
            )
            return
        }

        _probingKeys.value = _probingKeys.value + modelKey
        try {
            val proxyCreds = credentialStore.getProxyCredentials()
            val outcome = networkClient.probeModel(
                provider = provider,
                modelId = model.modelId,
                apiKey = apiKey,
                proxyCredentials = proxyCreds,
            )
            modelDao.upsert(
                model.copy(
                    healthStatus = outcome.status.name,
                    cooldownUntilMs = outcome.cooldownUntilMs,
                    lastError = outcome.errorMessage,
                    lastLatencyMs = outcome.latencyMs,
                    lastTestedAt = System.currentTimeMillis(),
                )
            )
        } finally {
            _probingKeys.value = _probingKeys.value - modelKey
        }
    }
}
