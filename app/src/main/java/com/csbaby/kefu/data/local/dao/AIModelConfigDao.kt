package com.csbaby.kefu.data.local.dao

import androidx.room.*
import com.csbaby.kefu.data.local.entity.AIModelConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AIModelConfigDao {
    @Query("SELECT * FROM ai_model_configs WHERE tenantId = :tenantId ORDER BY isDefault DESC, lastUsed DESC")
    fun getAllModels(tenantId: String): Flow<List<AIModelConfigEntity>>

    @Query("SELECT * FROM ai_model_configs WHERE isEnabled = 1 AND tenantId = :tenantId ORDER BY isDefault DESC, lastUsed DESC")
    fun getEnabledModels(tenantId: String): Flow<List<AIModelConfigEntity>>

    @Query("SELECT * FROM ai_model_configs WHERE isDefault = 1 AND tenantId = :tenantId LIMIT 1")
    suspend fun getDefaultModel(tenantId: String): AIModelConfigEntity?

    @Query("SELECT * FROM ai_model_configs WHERE id = :id AND tenantId = :tenantId")
    suspend fun getModelById(id: Long, tenantId: String): AIModelConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: AIModelConfigEntity): Long

    @Update
    suspend fun updateModel(model: AIModelConfigEntity)

    @Delete
    suspend fun deleteModel(model: AIModelConfigEntity)

    @Query("DELETE FROM ai_model_configs WHERE id = :id AND tenantId = :tenantId")
    suspend fun deleteById(id: Long, tenantId: String)

    @Query("UPDATE ai_model_configs SET isDefault = 0 WHERE isDefault = 1 AND tenantId = :tenantId")
    suspend fun clearDefaultModel(tenantId: String)

    @Query("UPDATE ai_model_configs SET isDefault = 1 WHERE id = :id AND tenantId = :tenantId")
    suspend fun setDefaultModel(id: Long, tenantId: String)

    @Query("UPDATE ai_model_configs SET lastUsed = :timestamp WHERE id = :id AND tenantId = :tenantId")
    suspend fun updateLastUsed(id: Long, tenantId: String, timestamp: Long)

    @Query("UPDATE ai_model_configs SET monthlyCost = monthlyCost + :cost WHERE id = :id AND tenantId = :tenantId")
    suspend fun addCost(id: Long, tenantId: String, cost: Double)

    @Query("SELECT * FROM ai_model_configs WHERE tenantId = :tenantId ORDER BY isDefault DESC, lastUsed DESC")
    suspend fun getAllModelsList(tenantId: String): List<AIModelConfigEntity>
}
