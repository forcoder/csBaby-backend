package com.csbaby.kefu.data.repository

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
    private val featureVariantDao: FeatureVariantDao
) : LLMFeatureRepository {

    // Feature methods
    override fun getAllFeatures(): Flow<List<LLMFeature>> {
        return llmFeatureDao.getAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getEnabledFeatures(): Flow<List<LLMFeature>> {
        return llmFeatureDao.getEnabled().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getFeatureByFeatureKey(featureKey: String): LLMFeature? {
        return llmFeatureDao.getByFeatureKey(featureKey)?.toDomain()
    }

    override suspend fun getFeatureById(id: Long): LLMFeature? {
        return llmFeatureDao.getById(id)?.toDomain()
    }

    override suspend fun insertFeature(feature: LLMFeature): Long {
        return llmFeatureDao.insert(feature.toEntity())
    }

    override suspend fun updateFeature(feature: LLMFeature) {
        llmFeatureDao.update(feature.toEntity())
    }

    override suspend fun deleteFeature(id: Long) {
        llmFeatureDao.deleteById(id)
    }

    override suspend fun setFeatureEnabled(featureKey: String, enabled: Boolean) {
        llmFeatureDao.setEnabled(featureKey, enabled)
    }

    override suspend fun updateDefaultVariant(featureKey: String, variantId: Long) {
        llmFeatureDao.updateDefaultVariant(featureKey, variantId)
    }

    // Variant methods
    override fun getVariantsByFeatureId(featureId: Long): Flow<List<FeatureVariant>> {
        return featureVariantDao.getByFeatureId(featureId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getVariantById(id: Long): FeatureVariant? {
        return featureVariantDao.getById(id)?.toDomain()
    }

    override suspend fun getActiveVariants(featureId: Long): List<FeatureVariant> {
        return featureVariantDao.getActiveVariants(featureId).map { it.toDomain() }
    }

    override suspend fun insertVariant(variant: FeatureVariant): Long {
        return featureVariantDao.insert(variant.toEntity())
    }

    override suspend fun updateVariant(variant: FeatureVariant) {
        featureVariantDao.update(variant.toEntity())
    }

    override suspend fun deleteVariant(id: Long) {
        featureVariantDao.deleteById(id)
    }

    override suspend fun deactivateAllVariants(featureId: Long) {
        featureVariantDao.deactivateAllByFeatureId(featureId)
    }

    override suspend fun setVariantActive(id: Long, isActive: Boolean) {
        featureVariantDao.setActive(id, isActive)
    }

    override suspend fun setVariantTrafficPercentage(id: Long, percentage: Int) {
        featureVariantDao.setTrafficPercentage(id, percentage)
    }
}
