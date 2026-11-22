package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class SearchMessageResult(
    val conversationId: String,
    val conversationTitle: String,
    val conversationMode: String,
    val turnId: String,
    val versionId: String,
    val role: String,
    val modelId: String,
    val providerName: String,
    val content: String,
    val reasoningContent: String,
    val createdAt: Long,
)

@Dao
interface ProviderDao {
    @Query("SELECT * FROM providers ORDER BY sortOrder ASC, name ASC")
    fun observeAll(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers ORDER BY sortOrder ASC, name ASC")
    suspend fun getAll(): List<ProviderEntity>

    @Query("SELECT * FROM providers WHERE id = :id")
    fun observeById(id: String): Flow<ProviderEntity?>

    @Query("SELECT * FROM providers WHERE id = :id")
    suspend fun getById(id: String): ProviderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(provider: ProviderEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(providers: List<ProviderEntity>)

    @Query("DELETE FROM providers WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ModelDao {
    @Query("SELECT * FROM models ORDER BY isSelected DESC, displayName ASC")
    fun observeAll(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE providerId = :providerId ORDER BY isSelected DESC, displayName ASC")
    fun observeByProvider(providerId: String): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models")
    suspend fun getAll(): List<ModelEntity>

    @Query("SELECT * FROM models WHERE `key` = :key")
    suspend fun getByKey(key: String): ModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(model: ModelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(models: List<ModelEntity>)

    @Query("DELETE FROM models WHERE providerId = :providerId AND `key` NOT IN (:keepKeys)")
    suspend fun deleteRemovedForProvider(providerId: String, keepKeys: List<String>)

    @Query("DELETE FROM models WHERE `key` = :key")
    suspend fun deleteByKey(key: String)
}

@Dao
interface CanonicalAliasDao {
    @Query("SELECT * FROM canonical_aliases ORDER BY isUserDefined DESC, canonicalFamilyId ASC")
    fun observeAll(): Flow<List<CanonicalAliasEntity>>

    @Query("SELECT * FROM canonical_aliases")
    suspend fun getAll(): List<CanonicalAliasEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alias: CanonicalAliasEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(aliases: List<CanonicalAliasEntity>)

    @Query("DELETE FROM canonical_aliases WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    suspend fun getAll(): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observeById(id: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM message_turns WHERE conversationId = :conversationId ORDER BY orderIndex ASC")
    fun observeTurns(conversationId: String): Flow<List<MessageTurnEntity>>

    @Query("SELECT * FROM message_turns WHERE conversationId = :conversationId ORDER BY orderIndex ASC")
    suspend fun getTurns(conversationId: String): List<MessageTurnEntity>

    @Query(
        """
        SELECT mv.* FROM message_versions mv
        INNER JOIN message_turns mt ON mv.turnId = mt.id
        WHERE mt.conversationId = :conversationId
        ORDER BY mt.orderIndex ASC, mv.branchKey ASC, mv.versionIndex ASC
        """
    )
    fun observeVersionsForConversation(conversationId: String): Flow<List<MessageVersionEntity>>

    @Query(
        """
        SELECT mv.* FROM message_versions mv
        INNER JOIN message_turns mt ON mv.turnId = mt.id
        WHERE mt.conversationId = :conversationId
        ORDER BY mt.orderIndex ASC, mv.branchKey ASC, mv.versionIndex ASC
        """
    )
    suspend fun getVersionsForConversation(conversationId: String): List<MessageVersionEntity>

    @Query("SELECT * FROM message_versions WHERE turnId = :turnId AND branchKey = :branchKey ORDER BY versionIndex ASC")
    suspend fun getVersionsForTurnAndBranch(turnId: String, branchKey: String): List<MessageVersionEntity>

    @Query("SELECT * FROM message_versions WHERE id = :versionId")
    suspend fun getVersionById(versionId: String): MessageVersionEntity?

    @Query("SELECT * FROM message_turns WHERE id = :turnId")
    suspend fun getTurnById(turnId: String): MessageTurnEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTurn(turn: MessageTurnEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVersion(version: MessageVersionEntity)

    @Update
    suspend fun updateVersion(version: MessageVersionEntity)

    @Query("UPDATE message_versions SET isActiveVersion = 0 WHERE turnId = :turnId AND branchKey = :branchKey")
    suspend fun deactivateBranchVersions(turnId: String, branchKey: String)

    @Query("UPDATE message_versions SET isActiveVersion = 1 WHERE id = :versionId")
    suspend fun activateVersion(versionId: String)

    @Query("DELETE FROM message_turns WHERE id = :turnId")
    suspend fun deleteTurnById(turnId: String)

    @Query("DELETE FROM message_versions WHERE id = :versionId")
    suspend fun deleteVersionById(versionId: String)

    @Query("DELETE FROM message_turns WHERE conversationId = :conversationId AND orderIndex > :afterOrderIndex")
    suspend fun deleteTurnsAfter(conversationId: String, afterOrderIndex: Int)

    @Query(
        """
        SELECT c.id AS conversationId, c.title AS conversationTitle, c.mode AS conversationMode,
               mt.id AS turnId, mv.id AS versionId, mt.role AS role,
               mv.modelId AS modelId, mv.providerName AS providerName,
               mv.content AS content, mv.reasoningContent AS reasoningContent,
               mv.createdAt AS createdAt
        FROM message_versions mv
        INNER JOIN message_turns mt ON mv.turnId = mt.id
        INNER JOIN conversations c ON mt.conversationId = c.id
        WHERE mv.isActiveVersion = 1
          AND (
              mv.content LIKE '%' || :query || '%'
              OR (:includeReasoning = 1 AND mv.reasoningContent LIKE '%' || :query || '%')
              OR c.title LIKE '%' || :query || '%'
          )
        ORDER BY mv.createdAt DESC
        LIMIT 100
        """
    )
    suspend fun searchMessages(query: String, includeReasoning: Boolean): List<SearchMessageResult>
}

@Dao
interface RoundtableDao {
    @Query("SELECT * FROM roundtable_participants WHERE conversationId = :conversationId ORDER BY sortOrder ASC")
    fun observeParticipants(conversationId: String): Flow<List<RoundtableParticipantEntity>>

    @Query("SELECT * FROM roundtable_participants WHERE conversationId = :conversationId ORDER BY sortOrder ASC")
    suspend fun getParticipants(conversationId: String): List<RoundtableParticipantEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(participants: List<RoundtableParticipantEntity>)

    @Query("DELETE FROM roundtable_participants WHERE conversationId = :conversationId")
    suspend fun deleteForConversation(conversationId: String)
}

@Dao
interface ParameterPresetDao {
    @Query("SELECT * FROM parameter_presets ORDER BY isBuiltIn DESC, updatedAt DESC")
    fun observeAll(): Flow<List<ParameterPresetEntity>>

    @Query("SELECT * FROM parameter_presets WHERE scope = :scope AND targetKey = :targetKey LIMIT 1")
    suspend fun getByScopeAndTarget(scope: String, targetKey: String): ParameterPresetEntity?

    @Query("SELECT * FROM parameter_presets WHERE id = :id")
    suspend fun getById(id: String): ParameterPresetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preset: ParameterPresetEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(presets: List<ParameterPresetEntity>)

    @Query("DELETE FROM parameter_presets WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface PromptTemplateDao {
    @Query("SELECT * FROM prompt_templates ORDER BY isBuiltIn DESC, title ASC")
    fun observeAll(): Flow<List<PromptTemplateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(template: PromptTemplateEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(templates: List<PromptTemplateEntity>)

    @Query("DELETE FROM prompt_templates WHERE id = :id")
    suspend fun deleteById(id: String)
}
