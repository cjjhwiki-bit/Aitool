package com.example.network

import com.example.data.local.ProviderEntity
import com.example.data.local.ProviderType
import com.example.data.model.GenerationParameters
import com.example.data.model.ParameterMerger
import org.json.JSONArray
import org.json.JSONObject

data class ChatMessagePayload(
    val role: String,
    val content: String,
)

data class BuiltRequest(
    val url: String,
    val headers: Map<String, String>,
    val maskedHeadersJson: String,
    val bodyJson: String,
)

object ProviderAdapter {

    fun isValidHttpUrl(url: String): Boolean {
        val trimmed = url.trim().lowercase()
        return trimmed.startsWith("https://") || trimmed.startsWith("http://")
    }

    fun resolveBaseUrl(provider: ProviderEntity): String {
        if (provider.providerType() == ProviderType.CLOUDFLARE && provider.cfAccountId.isNotBlank()) {
            val acc = provider.cfAccountId.trim()
            val gw = provider.cfGatewayId.trim()
            return if (gw.isNotEmpty()) {
                "https://api.cloudflare.com/client/v4/accounts/$acc/ai/gateway/$gw/compat"
            } else {
                "https://api.cloudflare.com/client/v4/accounts/$acc/ai/v1"
            }
        }
        return provider.baseUrl.trim().trimEnd('/')
    }

    fun buildModelsUrl(provider: ProviderEntity): String {
        val base = resolveBaseUrl(provider)
        return "$base/models"
    }

    fun buildChatUrl(provider: ProviderEntity, params: GenerationParameters): String {
        val base = resolveBaseUrl(provider)
        val path = params.requestPathOverride?.trim()?.takeIf { it.isNotEmpty() } ?: "/chat/completions"
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        return "$base$normalizedPath"
    }

    fun buildHeaders(
        provider: ProviderEntity,
        apiKey: String,
        params: GenerationParameters,
    ): Pair<Map<String, String>, String> {
        val headers = linkedMapOf<String, String>()
        headers["Content-Type"] = "application/json"
        if (apiKey.isNotBlank()) {
            headers["Authorization"] = "Bearer $apiKey"
        }
        if (provider.providerType() == ProviderType.OPENROUTER) {
            headers["HTTP-Referer"] = "https://polychat.local"
            headers["X-Title"] = "PolyChat Workstation"
        }
        mergeHeadersFromJson(provider.customHeadersJson, headers)
        mergeHeadersFromJson(params.customHeadersJson, headers)

        val maskedObj = JSONObject()
        headers.forEach { (k, v) ->
            val lower = k.lowercase()
            val safeValue = if (lower == "authorization" || lower.contains("key") || lower.contains("token") || lower.contains("secret")) {
                maskSecret(v)
            } else {
                v
            }
            maskedObj.put(k, safeValue)
        }
        return headers to maskedObj.toString(2)
    }

