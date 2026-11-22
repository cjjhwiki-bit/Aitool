package com.example

import com.example.data.local.CanonicalAliasEntity
import com.example.data.local.ModelEntity
import com.example.data.local.ModelHealthStatus
import com.example.data.local.ProviderEntity
import com.example.data.local.ProviderType
import com.example.data.model.GenerationParameters
import com.example.data.model.ParameterMerger
import com.example.domain.CanonicalModelMapper
import com.example.network.ProviderAdapter
import com.example.ui.components.MdBlock
import com.example.ui.components.parseMarkdownBlocks
import com.example.ui.components.stripMarkdownFormatting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PolyChatEngineTest {

    @Test
    fun threeLayerMerge_preservesUnsetNullsAndOverridesInOrder() {
        val global = GenerationParameters(
            temperature = 0.7,
            topP = 0.9,
            stream = true,
        )
        val preset = GenerationParameters(
            temperature = 0.3,
        )
        val convOverride = GenerationParameters(
            maxTokens = 2048,
        )
        val merged = ParameterMerger.merge(global, preset, convOverride)
        assertEquals(0.3, merged.temperature!!, 0.001)
        assertEquals(0.9, merged.topP!!, 0.001)
        assertEquals(true, merged.stream)
        assertEquals(2048, merged.maxTokens)
        // Unset parameters remain strictly null (omitted at serialization time)
        assertNull(merged.topK)
        assertNull(merged.minP)
        assertNull(merged.seed)
        assertNull(merged.presencePenalty)
        assertEquals(4, merged.activeOverrideCount())
    }

    @Test
    fun canonicalMapper_findsGreenCrossProviderFallback() {
        val mapper = CanonicalModelMapper()
        val aliases = listOf(
            CanonicalAliasEntity("1", "llama-3.3-70b", "Llama 3.3 70B", "llama-3.3-70b"),
        )
        val groqProvider = ProviderEntity("groq", "Groq", ProviderType.GROQ.name, 1, true, "https://api.groq.com/openai/v1")
        val orProvider = ProviderEntity("openrouter", "OpenRouter", ProviderType.OPENROUTER.name, 1, true, "https://openrouter.ai/api/v1")

        val groqModel = ModelEntity(
            key = "groq::llama-3.3-70b-versatile",
            providerId = "groq",
            modelId = "llama-3.3-70b-versatile",
            displayName = "Llama 3.3 70B",
            canonicalId = mapper.resolveCanonicalId("llama-3.3-70b-versatile", aliases),
            healthStatus = ModelHealthStatus.COOLDOWN.name,
            cooldownUntilMs = System.currentTimeMillis() + 60_000L,
        )
        val orModel = ModelEntity(
            key = "openrouter::meta-llama/llama-3.3-70b-instruct:free",
            providerId = "openrouter",
            modelId = "meta-llama/llama-3.3-70b-instruct:free",
            displayName = "Llama 3.3 70B Free",
            canonicalId = mapper.resolveCanonicalId("meta-llama/llama-3.3-70b-instruct:free", aliases),
            healthStatus = ModelHealthStatus.AVAILABLE.name,
        )

        val fallbacks = mapper.findSameModelFallbacks(
            failedModel = groqModel,
            allModels = listOf(groqModel, orModel),
            providers = listOf(groqProvider, orProvider),
            aliases = aliases,
        )
        assertEquals(1, fallbacks.size)
        assertEquals("openrouter", fallbacks.first().first.id)
        assertEquals(orModel.key, fallbacks.first().second.key)
    }

    @Test
    fun cloudflareEndpoint_andUrlSecurityValidation() {
        val cfNoGw = ProviderEntity(
            id = "cloudflare",
            name = "Cloudflare",
            type = ProviderType.CLOUDFLARE.name,
            tier = 2,
            baseUrl = "",
            cfAccountId = "acc123",
            cfGatewayId = "",
        )
        assertEquals(
            "https://api.cloudflare.com/client/v4/accounts/acc123/ai/v1",
            ProviderAdapter.resolveBaseUrl(cfNoGw),
        )

        val cfWithGw = cfNoGw.copy(cfGatewayId = "my-gw")
        assertEquals(
            "https://api.cloudflare.com/client/v4/accounts/acc123/ai/gateway/my-gw/compat",
            ProviderAdapter.resolveBaseUrl(cfWithGw),
        )

        assertTrue(ProviderAdapter.isValidHttpUrl("https://api.openai.com/v1"))
        assertTrue(ProviderAdapter.isValidHttpUrl("http://127.0.0.1:11434/v1"))
        assertFalse(ProviderAdapter.isValidHttpUrl("file:///etc/passwd"))
        assertEquals("Bearer sk-1••••6789", ProviderAdapter.maskSecret("Bearer sk-123456789"))
    }

    @Test
    fun markdownParser_andStripper_workCleanly() {
        val md = """
            # Title
            **Bold** and `code`
            ```kotlin
            val x = 1
            ```
        """.trimIndent()
        val blocks = parseMarkdownBlocks(md)
        assertTrue(blocks.any { it is MdBlock.Heading && it.text == "Title" })
        assertTrue(blocks.any { it is MdBlock.CodeBlock && it.language == "kotlin" && it.code == "val x = 1" })

        val stripped = stripMarkdownFormatting("# Header\nHello **world** `ok`")
        assertEquals("Header\nHello world ok", stripped)
    }
}
