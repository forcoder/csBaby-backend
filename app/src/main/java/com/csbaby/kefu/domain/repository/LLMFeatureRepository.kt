package com.csbaby.kefu.domain.repository

import com.csbaby.kefu.domain.model.FeatureVariant
import com.csbaby.kefu.domain.model.LLMFeature
import kotlinx.coroutines.flow.Flow

interface LLMFeatureRepository {
    // Feature methods
    fun getAllFeatures(): Flow<List<LLMFeature>>
    fun getEnabledFeatures(): Flow<List<LLMFeature>>
    suspend fun getFeatureByFeatureKey(featureKey: String): LLMFeature?
    suspend fun getFeatureById(id: Long): LLMFeature?
    suspend fun insertFeature(feature: LLMFeature): Long
    suspend fun updateFeature(feature: LLMFeature)
    suspend fun deleteFeature(id: Long)
    suspend fun setFeatureEnabled(featureKey: String, enabled: Boolean)
    suspend fun updateDefaultVariant(featureKey: String, variantId: Long)

    // Variant methods
    fun getVariantsByFeatureId(featureId: Long): Flow<List<FeatureVariant>>
    suspend fun getVariantById(id: Long): FeatureVariant?
    suspend fun getActiveVariants(featureId: Long): List<FeatureVariant>
    suspend fun insertVariant(variant: FeatureVariant): Long
    suspend fun updateVariant(variant: FeatureVariant)
    suspend fun deleteVariant(id: Long)
    suspend fun deactivateAllVariants(featureId: Long)
    suspend fun setVariantActive(id: Long, isActive: Boolean)
    suspend fun setVariantTrafficPercentage(id: Long, percentage: Int)
}
