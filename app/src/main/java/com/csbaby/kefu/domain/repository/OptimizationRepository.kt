package com.csbaby.kefu.domain.repository

import com.csbaby.kefu.domain.model.OptimizationEvent
import com.csbaby.kefu.domain.model.OptimizationMetrics
import kotlinx.coroutines.flow.Flow

interface OptimizationRepository {
    // Metrics methods
    fun getMetricsByFeatureKey(featureKey: String): Flow<List<OptimizationMetrics>>
    suspend fun getMetricsByFeatureKeyAndDate(featureKey: String, date: String): OptimizationMetrics?
    suspend fun getMetricsByVariantAndDateRange(variantId: Long, startDate: String, endDate: String): List<OptimizationMetrics>
    suspend fun getMetricsByFeatureKeyAndDateRange(featureKey: String, startDate: String, endDate: String): List<OptimizationMetrics>
    suspend fun upsertMetrics(metrics: OptimizationMetrics)

    // Event methods
    fun getEventsByFeatureKey(featureKey: String): Flow<List<OptimizationEvent>>
    fun getAllEvents(): Flow<List<OptimizationEvent>>
    suspend fun insertEvent(event: OptimizationEvent): Long
}
