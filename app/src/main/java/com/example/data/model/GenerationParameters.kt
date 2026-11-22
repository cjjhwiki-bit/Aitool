package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Six-Module Power User Parameter Model.
 * Strict "Omit-When-Unset" rule: every optional API parameter is nullable.
 * Only non-null fields are serialized into outbound JSON payloads.
 */
data class GenerationParameters(
    // Module 1: Sampling & Generation
    val temperature: Double? = null,
    val topP: Double? = null,
    val topK: Int? = null,
    val minP: Double? = null,
    val topA: Double? = null,
    val maxTokens: Int? = null,
    val useMaxCompletionTokensField: Boolean? = null,
    val presencePenalty: Double? = null,
    val frequencyPenalty: Double? = null,
    val repetitionPenalty: Double? = null,
    val seed: Int? = null,
    val stopSequences: List<String>? = null,
    val stream: Boolean? = null,
    val responseFormatType: String? = null, // "text", "json_object", "json_schema"
    val responseJsonSchema: String? = null,
    val logprobs: Boolean? = null,
    val topLogprobs: Int? = null,

    // Module 2: Reasoning / Thinking
    val reasoningEffort: String? = null, // "low", "medium", "high", "minimal"
    val thinkingBudget: Int? = null,
    val hideThinkingOutput: Boolean? = null,
    val collapseThinkingByDefault: Boolean? = null,
    val includeThinkingInHistory: Boolean? = null,

    // Module 3: Context & Prompts
    val systemPrompt: String? = null,
    val systemRoleName: String? = null, // "system" or "developer"
    val contextTruncationMode: String? = null, // "NONE", "COUNT", "TOKENS"
    val maxContextMessages: Int? = null,
    val maxContextTokens: Int? = null,
    val dropStrategy: String? = null, // "DROP_OLDEST_KEEP_SYSTEM", "SLIDING_PAIR"
    val titleGeneratorModelKey: String? = null,

    // Module 4: Provider-Specific Routing
    val openRouterFallbackModels: List<String>? = null,
    val openRouterProviderOrder: List<String>? = null,
    val openRouterProviderOnly: List<String>? = null,
    val openRouterProviderIgnore: List<String>? = null,
    val openRouterQuantizations: List<String>? = null,
    val openRouterMiddleOut: Boolean? = null,
    val geminiSafetyThreshold: String? = null, // "BLOCK_NONE", "BLOCK_ONLY_HIGH", "BLOCK_MEDIUM_AND_ABOVE"
    val mistralSafePrompt: Boolean? = null,
    val ollamaNumCtx: Int? = null,
    val ollamaKeepAlive: String? = null,

    // Module 5: Network & Ultimate Overrides
    val requestPathOverride: String? = null,
    val connectTimeoutSec: Int? = null,
    val readTimeoutSec: Int? = null,
    val retryCount: Int? = null,
    val retryBackoffMs: Long? = null,
    val proxyHost: String? = null,
    val proxyPort: Int? = null,
    val customHeadersJson: String? = null,
    val extraBodyJson: String? = null,

    // Module 6: Local UX
    val streamRenderThrottleMs: Long? = null,
    val keepScreenAwake: Boolean? = null,
    val hapticFeedback: Boolean? = null,
    val renderMarkdown: Boolean? = null,
    val renderLatex: Boolean? = null,
) {
    fun toJsonString(): String {
        val obj = JSONObject()
        temperature?.let { obj.put("temperature", it) }
        topP?.let { obj.put("topP", it) }
        topK?.let { obj.put("topK", it) }
        minP?.let { obj.put("minP", it) }
        topA?.let { obj.put("topA", it) }
        maxTokens?.let { obj.put("maxTokens", it) }
        useMaxCompletionTokensField?.let { obj.put("useMaxCompletionTokensField", it) }
        presencePenalty?.let { obj.put("presencePenalty", it) }
        frequencyPenalty?.let { obj.put("frequencyPenalty", it) }
        repetitionPenalty?.let { obj.put("repetitionPenalty", it) }
        seed?.let { obj.put("seed", it) }
        stopSequences?.let { obj.put("stopSequences", JSONArray(it)) }
        stream?.let { obj.put("stream", it) }
        responseFormatType?.let { obj.put("responseFormatType", it) }
        responseJsonSchema?.let { obj.put("responseJsonSchema", it) }
        logprobs?.let { obj.put("logprobs", it) }
        topLogprobs?.let { obj.put("topLogprobs", it) }

        reasoningEffort?.let { obj.put("reasoningEffort", it) }
        thinkingBudget?.let { obj.put("thinkingBudget", it) }
        hideThinkingOutput?.let { obj.put("hideThinkingOutput", it) }
        collapseThinkingByDefault?.let { obj.put("collapseThinkingByDefault", it) }
        includeThinkingInHistory?.let { obj.put("includeThinkingInHistory", it) }

        systemPrompt?.let { obj.put("systemPrompt", it) }
        systemRoleName?.let { obj.put("systemRoleName", it) }
        contextTruncationMode?.let { obj.put("contextTruncationMode", it) }
        maxContextMessages?.let { obj.put("maxContextMessages", it) }
        maxContextTokens?.let { obj.put("maxContextTokens", it) }
        dropStrategy?.let { obj.put("dropStrategy", it) }
        titleGeneratorModelKey?.let { obj.put("titleGeneratorModelKey", it) }

        openRouterFallbackModels?.let { obj.put("openRouterFallbackModels", JSONArray(it)) }
        openRouterProviderOrder?.let { obj.put("openRouterProviderOrder", JSONArray(it)) }
        openRouterProviderOnly?.let { obj.put("openRouterProviderOnly", JSONArray(it)) }
        openRouterProviderIgnore?.let { obj.put("openRouterProviderIgnore", JSONArray(it)) }
        openRouterQuantizations?.let { obj.put("openRouterQuantizations", JSONArray(it)) }
        openRouterMiddleOut?.let { obj.put("openRouterMiddleOut", it) }
        geminiSafetyThreshold?.let { obj.put("geminiSafetyThreshold", it) }
        mistralSafePrompt?.let { obj.put("mistralSafePrompt", it) }
        ollamaNumCtx?.let { obj.put("ollamaNumCtx", it) }
        ollamaKeepAlive?.let { obj.put("ollamaKeepAlive", it) }

        requestPathOverride?.let { obj.put("requestPathOverride", it) }
        connectTimeoutSec?.let { obj.put("connectTimeoutSec", it) }
        readTimeoutSec?.let { obj.put("readTimeoutSec", it) }
        retryCount?.let { obj.put("retryCount", it) }
        retryBackoffMs?.let { obj.put("retryBackoffMs", it) }
        proxyHost?.let { obj.put("proxyHost", it) }
        proxyPort?.let { obj.put("proxyPort", it) }
        customHeadersJson?.let { obj.put("customHeadersJson", it) }
        extraBodyJson?.let { obj.put("extraBodyJson", it) }

        streamRenderThrottleMs?.let { obj.put("streamRenderThrottleMs", it) }
        keepScreenAwake?.let { obj.put("keepScreenAwake", it) }
        hapticFeedback?.let { obj.put("hapticFeedback", it) }
        renderMarkdown?.let { obj.put("renderMarkdown", it) }
        renderLatex?.let { obj.put("renderLatex", it) }
        return obj.toString()
    }

    fun activeOverrideCount(): Int {
        var count = 0
        listOf<Any?>(
            temperature, topP, topK, minP, topA, maxTokens, useMaxCompletionTokensField,
            presencePenalty, frequencyPenalty, repetitionPenalty, seed, stopSequences, stream,
            responseFormatType, logprobs, reasoningEffort, thinkingBudget, hideThinkingOutput,
            collapseThinkingByDefault, includeThinkingInHistory, systemPrompt, systemRoleName,
            contextTruncationMode, maxContextMessages, maxContextTokens, dropStrategy,
            titleGeneratorModelKey, openRouterFallbackModels, openRouterProviderOrder,
            openRouterProviderOnly, openRouterProviderIgnore, openRouterQuantizations,
            openRouterMiddleOut, geminiSafetyThreshold, mistralSafePrompt, ollamaNumCtx,
            ollamaKeepAlive, requestPathOverride, connectTimeoutSec, readTimeoutSec, retryCount,
            retryBackoffMs, proxyHost, proxyPort, customHeadersJson, extraBodyJson,
            streamRenderThrottleMs, keepScreenAwake, hapticFeedback, renderMarkdown, renderLatex,
        ).forEach { if (it != null) count++ }
        return count
    }

    companion object {
        fun fromJsonString(json: String?): GenerationParameters {
            if (json.isNullOrBlank()) return GenerationParameters()
            return try {
                val obj = JSONObject(json)
                GenerationParameters(
                    temperature = obj.optDoubleOrNull("temperature"),
                    topP = obj.optDoubleOrNull("topP"),
                    topK = obj.optIntOrNull("topK"),
                    minP = obj.optDoubleOrNull("minP"),
                    topA = obj.optDoubleOrNull("topA"),
                    maxTokens = obj.optIntOrNull("maxTokens"),
                    useMaxCompletionTokensField = obj.optBooleanOrNull("useMaxCompletionTokensField"),
                    presencePenalty = obj.optDoubleOrNull("presencePenalty"),
                    frequencyPenalty = obj.optDoubleOrNull("frequencyPenalty"),
                    repetitionPenalty = obj.optDoubleOrNull("repetitionPenalty"),
                    seed = obj.optIntOrNull("seed"),
                    stopSequences = obj.optStringListOrNull("stopSequences"),
                    stream = obj.optBooleanOrNull("stream"),
                    responseFormatType = obj.optStringOrNull("responseFormatType"),
                    responseJsonSchema = obj.optStringOrNull("responseJsonSchema"),
                    logprobs = obj.optBooleanOrNull("logprobs"),
                    topLogprobs = obj.optIntOrNull("topLogprobs"),
                    reasoningEffort = obj.optStringOrNull("reasoningEffort"),
                    thinkingBudget = obj.optIntOrNull("thinkingBudget"),
                    hideThinkingOutput = obj.optBooleanOrNull("hideThinkingOutput"),
                    collapseThinkingByDefault = obj.optBooleanOrNull("collapseThinkingByDefault"),
                    includeThinkingInHistory = obj.optBooleanOrNull("includeThinkingInHistory"),
                    systemPrompt = obj.optStringOrNull("systemPrompt"),
                    systemRoleName = obj.optStringOrNull("systemRoleName"),
                    contextTruncationMode = obj.optStringOrNull("contextTruncationMode"),
                    maxContextMessages = obj.optIntOrNull("maxContextMessages"),
                    maxContextTokens = obj.optIntOrNull("maxContextTokens"),
                    dropStrategy = obj.optStringOrNull("dropStrategy"),
                    titleGeneratorModelKey = obj.optStringOrNull("titleGeneratorModelKey"),
                    openRouterFallbackModels = obj.optStringListOrNull("openRouterFallbackModels"),
                    openRouterProviderOrder = obj.optStringListOrNull("openRouterProviderOrder"),
                    openRouterProviderOnly = obj.optStringListOrNull("openRouterProviderOnly"),
                    openRouterProviderIgnore = obj.optStringListOrNull("openRouterProviderIgnore"),
                    openRouterQuantizations = obj.optStringListOrNull("openRouterQuantizations"),
                    openRouterMiddleOut = obj.optBooleanOrNull("openRouterMiddleOut"),
                    geminiSafetyThreshold = obj.optStringOrNull("geminiSafetyThreshold"),
                    mistralSafePrompt = obj.optBooleanOrNull("mistralSafePrompt"),
                    ollamaNumCtx = obj.optIntOrNull("ollamaNumCtx"),
                    ollamaKeepAlive = obj.optStringOrNull("ollamaKeepAlive"),
                    requestPathOverride = obj.optStringOrNull("requestPathOverride"),
                    connectTimeoutSec = obj.optIntOrNull("connectTimeoutSec"),
                    readTimeoutSec = obj.optIntOrNull("readTimeoutSec"),
                    retryCount = obj.optIntOrNull("retryCount"),
                    retryBackoffMs = obj.optLongOrNull("retryBackoffMs"),
                    proxyHost = obj.optStringOrNull("proxyHost"),
                    proxyPort = obj.optIntOrNull("proxyPort"),
                    customHeadersJson = obj.optStringOrNull("customHeadersJson"),
                    extraBodyJson = obj.optStringOrNull("extraBodyJson"),
                    streamRenderThrottleMs = obj.optLongOrNull("streamRenderThrottleMs"),
                    keepScreenAwake = obj.optBooleanOrNull("keepScreenAwake"),
                    hapticFeedback = obj.optBooleanOrNull("hapticFeedback"),
                    renderMarkdown = obj.optBooleanOrNull("renderMarkdown"),
                    renderLatex = obj.optBooleanOrNull("renderLatex"),
                )
            } catch (_: Exception) {
                GenerationParameters()
            }
        }
    }
}

private fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (has(key) && !isNull(key)) optDouble(key) else null

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null

private fun JSONObject.optLongOrNull(key: String): Long? =
    if (has(key) && !isNull(key)) optLong(key) else null

private fun JSONObject.optBooleanOrNull(key: String): Boolean? =
    if (has(key) && !isNull(key)) optBoolean(key) else null

private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) optString(key) else null

private fun JSONObject.optStringListOrNull(key: String): List<String>? {
    if (!has(key) || isNull(key)) return null
    val arr = optJSONArray(key) ?: return null
    return buildList {
        for (i in 0 until arr.length()) {
            add(arr.optString(i))
        }
    }
}
