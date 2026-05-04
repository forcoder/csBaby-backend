package com.csbaby.kefu.data.repository

import com.csbaby.kefu.data.local.AuthManager
import com.csbaby.kefu.data.local.EntityMapper.toDomain
import com.csbaby.kefu.data.local.EntityMapper.toEntity
import com.csbaby.kefu.data.local.dao.OptimizationEventDao
import com.csbaby.kefu.data.local.dao.OptimizationMetricsDao
import com.csbaby.kefu.domain.model.OptimizationEvent
import com.csbaby.kefu.domain.model.OptimizationMetrics
import com.csbaby.kefu.domain.repository.OptimizationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OptimizationRepositoryImpl @Inject constructor(
    private val optimizationMetricsDao: OptimizationMetricsDao,
    private val optimizationEventDao: OptimizationEventDao,
    private val authManager: AuthManager
) : OptimizationRepository {

    // Metrics methods
    override fun getMetricsByFeatureKey(featureKey: String): Flow<List<OptimizationMetrics>> {
        val tenantId = authManager.getTenantId() ?: ""
        return optimizationMetricsDao.getByFeatureKey(featureKey, tenantId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getMetricsByFeatureKeyAndDate(featureKey: String, date: String): OptimizationMetrics? {
        val tenantId = authManager.getTenantId() ?: ""
        return optimizationMetricsDao.getByFeatureKeyAndDate(featureKey, date, tenantId)?.toDomain()
    }

    override suspend fun getMetricsByVariantAndDateRange(variantId: Long, startDate: String, endDate: String): List<OptimizationMetrics> {
        val tenantId = authManager.getTenantId() ?: ""
        return optimizationMetricsDao.getByVariantAndDateRange(variantId, startDate, endDate, tenantId).map { it.toDomain() }
    }

    override suspend fun getMetricsByFeatureKeyAndDateRange(featureKey: String, startDate: String, endDate: String): List<OptimizationMetrics> {
        val tenantId = authManager.getTenantId() ?: ""
        return optimizationMetricsDao.getByFeatureKeyAndDateRange(featureKey, startDate, endDate, tenantId).map { it.toDomain() }
    }

    override suspend fun upsertMetrics(metrics: OptimizationMetrics) {
        optimizationMetricsDao.upsert(metrics.toEntity())
    }

    // Event methods
    override fun getEventsByFeatureKey(featureKey: String): Flow<List<OptimizationEvent>> {
        val tenantId = authManager.getTenantId() ?: ""
        return optimizationEventDao.getByFeatureKey(featureKey, tenantId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getAllEvents(): Flow<List<OptimizationEvent>> {
        val tenantId = authManager.getTenantId() ?: ""
        return optimizationEventDao.getAll(tenantId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun insertEvent(event: OptimizationEvent): Long {
        return optimizationEventDao.insert(event.toEntity())
    }
}