    private fun mergeHeadersFromJson(jsonStr: String?, target: MutableMap<String, String>) {
        if (jsonStr.isNullOrBlank()) return
        try {
            val obj = JSONObject(jsonStr)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = obj.optString(k)
                if (k.isNotBlank() && v.isNotBlank()) {
                    target[k] = v
                }
            }
        } catch (_: Exception) {
        }
    }

    fun maskSecret(value: String): String {
        val raw = value.removePrefix("Bearer ").trim()
        val masked = if (raw.length <= 8) "••••" else "${raw.take(4)}••••${raw.takeLast(4)}"
        return if (value.startsWith("Bearer ")) "Bearer $masked" else masked
    }

    /**
     * Builds the outbound JSON payload. Strictly omits any parameter that is null / unset.
     */
    fun buildChatRequestBody(
        provider: ProviderEntity,
        modelId: String,
        messages: List<ChatMessagePayload>,
        params: GenerationParameters,
    ): String {
        val body = JSONObject()
        body.put("model", modelId)

        val msgArray = JSONArray()
        messages.forEach { m ->
            msgArray.put(JSONObject().apply {
                put("role", m.role)
                put("content", m.content)
            })
        }
        body.put("messages", msgArray)

        // Module 1: Sampling & Generation (strictly omit if null)
        params.temperature?.let { body.put("temperature", it) }
        params.topP?.let { body.put("top_p", it) }
        params.topK?.let { body.put("top_k", it) }
        params.minP?.let { body.put("min_p", it) }
        params.topA?.let { body.put("top_a", it) }
        params.maxTokens?.let { tokens ->
            val fieldName = if (params.useMaxCompletionTokensField == true) {
                "max_completion_tokens"
            } else {
                "max_tokens"
            }
            body.put(fieldName, tokens)
        }
        params.presencePenalty?.let { body.put("presence_penalty", it) }
        params.frequencyPenalty?.let { body.put("frequency_penalty", it) }
        params.repetitionPenalty?.let { body.put("repetition_penalty", it) }
        params.seed?.let { body.put("seed", it) }
        params.stopSequences?.takeIf { it.isNotEmpty() }?.let { body.put("stop", JSONArray(it)) }
        params.stream?.let { body.put("stream", it) }

        params.responseFormatType?.let { fmt ->
            if (fmt == "json_object") {
                body.put("response_format", JSONObject().put("type", "json_object"))
            } else if (fmt == "json_schema" && !params.responseJsonSchema.isNullOrBlank()) {
                runCatching {
                    val schemaObj = JSONObject(params.responseJsonSchema)
                    body.put(
                        "response_format",
                        JSONObject()
                            .put("type", "json_schema")
                            .put("json_schema", schemaObj),
                    )
                }
            }
        }
        params.logprobs?.let { body.put("logprobs", it) }
        if (params.logprobs == true) {
            params.topLogprobs?.let { body.put("top_logprobs", it) }
        }

        // Module 2: Reasoning / Thinking
        params.reasoningEffort?.takeIf { it.isNotBlank() }?.let {
            body.put("reasoning_effort", it)
        }
        params.thinkingBudget?.let { budget ->
            if (provider.providerType() == ProviderType.OPENROUTER) {
                body.put("reasoning", JSONObject().put("max_tokens", budget))
            } else {
                body.put("thinking_budget", budget)
            }
        }

        // Module 4: Provider-Specific Routing
        if (provider.providerType() == ProviderType.OPENROUTER) {
            params.openRouterFallbackModels?.takeIf { it.isNotEmpty() }?.let {
                body.put("models", JSONArray(it))
            }
            val providerCfg = JSONObject()
            params.openRouterProviderOrder?.takeIf { it.isNotEmpty() }?.let {
                providerCfg.put("order", JSONArray(it))
            }
            params.openRouterProviderOnly?.takeIf { it.isNotEmpty() }?.let {
                providerCfg.put("only", JSONArray(it))
            }
            params.openRouterProviderIgnore?.takeIf { it.isNotEmpty() }?.let {
                providerCfg.put("ignore", JSONArray(it))
            }
            params.openRouterQuantizations?.takeIf { it.isNotEmpty() }?.let {
                providerCfg.put("quantizations", JSONArray(it))
            }
            if (providerCfg.length() > 0) {
                body.put("provider", providerCfg)
            }
            if (params.openRouterMiddleOut == true) {
                body.put("transforms", JSONArray(listOf("middle-out")))
            }
        }

        if (provider.providerType() == ProviderType.MISTRAL) {
            params.mistralSafePrompt?.let { body.put("safe_prompt", it) }
        }

        if (provider.providerType() == ProviderType.GEMINI && !params.geminiSafetyThreshold.isNullOrBlank()) {
            val threshold = params.geminiSafetyThreshold
            val categories = listOf(
                "HARM_CATEGORY_HARASSMENT",
                "HARM_CATEGORY_HATE_SPEECH",
                "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                "HARM_CATEGORY_DANGEROUS_CONTENT",
            )
            val safetyArray = JSONArray()
            categories.forEach { cat ->
                safetyArray.put(JSONObject().put("category", cat).put("threshold", threshold))
            }
            body.put("safety_settings", safetyArray)
        }

        if (provider.providerType() == ProviderType.CUSTOM) {
            params.ollamaNumCtx?.let { ctx ->
                val opts = body.optJSONObject("options") ?: JSONObject()
                opts.put("num_ctx", ctx)
                body.put("options", opts)
            }
            params.ollamaKeepAlive?.takeIf { it.isNotBlank() }?.let { ka ->
                body.put("keep_alive", ka)
            }
        }

        // Module 5: Extra Body JSON Deep Merge
        val finalJson = if (!params.extraBodyJson.isNullOrBlank()) {
            try {
                val extra = JSONObject(params.extraBodyJson)
                ParameterMerger.deepMergeJson(body, extra)
            } catch (_: Exception) {
                body
            }
        } else {
            body
        }

        return finalJson.toString()
    }

    fun prepareChatRequest(
        provider: ProviderEntity,
        modelId: String,
        apiKey: String,
        messages: List<ChatMessagePayload>,
        params: GenerationParameters,
    ): BuiltRequest {
        val url = buildChatUrl(provider, params)
        val (headers, maskedHeaders) = buildHeaders(provider, apiKey, params)
        val body = buildChatRequestBody(provider, modelId, messages, params)
        return BuiltRequest(
            url = url,
            headers = headers,
            maskedHeadersJson = maskedHeaders,
            bodyJson = body,
        )
    }
}
