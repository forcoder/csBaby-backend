package com.csbaby.kefu.data.local.dao

import androidx.room.*
import com.csbaby.kefu.data.local.entity.LLMFeatureEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LLMFeatureDao {
    @Query("SELECT * FROM llm_features WHERE tenantId = :tenantId ORDER BY createdAt DESC")
    fun getAll(tenantId: String): Flow<List<LLMFeatureEntity>>

    @Query("SELECT * FROM llm_features WHERE id = :id AND tenantId = :tenantId")
    suspend fun getById(id: Long, tenantId: String): LLMFeatureEntity?

    @Query("SELECT * FROM llm_features WHERE featureKey = :featureKey AND tenantId = :tenantId")
    suspend fun getByFeatureKey(featureKey: String, tenantId: String): LLMFeatureEntity?

    @Query("SELECT * FROM llm_features WHERE isEnabled = 1 AND tenantId = :tenantId ORDER BY createdAt DESC")
    fun getEnabled(tenantId: String): Flow<List<LLMFeatureEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LLMFeatureEntity): Long

    @Update
    suspend fun update(entity: LLMFeatureEntity)

    @Query("DELETE FROM llm_features WHERE id = :id AND tenantId = :tenantId")
    suspend fun deleteById(id: Long, tenantId: String)

    @Query("UPDATE llm_features SET defaultVariantId = :variantId, updatedAt = :timestamp WHERE featureKey = :featureKey AND tenantId = :tenantId")
    suspend fun updateDefaultVariant(featureKey: String, tenantId: String, variantId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE llm_features SET isEnabled = :enabled, updatedAt = :timestamp WHERE featureKey = :featureKey AND tenantId = :tenantId")
    suspend fun setEnabled(featureKey: String, tenantId: String, enabled: Boolean, timestamp: Long = System.currentTimeMillis())
}
