package com.example.domain

import com.example.data.local.AppDatabase
import com.example.data.local.ConversationEntity
import com.example.data.local.MessageRole
import com.example.data.local.MessageTurnEntity
import com.example.data.local.MessageVersionEntity
import com.example.data.local.RoundtableParticipantEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExportImportManager(private val db: AppDatabase) {

    suspend fun exportAllToJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("format", "polychat_backup_v1")
        root.put("exportedAt", System.currentTimeMillis())

        val convs = db.conversationDao().getAll()
        val convArray = JSONArray()
        convs.forEach { conv ->
            val cObj = JSONObject().apply {
                put("id", conv.id)
                put("title", conv.title)
                put("mode", conv.mode)
                put("activeModelKey", conv.activeModelKey ?: JSONObject.NULL)
                put("parallelModelKeysJson", conv.parallelModelKeysJson)
                put("parallelMergeStrategy", conv.parallelMergeStrategy)
                put("roundtableMaxTurns", conv.roundtableMaxTurns)
                put("roundtableDelayMs", conv.roundtableDelayMs)
                put("parameterOverrideJson", conv.parameterOverrideJson)
                put("activePresetId", conv.activePresetId ?: JSONObject.NULL)
                put("createdAt", conv.createdAt)
                put("updatedAt", conv.updatedAt)
            }

            val pArray = JSONArray()
            db.roundtableDao().getParticipants(conv.id).forEach { p ->
                pArray.put(JSONObject().apply {
                    put("id", p.id)
                    put("sortOrder", p.sortOrder)
                    put("modelKey", p.modelKey)
                    put("displayName", p.displayName)
                    put("systemPrompt", p.systemPrompt)
                })
            }
            cObj.put("participants", pArray)

            val turns = db.messageDao().getTurns(conv.id)
            val versionsByTurn = db.messageDao().getVersionsForConversation(conv.id).groupBy { it.turnId }
            val tArray = JSONArray()
            turns.forEach { t ->
                val tObj = JSONObject().apply {
                    put("id", t.id)
                    put("orderIndex", t.orderIndex)
                    put("role", t.role)
                    put("selectedBranchKey", t.selectedBranchKey ?: JSONObject.NULL)
                    put("createdAt", t.createdAt)
                }
                val vArray = JSONArray()
                versionsByTurn[t.id].orEmpty().forEach { v ->
                    vArray.put(JSONObject().apply {
                        put("id", v.id)
                        put("branchKey", v.branchKey)
                        put("versionIndex", v.versionIndex)
                        put("isActiveVersion", v.isActiveVersion)
                        put("content", v.content)
                        put("reasoningContent", v.reasoningContent)
                        put("providerId", v.providerId)
                        put("providerName", v.providerName)
                        put("modelId", v.modelId)
                        put("participantName", v.participantName)
                        put("isFailover", v.isFailover)
                        put("failoverFromLabel", v.failoverFromLabel)
                        put("status", v.status)
                        put("errorMessage", v.errorMessage)
                        put("paramSnapshotJson", v.paramSnapshotJson)
                        put("promptTokens", v.promptTokens ?: JSONObject.NULL)
                        put("completionTokens", v.completionTokens ?: JSONObject.NULL)
                        put("ttftMs", v.ttftMs ?: JSONObject.NULL)
                        put("tps", v.tps ?: JSONObject.NULL)
                        put("createdAt", v.createdAt)
                    })
                }
                tObj.put("versions", vArray)
                tArray.put(tObj)
            }
            cObj.put("turns", tArray)
            convArray.put(cObj)
        }
        root.put("conversations", convArray)
        root.toString(2)
    }

    suspend fun importFromJson(jsonString: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject(jsonString)
            require(root.optString("format").startsWith("polychat_backup")) { "不支援的備份檔案格式" }
            val convArray = root.getJSONArray("conversations")
            var importedCount = 0

            for (i in 0 until convArray.length()) {
                val cObj = convArray.getJSONObject(i)
                val conv = ConversationEntity(
                    id = cObj.getString("id"),
                    title = cObj.optString("title", "匯入對話"),
                    mode = cObj.optString("mode", "SINGLE"),
                    activeModelKey = cObj.optString("activeModelKey").takeIf { it.isNotBlank() && it != "null" },
                    parallelModelKeysJson = cObj.optString("parallelModelKeysJson", "[]"),
                    parallelMergeStrategy = cObj.optString("parallelMergeStrategy", "CONVERGE"),
                    roundtableMaxTurns = cObj.optInt("roundtableMaxTurns", 6),
                    roundtableDelayMs = cObj.optLong("roundtableDelayMs", 1000L),
                    parameterOverrideJson = cObj.optString("parameterOverrideJson", ""),
                    activePresetId = cObj.optString("activePresetId").takeIf { it.isNotBlank() && it != "null" },
                    createdAt = cObj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = cObj.optLong("updatedAt", System.currentTimeMillis()),
                )
                db.conversationDao().upsert(conv)

                val pArray = cObj.optJSONArray("participants")
                if (pArray != null && pArray.length() > 0) {
                    val participants = buildList {
                        for (j in 0 until pArray.length()) {
                            val p = pArray.getJSONObject(j)
                            add(
                                RoundtableParticipantEntity(
                                    id = p.getString("id"),
                                    conversationId = conv.id,
                                    sortOrder = p.optInt("sortOrder", j),
                                    modelKey = p.getString("modelKey"),
                                    displayName = p.getString("displayName"),
                                    systemPrompt = p.optString("systemPrompt", ""),
                                )
                            )
                        }
                    }
                    db.roundtableDao().upsertAll(participants)
                }

                val tArray = cObj.optJSONArray("turns") ?: JSONArray()
                for (tIdx in 0 until tArray.length()) {
                    val tObj = tArray.getJSONObject(tIdx)
                    val turn = MessageTurnEntity(
                        id = tObj.getString("id"),
                        conversationId = conv.id,
                        orderIndex = tObj.optInt("orderIndex", tIdx),
                        role = tObj.optString("role", MessageRole.USER.name),
                        selectedBranchKey = tObj.optString("selectedBranchKey").takeIf { it.isNotBlank() && it != "null" },
                        createdAt = tObj.optLong("createdAt", System.currentTimeMillis()),
                    )
                    db.messageDao().upsertTurn(turn)

                    val vArray = tObj.optJSONArray("versions") ?: JSONArray()
                    for (vIdx in 0 until vArray.length()) {
                        val vObj = vArray.getJSONObject(vIdx)
                        val ver = MessageVersionEntity(
                            id = vObj.getString("id"),
                            turnId = turn.id,
                            branchKey = vObj.optString("branchKey", "default"),
                            versionIndex = vObj.optInt("versionIndex", 0),
                            isActiveVersion = vObj.optBoolean("isActiveVersion", true),
                            content = vObj.optString("content", ""),
                            reasoningContent = vObj.optString("reasoningContent", ""),
                            providerId = vObj.optString("providerId", ""),
                            providerName = vObj.optString("providerName", ""),
                            modelId = vObj.optString("modelId", ""),
                            participantName = vObj.optString("participantName", ""),
                            isFailover = vObj.optBoolean("isFailover", false),
                            failoverFromLabel = vObj.optString("failoverFromLabel", ""),
                            status = vObj.optString("status", "COMPLETED"),
                            errorMessage = vObj.optString("errorMessage", ""),
                            paramSnapshotJson = vObj.optString("paramSnapshotJson", ""),
                            promptTokens = vObj.optInt("promptTokens").takeIf { vObj.has("promptTokens") && !vObj.isNull("promptTokens") },
                            completionTokens = vObj.optInt("completionTokens").takeIf { vObj.has("completionTokens") && !vObj.isNull("completionTokens") },
                            ttftMs = vObj.optLong("ttftMs").takeIf { vObj.has("ttftMs") && !vObj.isNull("ttftMs") },
                            tps = vObj.optDouble("tps").takeIf { vObj.has("tps") && !vObj.isNull("tps") },
                            createdAt = vObj.optLong("createdAt", System.currentTimeMillis()),
                        )
                        db.messageDao().upsertVersion(ver)
                    }
                }
                importedCount++
            }
            importedCount
        }
    }

    suspend fun exportConversationToMarkdown(conversationId: String): String = withContext(Dispatchers.IO) {
        val conv = db.conversationDao().getById(conversationId) ?: return@withContext ""
        val turns = db.messageDao().getTurns(conversationId)
        val versionsByTurn = db.messageDao().getVersionsForConversation(conversationId).groupBy { it.turnId }
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        buildString {
            appendLine("# ${conv.title}")
            appendLine("- 模式：${conv.mode}")
            appendLine("- 匯出時間：${fmt.format(Date())}")
            appendLine()
            turns.forEach { turn ->
                val actives = versionsByTurn[turn.id].orEmpty().filter { it.isActiveVersion }
                when (turn.messageRole()) {
                    MessageRole.USER -> {
                        val txt = actives.firstOrNull()?.content.orEmpty()
                        appendLine("## 使用者")
                        appendLine(txt)
                        appendLine()
                    }
                    MessageRole.SYSTEM_INTERJECTION -> {
                        val txt = actives.firstOrNull()?.content.orEmpty()
                        appendLine("> **主持人插話引導**：$txt")
                        appendLine()
                    }
                    MessageRole.ASSISTANT -> {
                        actives.forEach { ver ->
                            val header = buildString {
                                append("## ")
                                if (ver.participantName.isNotBlank()) append("${ver.participantName} · ")
                                append("${ver.providerName} · ${ver.modelId}")
                                if (ver.isFailover) append(" (備援切換 ↻)")
                            }
                            appendLine(header)
                            if (ver.reasoningContent.isNotBlank()) {
                                appendLine("<details><summary>思考過程</summary>\n")
                                appendLine(ver.reasoningContent)
                                appendLine("\n</details>\n")
                            }
                            appendLine(ver.content)
                            appendLine()
                        }
                    }
                }
            }
        }
    }
}
