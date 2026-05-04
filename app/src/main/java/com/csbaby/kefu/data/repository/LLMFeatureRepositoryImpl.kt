package com.csbaby.kefu.data.repository

import com.csbaby.kefu.data.local.AuthManager
import com.csbaby.kefu.data.local.EntityMapper.toDomain
import com.csbaby.kefu.data.local.EntityMapper.toEntity
import com.csbaby.kefu.data.local.dao.FeatureVariantDao
import com.csbaby.kefu.data.local.dao.LLMFeatureDao
import com.csbaby.kefu.domain.model.FeatureVariant
import com.csbaby.kefu.domain.model.LLMFeature
import com.csbaby.kefu.domain.repository.LLMFeatureRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LLMFeatureRepositoryImpl @Inject constructor(
    private val llmFeatureDao: LLMFeatureDao,
    private val featureVariantDao: FeatureVariantDao,
    private val authManager: AuthManager
) : LLMFeatureRepository {

    // Feature methods
    override fun getAllFeatures(): Flow<List<LLMFeature>> {
        val tenantId = authManager.getTenantId() ?: ""
        return llmFeatureDao.getAll(tenantId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getEnabledFeatures(): Flow<List<LLMFeature>> {
        val tenantId = authManager.getTenantId() ?: ""
        return llmFeatureDao.getEnabled(tenantId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getFeatureByFeatureKey(featureKey: String): LLMFeature? {
        val tenantId = authManager.getTenantId() ?: ""
        return llmFeatureDao.getByFeatureKey(featureKey, tenantId)?.toDomain()
    }

    override suspend fun getFeatureById(id: Long): LLMFeature? {
        val tenantId = authManager.getTenantId() ?: ""
        return llmFeatureDao.getById(id, tenantId)?.toDomain()
    }

    override suspend fun insertFeature(feature: LLMFeature): Long {
        return llmFeatureDao.insert(feature.toEntity())
    }

    override suspend fun updateFeature(feature: LLMFeature) {
        llmFeatureDao.update(feature.toEntity())
    }

    override suspend fun deleteFeature(id: Long) {
        val tenantId = authManager.getTenantId() ?: ""
        llmFeatureDao.deleteById(id, tenantId)
    }

    override suspend fun setFeatureEnabled(featureKey: String, enabled: Boolean) {
        val tenantId = authManager.getTenantId() ?: ""
        llmFeatureDao.setEnabled(featureKey, tenantId, enabled)
    }

    override suspend fun updateDefaultVariant(featureKey: String, variantId: Long) {
        val tenantId = authManager.getTenantId() ?: ""
        llmFeatureDao.updateDefaultVariant(featureKey, tenantId, variantId)
    }

    // Variant methods
    override fun getVariantsByFeatureId(featureId: Long): Flow<List<FeatureVariant>> {
        val tenantId = authManager.getTenantId() ?: ""
        return featureVariantDao.getByFeatureId(featureId, tenantId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getVariantById(id: Long): FeatureVariant? {
        val tenantId = authManager.getTenantId() ?: ""
        return featureVariantDao.getById(id, tenantId)?.toDomain()
    }

    override suspend fun getActiveVariants(featureId: Long): List<FeatureVariant> {
        val tenantId = authManager.getTenantId() ?: ""
        return featureVariantDao.getActiveVariants(featureId, tenantId).map { it.toDomain() }
    }

    override suspend fun insertVariant(variant: FeatureVariant): Long {
        return featureVariantDao.insert(variant.toEntity())
    }

    override suspend fun updateVariant(variant: FeatureVariant) {
        featureVariantDao.update(variant.toEntity())
    }

    override suspend fun deleteVariant(id: Long) {
        val tenantId = authManager.getTenantId() ?: ""
        featureVariantDao.deleteById(id, tenantId)
    }

    override suspend fun deactivateAllVariants(featureId: Long) {
        val tenantId = authManager.getTenantId() ?: ""
        featureVariantDao.deactivateAllByFeatureId(featureId, tenantId)
    }

    override suspend fun setVariantActive(id: Long, isActive: Boolean) {
        val tenantId = authManager.getTenantId() ?: ""
        featureVariantDao.setActive(id, tenantId, isActive)
    }

    override suspend fun setVariantTrafficPercentage(id: Long, percentage: Int) {
        val tenantId = authManager.getTenantId() ?: ""
        featureVariantDao.setTrafficPercentage(id, tenantId, percentage)
    }
}
