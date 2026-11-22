package com.example.domain

import com.example.data.local.CanonicalAliasEntity
import com.example.data.local.ModelEntity
import com.example.data.local.ModelHealthStatus
import com.example.data.local.ProviderEntity

/**
 * Normalizes provider-specific model IDs into shared Canonical Model Families
 * (e.g., Groq, OpenRouter, NVIDIA, Cloudflare variants of Llama 3.3 70B or DeepSeek R1)
 * so the 429 Failover Engine can find same-model backups across providers.
 */
class CanonicalModelMapper {

    fun resolveCanonicalId(
        modelId: String,
        aliases: List<CanonicalAliasEntity>,
    ): String {
        val normalized = modelId.lowercase()
            .substringAfterLast("/")
            .removeSuffix(":free")
            .trim()

        // 1. User-defined exact/substring aliases have highest priority
        val userMatch = aliases.filter { it.isUserDefined }.firstOrNull { alias ->
            val p = alias.matchPattern.lowercase().trim()
            p.isNotEmpty() && (modelId.equals(p, ignoreCase = true) || normalized.contains(p))
        }
        if (userMatch != null) return userMatch.canonicalFamilyId

        // 2. Built-in alias table
        val builtInMatch = aliases.filter { !it.isUserDefined }.firstOrNull { alias ->
            val p = alias.matchPattern.lowercase().trim()
            p.isNotEmpty() && normalized.contains(p)
        }
        if (builtInMatch != null) return builtInMatch.canonicalFamilyId

        // 3. Heuristic fallback stripping common suffixes
        return normalized
            .replace(Regex("-(instruct|versatile|chat|fp8|fast|preview|latest)$"), "")
            .replace("@cf/", "")
            .trim()
    }

    fun findSameModelFallbacks(
        failedModel: ModelEntity,
        allModels: List<ModelEntity>,
        providers: List<ProviderEntity>,
        aliases: List<CanonicalAliasEntity>,
        nowMs: Long = System.currentTimeMillis(),
    ): List<Pair<ProviderEntity, ModelEntity>> {
        val enabledProviders = providers.filter { it.isEnabled }.associateBy { it.id }
        val targetCanonical = failedModel.canonicalId.ifBlank {
            resolveCanonicalId(failedModel.modelId, aliases)
        }

        return allModels.mapNotNull { candidate ->
            if (candidate.key == failedModel.key) return@mapNotNull null
            val provider = enabledProviders[candidate.providerId] ?: return@mapNotNull null
            val candCanonical = candidate.canonicalId.ifBlank {
                resolveCanonicalId(candidate.modelId, aliases)
            }
            if (candCanonical != targetCanonical) return@mapNotNull null

            val isHealthy = when (candidate.health()) {
                ModelHealthStatus.AVAILABLE -> true
                ModelHealthStatus.COOLDOWN -> candidate.cooldownUntilMs <= nowMs
                else -> false
            }
            if (!isHealthy) return@mapNotNull null
            provider to candidate
        }
    }
}
