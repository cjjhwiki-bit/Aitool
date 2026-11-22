package com.example.data.model

import org.json.JSONObject

/**
 * Resolves 3-layer parameter inheritance:
 * 1. Global Defaults
 * 2. Provider / Model / Custom Preset
 * 3. Per-Conversation Live Drawer Override
 *
 * Also performs deep JSON merging for Extra Body overrides.
 */
object ParameterMerger {

    fun merge(vararg layers: GenerationParameters): GenerationParameters {
        var result = GenerationParameters()
        for (layer in layers) {
            result = mergeTwo(result, layer)
        }
        return result
    }

    private fun mergeTwo(base: GenerationParameters, over: GenerationParameters): GenerationParameters {
        return GenerationParameters(
            temperature = over.temperature ?: base.temperature,
            topP = over.topP ?: base.topP,
            topK = over.topK ?: base.topK,
            minP = over.minP ?: base.minP,
            topA = over.topA ?: base.topA,
            maxTokens = over.maxTokens ?: base.maxTokens,
            useMaxCompletionTokensField = over.useMaxCompletionTokensField ?: base.useMaxCompletionTokensField,
            presencePenalty = over.presencePenalty ?: base.presencePenalty,
            frequencyPenalty = over.frequencyPenalty ?: base.frequencyPenalty,
            repetitionPenalty = over.repetitionPenalty ?: base.repetitionPenalty,
            seed = over.seed ?: base.seed,
            stopSequences = over.stopSequences ?: base.stopSequences,
            stream = over.stream ?: base.stream,
            responseFormatType = over.responseFormatType ?: base.responseFormatType,
            responseJsonSchema = over.responseJsonSchema ?: base.responseJsonSchema,
            logprobs = over.logprobs ?: base.logprobs,
            topLogprobs = over.topLogprobs ?: base.topLogprobs,

            reasoningEffort = over.reasoningEffort ?: base.reasoningEffort,
            thinkingBudget = over.thinkingBudget ?: base.thinkingBudget,
            hideThinkingOutput = over.hideThinkingOutput ?: base.hideThinkingOutput,
            collapseThinkingByDefault = over.collapseThinkingByDefault ?: base.collapseThinkingByDefault,
            includeThinkingInHistory = over.includeThinkingInHistory ?: base.includeThinkingInHistory,

            systemPrompt = over.systemPrompt ?: base.systemPrompt,
            systemRoleName = over.systemRoleName ?: base.systemRoleName,
            contextTruncationMode = over.contextTruncationMode ?: base.contextTruncationMode,
            maxContextMessages = over.maxContextMessages ?: base.maxContextMessages,
            maxContextTokens = over.maxContextTokens ?: base.maxContextTokens,
            dropStrategy = over.dropStrategy ?: base.dropStrategy,
            titleGeneratorModelKey = over.titleGeneratorModelKey ?: base.titleGeneratorModelKey,

            openRouterFallbackModels = over.openRouterFallbackModels ?: base.openRouterFallbackModels,
            openRouterProviderOrder = over.openRouterProviderOrder ?: base.openRouterProviderOrder,
            openRouterProviderOnly = over.openRouterProviderOnly ?: base.openRouterProviderOnly,
            openRouterProviderIgnore = over.openRouterProviderIgnore ?: base.openRouterProviderIgnore,
            openRouterQuantizations = over.openRouterQuantizations ?: base.openRouterQuantizations,
            openRouterMiddleOut = over.openRouterMiddleOut ?: base.openRouterMiddleOut,
            geminiSafetyThreshold = over.geminiSafetyThreshold ?: base.geminiSafetyThreshold,
            mistralSafePrompt = over.mistralSafePrompt ?: base.mistralSafePrompt,
            ollamaNumCtx = over.ollamaNumCtx ?: base.ollamaNumCtx,
            ollamaKeepAlive = over.ollamaKeepAlive ?: base.ollamaKeepAlive,

            requestPathOverride = over.requestPathOverride ?: base.requestPathOverride,
            connectTimeoutSec = over.connectTimeoutSec ?: base.connectTimeoutSec,
            readTimeoutSec = over.readTimeoutSec ?: base.readTimeoutSec,
            retryCount = over.retryCount ?: base.retryCount,
            retryBackoffMs = over.retryBackoffMs ?: base.retryBackoffMs,
            proxyHost = over.proxyHost ?: base.proxyHost,
            proxyPort = over.proxyPort ?: base.proxyPort,
            customHeadersJson = mergeJsonObjectStrings(base.customHeadersJson, over.customHeadersJson),
            extraBodyJson = mergeJsonObjectStrings(base.extraBodyJson, over.extraBodyJson),

            streamRenderThrottleMs = over.streamRenderThrottleMs ?: base.streamRenderThrottleMs,
            keepScreenAwake = over.keepScreenAwake ?: base.keepScreenAwake,
            hapticFeedback = over.hapticFeedback ?: base.hapticFeedback,
            renderMarkdown = over.renderMarkdown ?: base.renderMarkdown,
            renderLatex = over.renderLatex ?: base.renderLatex,
        )
    }

    fun mergeJsonObjectStrings(baseJson: String?, overrideJson: String?): String? {
        if (baseJson.isNullOrBlank() && overrideJson.isNullOrBlank()) return null
        if (baseJson.isNullOrBlank()) return overrideJson
        if (overrideJson.isNullOrBlank()) return baseJson
        return try {
            val baseObj = JSONObject(baseJson)
            val overObj = JSONObject(overrideJson)
            deepMergeJson(baseObj, overObj).toString()
        } catch (_: Exception) {
            overrideJson
        }
    }

    fun deepMergeJson(target: JSONObject, source: JSONObject): JSONObject {
        val result = JSONObject(target.toString())
        val keys = source.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = source.get(key)
            val existing = result.opt(key)
            if (value is JSONObject && existing is JSONObject) {
                result.put(key, deepMergeJson(existing, value))
            } else {
                result.put(key, value)
            }
        }
        return result
    }
}
