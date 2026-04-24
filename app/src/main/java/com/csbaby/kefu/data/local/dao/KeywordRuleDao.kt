package com.csbaby.kefu.data.local.dao

import androidx.room.*
import com.csbaby.kefu.data.local.entity.KeywordRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KeywordRuleDao {
    @Query("SELECT * FROM keyword_rules ORDER BY priority DESC, createdAt DESC")
    fun getAllRules(): Flow<List<KeywordRuleEntity>>

    @Query("SELECT * FROM keyword_rules WHERE enabled = 1 ORDER BY priority DESC")
    fun getEnabledRules(): Flow<List<KeywordRuleEntity>>

    @Query("SELECT * FROM keyword_rules WHERE category = :category ORDER BY priority DESC")
    fun getRulesByCategory(category: String): Flow<List<KeywordRuleEntity>>

    @Query("SELECT * FROM keyword_rules WHERE id = :id")
    suspend fun getRuleById(id: Long): KeywordRuleEntity?

    @Query("SELECT * FROM keyword_rules WHERE keyword LIKE '%' || :keyword || '%'")
    suspend fun searchByKeyword(keyword: String): List<KeywordRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: KeywordRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<KeywordRuleEntity>)

    @Update
    suspend fun updateRule(rule: KeywordRuleEntity)

    @Delete
    suspend fun deleteRule(rule: KeywordRuleEntity)

    @Query("DELETE FROM keyword_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM keyword_rules")
    suspend fun deleteAllRules()

    @Query("SELECT COUNT(*) FROM keyword_rules")
    suspend fun getRuleCount(): Int

    @Query("SELECT COUNT(*) FROM keyword_rules")
    fun getRuleCountFlow(): Flow<Int>

    @Query("SELECT DISTINCT category FROM keyword_rules")

    fun getAllCategories(): Flow<List<String>>
}
