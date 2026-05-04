package com.csbaby.kefu.data.local.dao

import androidx.room.*
import com.csbaby.kefu.data.local.entity.KeywordRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KeywordRuleDao {
    @Query("SELECT * FROM keyword_rules WHERE tenantId = :tenantId ORDER BY priority DESC, createdAt DESC")
    fun getAllRules(tenantId: String): Flow<List<KeywordRuleEntity>>

    @Query("SELECT * FROM keyword_rules WHERE enabled = 1 AND tenantId = :tenantId ORDER BY priority DESC")
    fun getEnabledRules(tenantId: String): Flow<List<KeywordRuleEntity>>

    @Query("SELECT * FROM keyword_rules WHERE category = :category AND tenantId = :tenantId ORDER BY priority DESC")
    fun getRulesByCategory(category: String, tenantId: String): Flow<List<KeywordRuleEntity>>

    @Query("SELECT * FROM keyword_rules WHERE id = :id AND tenantId = :tenantId")
    suspend fun getRuleById(id: Long, tenantId: String): KeywordRuleEntity?

    @Query("SELECT * FROM keyword_rules WHERE keyword LIKE '%' || :keyword || '%' AND tenantId = :tenantId")
    suspend fun searchByKeyword(keyword: String, tenantId: String): List<KeywordRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: KeywordRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<KeywordRuleEntity>)

    @Update
    suspend fun updateRule(rule: KeywordRuleEntity)

    @Delete
    suspend fun deleteRule(rule: KeywordRuleEntity)

    @Query("DELETE FROM keyword_rules WHERE id = :id AND tenantId = :tenantId")
    suspend fun deleteById(id: Long, tenantId: String)

    @Query("DELETE FROM keyword_rules WHERE tenantId = :tenantId")
    suspend fun deleteAllRules(tenantId: String)

    @Query("SELECT COUNT(*) FROM keyword_rules WHERE tenantId = :tenantId")
    suspend fun getRuleCount(tenantId: String): Int

    @Query("SELECT COUNT(*) FROM keyword_rules WHERE tenantId = :tenantId")
    fun getRuleCountFlow(tenantId: String): Flow<Int>

    @Query("SELECT DISTINCT category FROM keyword_rules WHERE tenantId = :tenantId")
    fun getAllCategories(tenantId: String): Flow<List<String>>

    @Query("SELECT * FROM keyword_rules WHERE tenantId = :tenantId ORDER BY priority DESC, createdAt DESC")
    suspend fun getAllRulesList(tenantId: String): List<KeywordRuleEntity>
}
