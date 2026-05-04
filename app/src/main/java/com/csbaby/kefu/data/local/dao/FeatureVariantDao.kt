package com.csbaby.kefu.data.local.dao

import androidx.room.*
import com.csbaby.kefu.data.local.entity.FeatureVariantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FeatureVariantDao {
    @Query("SELECT * FROM feature_variants WHERE featureId = :featureId AND tenantId = :tenantId ORDER BY createdAt DESC")
    fun getByFeatureId(featureId: Long, tenantId: String): Flow<List<FeatureVariantEntity>>

    @Query("SELECT * FROM feature_variants WHERE id = :id AND tenantId = :tenantId")
    suspend fun getById(id: Long, tenantId: String): FeatureVariantEntity?

    @Query("SELECT * FROM feature_variants WHERE featureId = :featureId AND isActive = 1 AND tenantId = :tenantId")
    suspend fun getActiveVariants(featureId: Long, tenantId: String): List<FeatureVariantEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FeatureVariantEntity): Long

    @Update
    suspend fun update(entity: FeatureVariantEntity)

    @Query("DELETE FROM feature_variants WHERE id = :id AND tenantId = :tenantId")
    suspend fun deleteById(id: Long, tenantId: String)

    @Query("UPDATE feature_variants SET isActive = 0 WHERE featureId = :featureId AND tenantId = :tenantId")
    suspend fun deactivateAllByFeatureId(featureId: Long, tenantId: String)

    @Query("UPDATE feature_variants SET isActive = :isActive WHERE id = :id AND tenantId = :tenantId")
    suspend fun setActive(id: Long, tenantId: String, isActive: Boolean)

    @Query("UPDATE feature_variants SET trafficPercentage = :percentage WHERE id = :id AND tenantId = :tenantId")
    suspend fun setTrafficPercentage(id: Long, tenantId: String, percentage: Int)
}
