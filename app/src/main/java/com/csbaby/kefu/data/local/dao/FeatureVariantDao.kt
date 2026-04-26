package com.csbaby.kefu.data.local.dao

import androidx.room.*
import com.csbaby.kefu.data.local.entity.FeatureVariantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FeatureVariantDao {
    @Query("SELECT * FROM feature_variants WHERE featureId = :featureId ORDER BY createdAt DESC")
    fun getByFeatureId(featureId: Long): Flow<List<FeatureVariantEntity>>

    @Query("SELECT * FROM feature_variants WHERE id = :id")
    suspend fun getById(id: Long): FeatureVariantEntity?

    @Query("SELECT * FROM feature_variants WHERE featureId = :featureId AND isActive = 1")
    suspend fun getActiveVariants(featureId: Long): List<FeatureVariantEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FeatureVariantEntity): Long

    @Update
    suspend fun update(entity: FeatureVariantEntity)

    @Query("DELETE FROM feature_variants WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE feature_variants SET isActive = 0 WHERE featureId = :featureId")
    suspend fun deactivateAllByFeatureId(featureId: Long)

    @Query("UPDATE feature_variants SET isActive = :isActive WHERE id = :id")
    suspend fun setActive(id: Long, isActive: Boolean)

    @Query("UPDATE feature_variants SET trafficPercentage = :percentage WHERE id = :id")
    suspend fun setTrafficPercentage(id: Long, percentage: Int)
}
