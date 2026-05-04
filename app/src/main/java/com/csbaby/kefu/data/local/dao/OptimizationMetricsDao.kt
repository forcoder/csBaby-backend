package com.csbaby.kefu.data.local.dao

import androidx.room.*
import com.csbaby.kefu.data.local.entity.OptimizationMetricsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OptimizationMetricsDao {
    @Query("SELECT * FROM optimization_metrics WHERE featureKey = :featureKey AND tenantId = :tenantId ORDER BY date DESC")
    fun getByFeatureKey(featureKey: String, tenantId: String): Flow<List<OptimizationMetricsEntity>>

    @Query("SELECT * FROM optimization_metrics WHERE featureKey = :featureKey AND date = :date AND tenantId = :tenantId")
    suspend fun getByFeatureKeyAndDate(featureKey: String, date: String, tenantId: String): OptimizationMetricsEntity?

    @Query("SELECT * FROM optimization_metrics WHERE variantId = :variantId AND date BETWEEN :startDate AND :endDate AND tenantId = :tenantId ORDER BY date ASC")
    suspend fun getByVariantAndDateRange(variantId: Long, startDate: String, endDate: String, tenantId: String): List<OptimizationMetricsEntity>

    @Query("SELECT * FROM optimization_metrics WHERE featureKey = :featureKey AND date BETWEEN :startDate AND :endDate AND tenantId = :tenantId ORDER BY date ASC")
    suspend fun getByFeatureKeyAndDateRange(featureKey: String, startDate: String, endDate: String, tenantId: String): List<OptimizationMetricsEntity>

    @Insert
    suspend fun insert(entity: OptimizationMetricsEntity): Long

    @Update
    suspend fun update(entity: OptimizationMetricsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OptimizationMetricsEntity)

    @Query("DELETE FROM optimization_metrics WHERE id = :id AND tenantId = :tenantId")
    suspend fun deleteById(id: Long, tenantId: String)
}
