package com.csbaby.kefu.data.local.dao

import androidx.room.*
import com.csbaby.kefu.data.local.entity.OptimizationMetricsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OptimizationMetricsDao {
    @Query("SELECT * FROM optimization_metrics WHERE featureKey = :featureKey ORDER BY date DESC")
    fun getByFeatureKey(featureKey: String): Flow<List<OptimizationMetricsEntity>>

    @Query("SELECT * FROM optimization_metrics WHERE featureKey = :featureKey AND date = :date")
    suspend fun getByFeatureKeyAndDate(featureKey: String, date: String): OptimizationMetricsEntity?

    @Query("SELECT * FROM optimization_metrics WHERE variantId = :variantId AND date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    suspend fun getByVariantAndDateRange(variantId: Long, startDate: String, endDate: String): List<OptimizationMetricsEntity>

    @Query("SELECT * FROM optimization_metrics WHERE featureKey = :featureKey AND date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    suspend fun getByFeatureKeyAndDateRange(featureKey: String, startDate: String, endDate: String): List<OptimizationMetricsEntity>

    @Insert
    suspend fun insert(entity: OptimizationMetricsEntity): Long

    @Update
    suspend fun update(entity: OptimizationMetricsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OptimizationMetricsEntity)

    @Query("DELETE FROM optimization_metrics WHERE id = :id")
    suspend fun deleteById(id: Long)
}
