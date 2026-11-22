package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.GenerationParameters

@Database(
    entities = [
        ProviderEntity::class,
        ModelEntity::class,
        CanonicalAliasEntity::class,
        ConversationEntity::class,
        MessageTurnEntity::class,
        MessageVersionEntity::class,
        RoundtableParticipantEntity::class,
        ParameterPresetEntity::class,
        PromptTemplateEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun providerDao(): ProviderDao
    abstract fun modelDao(): ModelDao
    abstract fun canonicalAliasDao(): CanonicalAliasDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun roundtableDao(): RoundtableDao
    abstract fun parameterPresetDao(): ParameterPresetDao
    abstract fun promptTemplateDao(): PromptTemplateDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "polychat_workstation.db",
                ).build().also { INSTANCE = it }
            }
        }

        suspend fun seedDefaultsIfNeeded(db: AppDatabase) {
            db.providerDao().insertAllIgnore(defaultProviders())
            db.canonicalAliasDao().insertAllIgnore(defaultCanonicalAliases())
            db.parameterPresetDao().insertAllIgnore(defaultPresets())
            db.promptTemplateDao().insertAllIgnore(defaultPromptTemplates())
        }

        private fun defaultProviders(): List<ProviderEntity> = listOf(
            ProviderEntity(
                id = "gemini",
                name = "Google Gemini (AI Studio)",
                type = ProviderType.GEMINI.name,
                tier = 1,
                isEnabled = true,
                baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
                keyPortalUrl = "https://aistudio.google.com/apikey",
                statusNote = "免費額度受 Google 資料政策規範，敏感內容請留意隱私提醒",
                sortOrder = 1,
            ),
            ProviderEntity(
                id = "openrouter",
                name = "OpenRouter",
                type = ProviderType.OPENROUTER.name,
                tier = 1,
                isEnabled = true,
                baseUrl = "https://openrouter.ai/api/v1",
                openRouterFreeOnly = true,
                keyPortalUrl = "https://openrouter.ai/keys",
                statusNote = "預設僅顯示 :free 免費模型，支援自動路由與備援陣列",
                sortOrder = 2,
            ),
            ProviderEntity(
                id = "groq",
                name = "Groq",
                type = ProviderType.GROQ.name,
                tier = 1,
                isEnabled = true,
                baseUrl = "https://api.groq.com/openai/v1",
                keyPortalUrl = "https://console.groq.com/keys",
                statusNote = "極速 LPU 推論引擎，支援 Llama / DeepSeek / Qwen",
                sortOrder = 3,
            ),
            ProviderEntity(
                id = "nvidia",
                name = "NVIDIA NIM",
                type = ProviderType.NVIDIA.name,
                tier = 2,
                isEnabled = true,
                baseUrl = "https://integrate.api.nvidia.com/v1",
                keyPortalUrl = "https://build.nvidia.com",
                statusNote = "提供多種開源旗艦模型免費試用額度",
                sortOrder = 4,
            ),
            ProviderEntity(
                id = "cloudflare",
                name = "Cloudflare Workers AI",
                type = ProviderType.CLOUDFLARE.name,
                tier = 2,
                isEnabled = true,
                baseUrl = "https://api.cloudflare.com/client/v4/accounts/{account_id}/ai/v1",
                keyPortalUrl = "https://dash.cloudflare.com",
                statusNote = "填入 Account ID 與可選 AI Gateway ID 後自動拼裝端點",
                sortOrder = 5,
            ),
            ProviderEntity(
                id = "mistral",
                name = "Mistral AI (Free Mode)",
                type = ProviderType.MISTRAL.name,
                tier = 2,
                isEnabled = true,
                baseUrl = "https://api.mistral.ai/v1",
                keyPortalUrl = "https://console.mistral.ai/api-keys",
                statusNote = "免費實驗階層，支援 Mistral / Codestral 系列",
                sortOrder = 6,
            ),
        )

        private fun defaultCanonicalAliases(): List<CanonicalAliasEntity> = listOf(
            CanonicalAliasEntity("ca_llama33_70b", "llama-3.3-70b", "Llama 3.3 70B", "llama-3.3-70b"),
            CanonicalAliasEntity("ca_llama31_8b", "llama-3.1-8b", "Llama 3.1 8B", "llama-3.1-8b"),
            CanonicalAliasEntity("ca_llama31_70b", "llama-3.1-70b", "Llama 3.1 70B", "llama-3.1-70b"),
            CanonicalAliasEntity("ca_deepseek_r1", "deepseek-r1", "DeepSeek R1", "deepseek-r1"),
            CanonicalAliasEntity("ca_deepseek_v3", "deepseek-v3", "DeepSeek V3 / Chat", "deepseek-v3"),
            CanonicalAliasEntity("ca_qwen25_72b", "qwen-2.5-72b", "Qwen 2.5 72B", "qwen2.5-72b"),
            CanonicalAliasEntity("ca_qwen3_32b", "qwen-3-32b", "Qwen 3 32B", "qwen3-32b"),
            CanonicalAliasEntity("ca_gemma2_27b", "gemma-2-27b", "Gemma 2 27B", "gemma-2-27b"),
            CanonicalAliasEntity("ca_mistral_small", "mistral-small", "Mistral Small", "mistral-small"),
            CanonicalAliasEntity("ca_gemini_25_flash", "gemini-2.5-flash", "Gemini 2.5 Flash", "gemini-2.5-flash"),
        )

        private fun defaultPresets(): List<ParameterPresetEntity> = listOf(
            ParameterPresetEntity(
                id = "global_default",
                name = "全域預設",
                scope = "GLOBAL",
                targetKey = "",
                paramsJson = GenerationParameters(
                    stream = true,
                    collapseThinkingByDefault = true,
                    includeThinkingInHistory = false,
                    streamRenderThrottleMs = 45L,
                    renderMarkdown = true,
                    renderLatex = true,
                    hapticFeedback = true,
                    connectTimeoutSec = 20,
                    readTimeoutSec = 90,
                    retryCount = 1,
                    retryBackoffMs = 1200L,
                ).toJsonString(),
                isBuiltIn = true,
            ),
            ParameterPresetEntity(
                id = "preset_balanced",
                name = "均衡對話",
                scope = "PRESET",
                paramsJson = GenerationParameters(temperature = 0.7, topP = 0.95).toJsonString(),
                isBuiltIn = true,
            ),
            ParameterPresetEntity(
                id = "preset_coding",
                name = "精準程式碼",
                scope = "PRESET",
                paramsJson = GenerationParameters(
                    temperature = 0.15,
                    topP = 0.9,
                    reasoningEffort = "high",
                ).toJsonString(),
                isBuiltIn = true,
            ),
            ParameterPresetEntity(
                id = "preset_creative",
                name = "創意腦力激盪",
                scope = "PRESET",
                paramsJson = GenerationParameters(
                    temperature = 1.05,
                    topP = 0.95,
                    presencePenalty = 0.4,
                ).toJsonString(),
                isBuiltIn = true,
            ),
            ParameterPresetEntity(
                id = "preset_json_strict",
                name = "嚴格 JSON 輸出",
                scope = "PRESET",
                paramsJson = GenerationParameters(
                    temperature = 0.1,
                    responseFormatType = "json_object",
                ).toJsonString(),
                isBuiltIn = true,
            ),
        )

        private fun defaultPromptTemplates(): List<PromptTemplateEntity> = listOf(
            PromptTemplateEntity(
                id = "tpl_general_tw",
                title = "繁體中文專業助手",
                category = "通用",
                content = "你是一位精確、條理分明的繁體中文 AI 助手。現在日期是 {{date}}，目前使用模型為 {{model}}。請以清晰結構直接回答重點。",
                isBuiltIn = true,
            ),
            PromptTemplateEntity(
                id = "tpl_architect",
                title = "系統架構與程式碼審查員",
                category = "開發",
                content = "請以資深軟體架構師視角審查問題，指出邊界條件、時間複雜度與潛在安全性風險，並給出可直接執行的簡潔程式碼。",
                isBuiltIn = true,
            ),
            PromptTemplateEntity(
                id = "tpl_debate_pro",
                title = "圓桌正方：創新推進者",
                category = "圓桌人設",
                content = "你在圓桌討論中扮演積極推進者，專注挖掘方案的機會、創新亮點與可行突破口，並簡明回應其他發言者的疑慮。",
                isBuiltIn = true,
            ),
            PromptTemplateEntity(
                id = "tpl_debate_critic",
                title = "圓桌反方：魔鬼代言人",
                category = "圓桌人設",
                content = "你在圓桌討論中扮演嚴謹的質疑者，負責找出邏輯漏洞、成本盲點與實務風險，提出具建設性的反駁。",
                isBuiltIn = true,
            ),
        )
    }
}
