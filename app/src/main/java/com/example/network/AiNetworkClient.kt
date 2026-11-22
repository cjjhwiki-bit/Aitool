package com.example.network

import com.example.data.local.ModelHealthStatus
import com.example.data.local.ProviderEntity
import com.example.data.local.ProviderType
import com.example.data.model.GenerationParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

data class FetchedModelInfo(
    val modelId: String,
    val displayName: String,
    val isFree: Boolean,
    val contextLength: Int?,
)

data class ProbeOutcome(
    val status: ModelHealthStatus,
    val latencyMs: Long?,
    val errorMessage: String,
    val cooldownUntilMs: Long = 0L,
)

sealed interface StreamEvent {
    data class Delta(
        val contentDelta: String,
        val reasoningDelta: String,
        val ttftMs: Long?,
    ) : StreamEvent

    data class Completed(
        val fullContent: String,
        val fullReasoning: String,
        val promptTokens: Int?,
        val completionTokens: Int?,
        val ttftMs: Long?,
        val tps: Double?,
        val rateLimitInfoJson: String,
        val debugRequestJson: String,
        val debugResponseHeadersJson: String,
    ) : StreamEvent

    data class QuotaExceeded(
        val partialContent: String,
        val partialReasoning: String,
        val retryAfterSeconds: Long,
        val errorMessage: String,
        val rateLimitInfoJson: String,
        val debugRequestJson: String,
        val debugResponseHeadersJson: String,
    ) : StreamEvent

    data class Failed(
        val partialContent: String,
        val partialReasoning: String,
        val httpCode: Int?,
        val errorMessage: String,
        val debugRequestJson: String,
        val debugResponseHeadersJson: String,
    ) : StreamEvent
}

