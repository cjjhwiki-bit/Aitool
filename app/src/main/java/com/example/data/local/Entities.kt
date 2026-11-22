package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ProviderType {
    GEMINI,
    OPENROUTER,
    GROQ,
    NVIDIA,
    CLOUDFLARE,
    MISTRAL,
    CUSTOM,
}

enum class ModelHealthStatus {
    AVAILABLE,
    COOLDOWN,
    UNAVAILABLE,
    UNTESTED,
}

enum class ConversationMode {
    SINGLE,
    PARALLEL,
    ROUNDTABLE,
}

enum class ParallelMergeStrategy {
    CONVERGE,
    BRANCH,
}

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM_INTERJECTION,
}

enum class MessageStatus {
    STREAMING,
    COMPLETED,
    FROZEN_QUOTA,
    ERROR,
    CANCELLED,
}

@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String, // ProviderType.name
    val tier: Int, // 1 = 主力, 2 = 備援, 3 = 自訂
    val isEnabled: Boolean = true,
    val baseUrl: String,
    val cfAccountId: String = "",
    val cfGatewayId: String = "",
    val openRouterFreeOnly: Boolean = true,
    val allowEmptyKey: Boolean = false,
    val customHeadersJson: String = "",
    val keyPortalUrl: String = "",
    val statusNote: String = "",
    val lastTestedAt: Long = 0L,
    val lastTestSuccess: Boolean? = null,
    val sortOrder: Int = 0,
) {
    fun providerType(): ProviderType =
        runCatching { ProviderType.valueOf(type) }.getOrDefault(ProviderType.CUSTOM)
}

@Entity(
    tableName = "models",
    foreignKeys = [
        ForeignKey(
            entity = ProviderEntity::class,
            parentColumns = ["id"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("providerId"), Index("canonicalId")],
)
data class ModelEntity(
    @PrimaryKey val key: String, // "$providerId::$modelId"
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val canonicalId: String,
    val isSelected: Boolean = true,
    val isFree: Boolean = true,
    val contextLength: Int? = null,
    val healthStatus: String = ModelHealthStatus.UNTESTED.name,
    val cooldownUntilMs: Long = 0L,
    val lastError: String = "",
    val lastLatencyMs: Long? = null,
    val lastTestedAt: Long = 0L,
) {
    fun health(): ModelHealthStatus =
        runCatching { ModelHealthStatus.valueOf(healthStatus) }.getOrDefault(ModelHealthStatus.UNTESTED)
}

@Entity(tableName = "canonical_aliases")
data class CanonicalAliasEntity(
    @PrimaryKey val id: String,
    val canonicalFamilyId: String,
    val displayName: String,
    val matchPattern: String, // exact modelId or substring pattern
    val isUserDefined: Boolean = false,
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val mode: String = ConversationMode.SINGLE.name,
    val activeModelKey: String? = null,
    val parallelModelKeysJson: String = "[]",
    val parallelMergeStrategy: String = ParallelMergeStrategy.CONVERGE.name,
    val roundtableMaxTurns: Int = 6,
    val roundtableDelayMs: Long = 1000L,
    val parameterOverrideJson: String = "",
    val activePresetId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun conversationMode(): ConversationMode =
        runCatching { ConversationMode.valueOf(mode) }.getOrDefault(ConversationMode.SINGLE)

    fun mergeStrategy(): ParallelMergeStrategy =
        runCatching { ParallelMergeStrategy.valueOf(parallelMergeStrategy) }
            .getOrDefault(ParallelMergeStrategy.CONVERGE)
}

@Entity(
    tableName = "message_turns",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("conversationId")],
)
data class MessageTurnEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val orderIndex: Int,
    val role: String, // MessageRole.name
    val selectedBranchKey: String? = null, // Used in Parallel Converge mode
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun messageRole(): MessageRole =
        runCatching { MessageRole.valueOf(role) }.getOrDefault(MessageRole.USER)
}

@Entity(
    tableName = "message_versions",
    foreignKeys = [
        ForeignKey(
            entity = MessageTurnEntity::class,
            parentColumns = ["id"],
            childColumns = ["turnId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("turnId"), Index("branchKey")],
)
data class MessageVersionEntity(
    @PrimaryKey val id: String,
    val turnId: String,
    val branchKey: String = "default", // "default" or "$providerId::$modelId"
    val versionIndex: Int = 0,
    val isActiveVersion: Boolean = true,
    val content: String = "",
    val reasoningContent: String = "",
    val providerId: String = "",
    val providerName: String = "",
    val modelId: String = "",
    val participantName: String = "",
    val isFailover: Boolean = false,
    val failoverFromLabel: String = "",
    val status: String = MessageStatus.COMPLETED.name,
    val errorMessage: String = "",
    val paramSnapshotJson: String = "",
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val ttftMs: Long? = null,
    val tps: Double? = null,
    val rateLimitInfoJson: String = "",
    val debugRequestJson: String = "",
    val debugResponseHeadersJson: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun messageStatus(): MessageStatus =
        runCatching { MessageStatus.valueOf(status) }.getOrDefault(MessageStatus.COMPLETED)
}

@Entity(
    tableName = "roundtable_participants",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("conversationId")],
)
data class RoundtableParticipantEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val sortOrder: Int,
    val modelKey: String,
    val displayName: String,
    val systemPrompt: String = "",
)

@Entity(tableName = "parameter_presets")
data class ParameterPresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val scope: String, // "GLOBAL", "PRESET", "PROVIDER", "MODEL"
    val targetKey: String = "",
    val paramsJson: String,
    val isBuiltIn: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "prompt_templates")
data class PromptTemplateEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: String,
    val content: String,
    val isBuiltIn: Boolean = false,
)
