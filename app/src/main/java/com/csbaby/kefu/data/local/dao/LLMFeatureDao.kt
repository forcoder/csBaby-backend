package com.csbaby.kefu.data.local.dao

import androidx.room.*
import com.csbaby.kefu.data.local.entity.LLMFeatureEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LLMFeatureDao {
    @Query("SELECT * FROM llm_features ORDER BY createdAt DESC")
    fun getAll(): Flow<List<LLMFeatureEntity>>

    @Query("SELECT * FROM llm_features WHERE id = :id")
    suspend fun getById(id: Long): LLMFeatureEntity?

    @Query("SELECT * FROM llm_features WHERE featureKey = :featureKey")
    suspend fun getByFeatureKey(featureKey: String): LLMFeatureEntity?

    @Query("SELECT * FROM llm_features WHERE isEnabled = 1 ORDER BY createdAt DESC")
    fun getEnabled(): Flow<List<LLMFeatureEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LLMFeatureEntity): Long

    @Update
    suspend fun update(entity: LLMFeatureEntity)

    @Query("DELETE FROM llm_features WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE llm_features SET defaultVariantId = :variantId, updatedAt = :timestamp WHERE featureKey = :featureKey")
    suspend fun updateDefaultVariant(featureKey: String, variantId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE llm_features SET isEnabled = :enabled, updatedAt = :timestamp WHERE featureKey = :featureKey")
    suspend fun setEnabled(featureKey: String, enabled: Boolean, timestamp: Long = System.currentTimeMillis())
}