class AiNetworkClient(
    private val baseClient: OkHttpClient = OkHttpClient.Builder().build(),
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun buildClient(
        params: GenerationParameters,
        proxyCredentials: Pair<String, String> = "" to "",
    ): OkHttpClient {
        val connectSec = (params.connectTimeoutSec ?: 20).coerceIn(3, 120).toLong()
        val readSec = (params.readTimeoutSec ?: 90).coerceIn(10, 300).toLong()
        val builder = baseClient.newBuilder()
            .connectTimeout(connectSec, TimeUnit.SECONDS)
            .readTimeout(readSec, TimeUnit.SECONDS)
            .writeTimeout(connectSec, TimeUnit.SECONDS)

        val proxyHost = params.proxyHost?.trim().orEmpty()
        val proxyPort = params.proxyPort ?: 0
        if (proxyHost.isNotEmpty() && proxyPort in 1..65535) {
            builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyHost, proxyPort)))
            val (user, pass) = proxyCredentials
            if (user.isNotEmpty()) {
                builder.proxyAuthenticator { _, response ->
                    val credential = Credentials.basic(user, pass)
                    response.request.newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build()
                }
            }
        }
        return builder.build()
    }

    suspend fun fetchModels(
        provider: ProviderEntity,
        apiKey: String,
        params: GenerationParameters = GenerationParameters(),
        proxyCredentials: Pair<String, String> = "" to "",
    ): Result<List<FetchedModelInfo>> = withContext(Dispatchers.IO) {
        val url = ProviderAdapter.buildModelsUrl(provider)
        if (!ProviderAdapter.isValidHttpUrl(url)) {
            return@withContext Result.failure(IllegalArgumentException("無效的 Base URL 協定，僅支援 http:// 或 https://"))
        }

        val (headers, _) = ProviderAdapter.buildHeaders(provider, apiKey, params)
        val reqBuilder = Request.Builder().url(url).get()
        headers.forEach { (k, v) ->
            if (k != "Content-Type") reqBuilder.header(k, v)
        }

        return@withContext runCatching {
            val client = buildClient(params, proxyCredentials)
            client.newCall(reqBuilder.build()).execute().use { resp ->
                val rawBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException("HTTP ${resp.code}: ${extractErrorMessage(rawBody, resp.message)}")
                }
                parseModelsResponse(provider, rawBody)
            }
        }
    }

    private fun parseModelsResponse(provider: ProviderEntity, rawBody: String): List<FetchedModelInfo> {
        val root = JSONObject(rawBody)
        val dataArray = root.optJSONArray("data") ?: root.optJSONArray("models") ?: JSONArray()
        val list = mutableListOf<FetchedModelInfo>()
        for (i in 0 until dataArray.length()) {
            val item = dataArray.optJSONObject(i) ?: continue
            val id = item.optString("id").ifBlank { item.optString("name") }
            if (id.isBlank()) continue

            val displayName = item.optString("name").ifBlank { id }
            val ctx = when {
                item.has("context_length") -> item.optInt("context_length")
                item.has("context_window") -> item.optInt("context_window")
                else -> null
            }

            val isFree = when (provider.providerType()) {
                ProviderType.OPENROUTER -> {
                    val pricing = item.optJSONObject("pricing")
                    val promptPrice = pricing?.optString("prompt")?.toDoubleOrNull()
                    val completionPrice = pricing?.optString("completion")?.toDoubleOrNull()
                    id.endsWith(":free") || (promptPrice == 0.0 && completionPrice == 0.0)
                }
                else -> true
            }

            if (provider.providerType() == ProviderType.OPENROUTER && provider.openRouterFreeOnly && !isFree) {
                continue
            }

            list.add(
                FetchedModelInfo(
                    modelId = id,
                    displayName = displayName,
                    isFree = isFree,
                    contextLength = ctx,
                )
            )
        }
        return list.distinctBy { it.modelId }
    }

    suspend fun testConnection(
        provider: ProviderEntity,
        apiKey: String,
        params: GenerationParameters = GenerationParameters(),
        proxyCredentials: Pair<String, String> = "" to "",
    ): Result<String> = withContext(Dispatchers.IO) {
        val res = fetchModels(provider, apiKey, params, proxyCredentials)
        res.map { models -> "連線成功，共取得 ${models.size} 個模型" }
    }

    suspend fun probeModel(
        provider: ProviderEntity,
        modelId: String,
        apiKey: String,
        params: GenerationParameters = GenerationParameters(),
        proxyCredentials: Pair<String, String> = "" to "",
    ): ProbeOutcome = withContext(Dispatchers.IO) {
        val probeParams = params.copy(
            maxTokens = 1,
            stream = false,
            temperature = null,
            topP = null,
        )
        val built = ProviderAdapter.prepareChatRequest(
            provider = provider,
            modelId = modelId,
            apiKey = apiKey,
            messages = listOf(ChatMessagePayload("user", "hi")),
            params = probeParams,
        )
        if (!ProviderAdapter.isValidHttpUrl(built.url)) {
            return@withContext ProbeOutcome(ModelHealthStatus.UNAVAILABLE, null, "Base URL 無效")
        }

        val reqBuilder = Request.Builder()
            .url(built.url)
            .post(built.bodyJson.toRequestBody(jsonMediaType))
        built.headers.forEach { (k, v) -> reqBuilder.header(k, v) }

        val startMs = System.currentTimeMillis()
        return@withContext try {
            val client = buildClient(probeParams, proxyCredentials)
            client.newCall(reqBuilder.build()).execute().use { resp ->
                val elapsed = System.currentTimeMillis() - startMs
                val rawBody = resp.body?.string().orEmpty()
                when {
                    resp.isSuccessful -> ProbeOutcome(
                        status = ModelHealthStatus.AVAILABLE,
                        latencyMs = elapsed,
                        errorMessage = "",
                    )
                    resp.code == 429 || isQuotaError(resp.code, rawBody) -> {
                        val retrySec = parseRetryAfterSeconds(resp.header("Retry-After"), rawBody)
                        ProbeOutcome(
                            status = ModelHealthStatus.COOLDOWN,
                            latencyMs = elapsed,
                            errorMessage = "429 額度限制：${extractErrorMessage(rawBody, "Rate limited")}",
                            cooldownUntilMs = System.currentTimeMillis() + retrySec * 1000L,
                        )
                    }
                    else -> ProbeOutcome(
                        status = ModelHealthStatus.UNAVAILABLE,
                        latencyMs = elapsed,
                        errorMessage = "HTTP ${resp.code}: ${extractErrorMessage(rawBody, resp.message)}",
                    )
                }
            }
        } catch (e: Exception) {
            ProbeOutcome(
                status = ModelHealthStatus.UNAVAILABLE,
                latencyMs = null,
                errorMessage = e.localizedMessage ?: "網路連線失敗",
            )
        }
    }

    suspend fun streamChat(
        provider: ProviderEntity,
        modelId: String,
        apiKey: String,
        messages: List<ChatMessagePayload>,
        params: GenerationParameters,
        proxyCredentials: Pair<String, String> = "" to "",
        onEvent: suspend (StreamEvent) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val effectiveParams = if (params.stream == null) params.copy(stream = true) else params
        val built = ProviderAdapter.prepareChatRequest(
            provider = provider,
            modelId = modelId,
            apiKey = apiKey,
            messages = messages,
            params = effectiveParams,
        )
        val debugReq = JSONObject().apply {
            put("url", built.url)
            put("headers", JSONObject(built.maskedHeadersJson))
            put("body", runCatching { JSONObject(built.bodyJson) }.getOrDefault(built.bodyJson))
        }.toString(2)

        if (!ProviderAdapter.isValidHttpUrl(built.url)) {
            onEvent(StreamEvent.Failed("", "", null, "無效的請求網址協定", debugReq, "{}"))
            return@withContext
        }

        val maxRetries = (effectiveParams.retryCount ?: 0).coerceIn(0, 4)
        val baseBackoff = (effectiveParams.retryBackoffMs ?: 1000L).coerceIn(300L, 10000L)
        val client = buildClient(effectiveParams, proxyCredentials)

        var attempt = 0
        while (attempt <= maxRetries) {
            val reqBuilder = Request.Builder()
                .url(built.url)
                .post(built.bodyJson.toRequestBody(jsonMediaType))
            built.headers.forEach { (k, v) -> reqBuilder.header(k, v) }

            val startNs = System.nanoTime()
            var firstTokenNs: Long? = null
            val contentAcc = StringBuilder()
            val reasoningAcc = StringBuilder()
            var promptTokens: Int? = null
            var completionTokens: Int? = null
            var chunkCount = 0

            try {
                val call = client.newCall(reqBuilder.build())
                val resp = call.execute()
                resp.use { r ->
                    val respHeadersJson = headersToJson(r.headers)
                    val rateLimitJson = extractRateLimitInfo(r.headers)

                    if (!r.isSuccessful) {
                        val errBody = r.body?.string().orEmpty()
                        if (r.code == 429 || isQuotaError(r.code, errBody)) {
                            val retrySec = parseRetryAfterSeconds(r.header("Retry-After"), errBody)
                            onEvent(
                                StreamEvent.QuotaExceeded(
                                    partialContent = contentAcc.toString(),
                                    partialReasoning = reasoningAcc.toString(),
                                    retryAfterSeconds = retrySec,
                                    errorMessage = extractErrorMessage(errBody, "Rate limit / Quota exceeded"),
                                    rateLimitInfoJson = rateLimitJson,
                                    debugRequestJson = debugReq,
                                    debugResponseHeadersJson = respHeadersJson,
                                )
                            )
                            return@withContext
                        }

                        if (r.code in 500..599 && attempt < maxRetries) {
                            attempt++
                            delay(baseBackoff * attempt)
                            return@use
                        }

                        onEvent(
                            StreamEvent.Failed(
                                partialContent = contentAcc.toString(),
                                partialReasoning = reasoningAcc.toString(),
                                httpCode = r.code,
                                errorMessage = extractErrorMessage(errBody, r.message),
                                debugRequestJson = debugReq,
                                debugResponseHeadersJson = respHeadersJson,
                            )
                        )
                        return@withContext
                    }

                    val contentType = r.header("Content-Type").orEmpty().lowercase()
                    val isStreaming = effectiveParams.stream != false && !contentType.contains("application/json")

                    if (!isStreaming) {
                        val bodyStr = r.body?.string().orEmpty()
                        val ttftMs = (System.nanoTime() - startNs) / 1_000_000L
                        val obj = JSONObject(bodyStr)
                        val choices = obj.optJSONArray("choices")
                        val firstMsg = choices?.optJSONObject(0)?.optJSONObject("message")
                        val rawText = firstMsg?.optString("content").orEmpty()
                        val explicitReasoning = firstMsg?.optString("reasoning_content")
                            ?.ifBlank { firstMsg.optString("reasoning") }.orEmpty()
                        val (parsedText, tagReasoning) = extractInlineThinkTags(rawText)
                        val combinedReasoning = (explicitReasoning + if (tagReasoning.isNotEmpty()) "\n$tagReasoning" else "").trim()
                        val usage = obj.optJSONObject("usage")
                        promptTokens = usage?.optInt("prompt_tokens")?.takeIf { it > 0 }
                        completionTokens = usage?.optInt("completion_tokens")?.takeIf { it > 0 }
                        val elapsedSec = ((System.nanoTime() - startNs) / 1_000_000_000.0).coerceAtLeast(0.01)
                        val tps = completionTokens?.let { it / elapsedSec }

                        onEvent(
                            StreamEvent.Completed(
                                fullContent = parsedText,
                                fullReasoning = combinedReasoning,
                                promptTokens = promptTokens,
                                completionTokens = completionTokens,
                                ttftMs = ttftMs,
                                tps = tps,
                                rateLimitInfoJson = rateLimitJson,
                                debugRequestJson = debugReq,
                                debugResponseHeadersJson = respHeadersJson,
                            )
                        )
                        return@withContext
                    }

                    // SSE Streaming Loop
                    val reader: BufferedReader = r.body?.charStream()?.let { BufferedReader(it) }
                        ?: run {
                            onEvent(StreamEvent.Failed("", "", r.code, "空回應串流", debugReq, respHeadersJson))
                            return@withContext
                        }

                    var insideThinkTag = false
                    while (currentCoroutineContext().isActive) {
                        val line = reader.readLine() ?: break
                        if (line.isBlank()) continue
                        if (!line.startsWith("data:")) continue
                        val payload = line.removePrefix("data:").trim()
                        if (payload == "[DONE]") break

                        val json = runCatching { JSONObject(payload) }.getOrNull() ?: continue
                        // Check inline SSE error
                        if (json.has("error")) {
                            val errMsg = json.optJSONObject("error")?.optString("message").orEmpty().ifBlank { payload }
                            val code = json.optJSONObject("error")?.optInt("code")
                            if (code == 429 || isQuotaError(code ?: 0, errMsg)) {
                                onEvent(
                                    StreamEvent.QuotaExceeded(
                                        partialContent = contentAcc.toString(),
                                        partialReasoning = reasoningAcc.toString(),
                                        retryAfterSeconds = 60L,
                                        errorMessage = errMsg,
                                        rateLimitInfoJson = rateLimitJson,
                                        debugRequestJson = debugReq,
                                        debugResponseHeadersJson = respHeadersJson,
                                    )
                                )
                            } else {
                                onEvent(
                                    StreamEvent.Failed(
                                        partialContent = contentAcc.toString(),
                                        partialReasoning = reasoningAcc.toString(),
                                        httpCode = code,
                                        errorMessage = errMsg,
                                        debugRequestJson = debugReq,
                                        debugResponseHeadersJson = respHeadersJson,
                                    )
                                )
                            }
                            return@withContext
                        }

                        json.optJSONObject("usage")?.let { u ->
                            if (u.has("prompt_tokens")) promptTokens = u.optInt("prompt_tokens")
                            if (u.has("completion_tokens")) completionTokens = u.optInt("completion_tokens")
                        }

                        val choices = json.optJSONArray("choices") ?: continue
                        val choice0 = choices.optJSONObject(0) ?: continue
                        val delta = choice0.optJSONObject("delta") ?: choice0.optJSONObject("message") ?: continue

                        val rawReasoningDelta = when {
                            delta.has("reasoning_content") && !delta.isNull("reasoning_content") -> delta.optString("reasoning_content")
                            delta.has("reasoning") && !delta.isNull("reasoning") -> delta.optString("reasoning")
                            else -> ""
                        }
                        val rawContentDelta = if (delta.has("content") && !delta.isNull("content")) delta.optString("content") else ""

                        var contentPiece = ""
                        var reasoningPiece = rawReasoningDelta

                        if (rawContentDelta.isNotEmpty()) {
                            var remaining = rawContentDelta
                            while (remaining.isNotEmpty()) {
                                if (!insideThinkTag) {
                                    val openIdx = remaining.indexOf("<think>")
                                    if (openIdx >= 0) {
                                        contentPiece += remaining.substring(0, openIdx)
                                        remaining = remaining.substring(openIdx + 7)
                                        insideThinkTag = true
                                    } else {
                                        contentPiece += remaining
                                        remaining = ""
                                    }
                                } else {
                                    val closeIdx = remaining.indexOf("</think>")
                                    if (closeIdx >= 0) {
                                        reasoningPiece += remaining.substring(0, closeIdx)
                                        remaining = remaining.substring(closeIdx + 8)
                                        insideThinkTag = false
                                    } else {
                                        reasoningPiece += remaining
                                        remaining = ""
                                    }
                                }
                            }
                        }

                        if (contentPiece.isNotEmpty() || reasoningPiece.isNotEmpty()) {
                            if (firstTokenNs == null) {
                                firstTokenNs = System.nanoTime()
                            }
                            chunkCount++
                            contentAcc.append(contentPiece)
                            reasoningAcc.append(reasoningPiece)
                            onEvent(
                                StreamEvent.Delta(
                                    contentDelta = contentPiece,
                                    reasoningDelta = reasoningPiece,
                                    ttftMs = (firstTokenNs!! - startNs) / 1_000_000L,
                                )
                            )
                        }
                    }

                    val totalSec = ((System.nanoTime() - startNs) / 1_000_000_000.0).coerceAtLeast(0.05)
                    val estTokens = completionTokens ?: chunkCount
                    val tps = if (estTokens > 0) estTokens / totalSec else null
                    val ttftMs = firstTokenNs?.let { (it - startNs) / 1_000_000L }

                    onEvent(
                        StreamEvent.Completed(
                            fullContent = contentAcc.toString(),
                            fullReasoning = reasoningAcc.toString(),
                            promptTokens = promptTokens,
                            completionTokens = completionTokens,
                            ttftMs = ttftMs,
                            tps = tps,
                            rateLimitInfoJson = rateLimitJson,
                            debugRequestJson = debugReq,
                            debugResponseHeadersJson = respHeadersJson,
                        )
                    )
                    return@withContext
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                if (attempt < maxRetries) {
                    attempt++
                    delay(baseBackoff * attempt)
                } else {
                    onEvent(
                        StreamEvent.Failed(
                            partialContent = contentAcc.toString(),
                            partialReasoning = reasoningAcc.toString(),
                            httpCode = null,
                            errorMessage = e.localizedMessage ?: "串流連線中斷",
                            debugRequestJson = debugReq,
                            debugResponseHeadersJson = "{}",
                        )
                    )
                    return@withContext
                }
            }
        }
    }

    private fun extractInlineThinkTags(raw: String): Pair<String, String> {
        val regex = Regex("<think>([\\s\\S]*?)</think>")
        val thoughts = regex.findAll(raw).map { it.groupValues[1].trim() }.joinToString("\n")
        val cleaned = raw.replace(regex, "").trim()
        return cleaned to thoughts
    }

    private fun isQuotaError(code: Int, body: String): Boolean {
        if (code == 429) return true
        val lower = body.lowercase()
        return lower.contains("rate_limit") ||
            lower.contains("quota") ||
            lower.contains("too many requests") ||
            lower.contains("resource_exhausted")
    }

    private fun parseRetryAfterSeconds(headerVal: String?, body: String): Long {
        headerVal?.trim()?.toLongOrNull()?.let { if (it > 0) return it.coerceAtMost(3600) }
        val match = Regex("(\\d+)\\s*(?:s|sec|seconds)").find(body.lowercase())
        match?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { if (it > 0) return it.coerceAtMost(3600) }
        return 60L
    }

    private fun extractErrorMessage(rawBody: String, fallback: String): String {
        if (rawBody.isBlank()) return fallback
        return try {
            val obj = JSONObject(rawBody)
            when {
                obj.has("error") -> {
                    val err = obj.opt("error")
                    if (err is JSONObject) err.optString("message").ifBlank { rawBody }
                    else err?.toString().orEmpty().ifBlank { rawBody }
                }
                obj.has("message") -> obj.optString("message").ifBlank { rawBody }
                else -> rawBody.take(280)
            }
        } catch (_: Exception) {
            rawBody.take(280)
        }
    }

    private fun headersToJson(headers: okhttp3.Headers): String {
        val obj = JSONObject()
        for (i in 0 until headers.size) {
            obj.put(headers.name(i), headers.value(i))
        }
        return obj.toString(2)
    }

    private fun extractRateLimitInfo(headers: okhttp3.Headers): String {
        val obj = JSONObject()
        for (i in 0 until headers.size) {
            val name = headers.name(i).lowercase()
            if (name.contains("ratelimit") || name.contains("retry-after") || name.contains("x-quota")) {
                obj.put(headers.name(i), headers.value(i))
            }
        }
        return if (obj.length() > 0) obj.toString() else ""
    }
}
