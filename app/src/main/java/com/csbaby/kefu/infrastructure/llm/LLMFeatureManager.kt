package com.csbaby.kefu.infrastructure.llm

import android.util.Log
import com.csbaby.kefu.domain.model.FeatureVariant
import com.csbaby.kefu.domain.model.VariantType
import com.csbaby.kefu.domain.model.FeedbackAction
import com.csbaby.kefu.domain.model.LLMFeature
import com.csbaby.kefu.domain.model.ReplyFeedback
import com.csbaby.kefu.domain.model.OptimizationMetrics
import com.csbaby.kefu.domain.repository.LLMFeatureRepository
import com.csbaby.kefu.domain.repository.OptimizationRepository
import com.csbaby.kefu.domain.repository.ReplyFeedbackRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LLMFeatureManager @Inject constructor(
    private val llmFeatureRepository: LLMFeatureRepository,
    private val optimizationRepository: OptimizationRepository,
    private val replyFeedbackRepository: ReplyFeedbackRepository
) {

    /**
     * Get active variant for a feature with consistent hashing traffic allocation.
     */
    suspend fun getActiveVariant(featureKey: String): Result<FeatureVariant> {
        return try {
            val feature = llmFeatureRepository.getFeatureByFeatureKey(featureKey)
                ?: return Result.failure(IllegalArgumentException("Feature not found: $featureKey"))

            val variants = llmFeatureRepository.getVariantsByFeatureId(feature.id).first()

            if (variants.isEmpty()) {
                return Result.failure(IllegalArgumentException("No variants found for feature: $featureKey"))
            }

            // Use traffic percentage for routing
            val totalTraffic = variants.sumOf { it.trafficPercentage.toDouble() }.toInt()
            if (totalTraffic == 0) {
                return Result.failure(IllegalArgumentException("Total traffic is zero for feature: $featureKey"))
            }

            val hash = featureKey.hashCode() % 100
            var accumulated = 0

            val selectedVariant = variants.sortedByDescending { it.isActive }
                .find { variant ->
                    accumulated += variant.trafficPercentage
                    hash < accumulated && variant.isActive
                } ?: variants.first { it.isActive }

            Result.success(selectedVariant)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update metrics for a specific variant.
     */
    suspend fun updateMetrics(
        featureKey: String,
        variantId: Long,
        accepted: Boolean = false,
        modified: Boolean = false,
        rejected: Boolean = false,
        confidence: Double = 1.0,
        responseTimeMs: Int = 0
    ) {
        try {
            val currentDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            val existingMetrics = optimizationRepository.getMetricsByFeatureKeyAndDate(featureKey, currentDate)

            val newMetrics = if (existingMetrics != null) {
                existingMetrics.copy(
                    totalGenerated = existingMetrics.totalGenerated + 1,
                    totalAccepted = existingMetrics.totalAccepted + if (accepted) 1 else 0,
                    totalModified = existingMetrics.totalModified + if (modified) 1 else 0,
                    totalRejected = existingMetrics.totalRejected + if (rejected) 1 else 0,
                    avgConfidence = (existingMetrics.avgConfidence * existingMetrics.totalGenerated + confidence.toFloat()) / (existingMetrics.totalGenerated + 1),
                    avgResponseTimeMs = if (responseTimeMs > 0) {
                        (existingMetrics.avgResponseTimeMs * existingMetrics.totalGenerated + responseTimeMs.toLong()) / (existingMetrics.totalGenerated + 1)
                    } else existingMetrics.avgResponseTimeMs
                )
            } else {
                OptimizationMetrics(
                    id = 0L,
                    featureKey = featureKey,
                    variantId = variantId,
                    date = currentDate,
                    totalGenerated = 1,
                    totalAccepted = if (accepted) 1 else 0,
                    totalModified = if (modified) 1 else 0,
                    totalRejected = if (rejected) 1 else 0,
                    avgConfidence = confidence.toFloat(),
                    avgResponseTimeMs = responseTimeMs.toLong(),
                    accuracyScore = if (accepted || modified) confidence.toFloat() else 0f
                )
            }

            optimizationRepository.upsertMetrics(newMetrics)
        } catch (e: Exception) {
            Log.e("LLMFeatureManager", "Failed to update metrics", e)
        }
    }

    /**
     * Initialize default LLM features.
     */
    suspend fun initializeDefaultFeatures() {
        try {
            val existingFeatures = llmFeatureRepository.getAllFeatures().first()
            if (existingFeatures.isNotEmpty()) {
                Log.d("LLMFeatureManager", "Features already initialized, skipping")
                return
            }

            // Create default LLM features
            val replyGenerationFeature = LLMFeature(
                id = 0L,
                featureKey = "reply_generation",
                displayName = "智能回复生成",
                description = "根据用户输入生成个性化回复",
                isEnabled = true,
                defaultVariantId = null,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            val autoRuleFeature = LLMFeature(
                id = 0L,
                featureKey = "auto_rule_generation",
                displayName = "自动规则生成",
                description = "自动生成知识库规则",
                isEnabled = true,
                defaultVariantId = null,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            llmFeatureRepository.insertFeature(replyGenerationFeature.copy(id = 0))
            llmFeatureRepository.insertFeature(autoRuleFeature.copy(id = 0))

            Log.d("LLMFeatureManager", "Default LLM features initialized successfully")
        } catch (e: Exception) {
            Log.e("LLMFeatureManager", "Failed to initialize default features", e)
        }
    }

    /**
     * Record user feedback for optimization.
     */
    suspend fun recordFeedback(
        featureKey: String,
        variantId: Long,
        replyHistoryId: Long,
        userAction: FeedbackAction,
        modifiedPart: String? = null,
        userRating: Int? = null
    ) {
        try {
            val feedback = ReplyFeedback(
                id = 0L,
                replyHistoryId = replyHistoryId,
                variantId = variantId,
                userAction = userAction,
                modifiedPart = modifiedPart,
                userRating = userRating,
                feedbackText = null,
                createdAt = System.currentTimeMillis()
            )

            replyFeedbackRepository.insertFeedback(feedback)
            Log.d("LLMFeatureManager", "Feedback recorded: $featureKey/$variantId -> $userAction")
        } catch (e: Exception) {
            Log.e("LLMFeatureManager", "Failed to record feedback", e)
        }
    }

    /**
     * Get all available features.
     */
    fun getAllFeatures(): kotlinx.coroutines.flow.Flow<List<LLMFeature>> {
        return llmFeatureRepository.getAllFeatures()
    }

    /**
     * Get enabled features only.
     */
    fun getEnabledFeatures(): kotlinx.coroutines.flow.Flow<List<LLMFeature>> {
        return llmFeatureRepository.getEnabledFeatures()
    }

    /**
     * Update feature configuration.
     */
    suspend fun updateFeature(featureKey: String, isEnabled: Boolean): Result<Unit> {
        return try {
            val feature = llmFeatureRepository.getFeatureByFeatureKey(featureKey)
                ?: return Result.failure(IllegalArgumentException("Feature not found: $featureKey"))

            llmFeatureRepository.setFeatureEnabled(featureKey, isEnabled)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get optimization metrics for a feature.
     */
    fun getMetricsForFeature(featureKey: String): kotlinx.coroutines.flow.Flow<List<OptimizationMetrics>> {
        return optimizationRepository.getMetricsByFeatureKey(featureKey)
    }

    /**
     * Record a successful reply generation.
     */
    suspend fun recordSuccessfulReply(
        featureKey: String,
        variantId: Long,
        replyHistoryId: Long,
        responseTimeMs: Int,
        confidence: Double = 1.0
    ) {
        updateMetrics(featureKey, variantId, accepted = true, responseTimeMs = responseTimeMs, confidence = confidence)
        recordFeedback(featureKey, variantId, replyHistoryId, FeedbackAction.ACCEPTED, null, null)
    }

    /**
     * Record a modified reply.
     */
    suspend fun recordModifiedReply(
        featureKey: String,
        variantId: Long,
        replyHistoryId: Long,
        modifiedPart: String,
        responseTimeMs: Int,
        confidence: Double = 0.8
    ) {
        updateMetrics(featureKey, variantId, modified = true, responseTimeMs = responseTimeMs, confidence = confidence)
        recordFeedback(featureKey, variantId, replyHistoryId, FeedbackAction.MODIFIED, modifiedPart, 4)
    }

    /**
     * Record a rejected reply.
     */
    suspend fun recordRejectedReply(
        featureKey: String,
        variantId: Long,
        replyHistoryId: Long,
        responseTimeMs: Int
    ) {
        updateMetrics(featureKey, variantId, rejected = true, responseTimeMs = responseTimeMs)
        recordFeedback(featureKey, variantId, replyHistoryId, FeedbackAction.REJECTED, null, 2)
    }
}