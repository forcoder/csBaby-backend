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

    // 新增的性能监控查询方法

    /**
     * 获取功能性能指标摘要
     */
    @Query("""
        SELECT featureKey,
               AVG(accuracyScore) as avgAccuracy,
               AVG(avgResponseTimeMs) as avgResponseTime,
               SUM(totalGenerated) as totalGenerated,
               SUM(totalAccepted) as totalAccepted,
               SUM(totalModified) as totalModified,
               SUM(totalRejected) as totalRejected
        FROM optimization_metrics
        WHERE date BETWEEN :startDate AND :endDate
        GROUP BY featureKey
        ORDER BY avgAccuracy DESC
    """)
    suspend fun getFeaturePerformanceSummary(startDate: String, endDate: String): List<Map<String, Any>>

    /**
     * 获取变体性能对比数据
     */
    @Query("""
        SELECT variantId,
               AVG(accuracyScore) as avgAccuracy,
               AVG(avgResponseTimeMs) as avgResponseTime,
               COUNT(*) as recordCount
        FROM optimization_metrics
        WHERE featureKey = :featureKey AND date BETWEEN :startDate AND :endDate
        GROUP BY variantId
        ORDER BY avgAccuracy DESC
    """)
    suspend fun getVariantPerformanceComparison(
        featureKey: String,
        startDate: String,
        endDate: String
    ): List<VariantPerformance>

    /**
     * 批量插入优化指标
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(metrics: List<OptimizationMetricsEntity>): List<Long>

    /**
     * 清理过期数据
     */
    @Query("DELETE FROM optimization_metrics WHERE date < :cutoffDate")
    suspend fun deleteOldMetrics(cutoffDate: String): Int

    /**
     * 获取总记录数（用于分页）
     */
    @Query("SELECT COUNT(*) FROM optimization_metrics")
    suspend fun getTotalRecordCount(): Int

    /**
     * 按日期范围获取记录数
     */
    @Query("SELECT COUNT(*) FROM optimization_metrics WHERE date BETWEEN :startDate AND :endDate")
    suspend fun getRecordCountByDateRange(startDate: String, endDate: String): Int
}
