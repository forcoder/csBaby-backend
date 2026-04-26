package com.csbaby.kefu.infrastructure.llm

import android.util.Log
import com.csbaby.kefu.domain.model.OptimizationEvent
import com.csbaby.kefu.domain.model.EventType
import com.csbaby.kefu.domain.model.EventTriggerer
import com.csbaby.kefu.domain.repository.LLMFeatureRepository
import com.csbaby.kefu.domain.repository.OptimizationRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OptimizationEngine @Inject constructor(
    private val llmFeatureRepository: LLMFeatureRepository,
    private val optimizationRepository: OptimizationRepository
) {

    companion object {
        private const val TAG = "OptimizationEngine"
        private const val OPTIMIZATION_THRESHOLD_ACCEPT_RATE = 0.7f
        private const val OPTIMIZATION_THRESHOLD_MODIFY_RATE = 0.3f
        private const val MIN_SAMPLES_FOR_OPTIMIZATION = 50
    }

    /**
     * Run optimization cycle for a feature based on collected metrics.
     */
    suspend fun runOptimizationCycle(featureKey: String) {
        try {
            Log.d(TAG, "Starting optimization cycle for feature: $featureKey")

            val metrics = optimizationRepository.getMetricsByFeatureKey(featureKey).first()
            if (metrics.isEmpty()) {
                Log.d(TAG, "No metrics found for feature: $featureKey")
                return
            }

            val latestMetrics = metrics.maxByOrNull { it.date }
                ?: return

            val totalGenerated = latestMetrics.totalAccepted + latestMetrics.totalModified + latestMetrics.totalRejected
            if (totalGenerated < MIN_SAMPLES_FOR_OPTIMIZATION) {
                Log.d(TAG, "Insufficient samples ($totalGenerated) for optimization")
                return
            }

            val acceptanceRate = if (latestMetrics.totalGenerated > 0) {
                latestMetrics.totalAccepted.toFloat() / latestMetrics.totalGenerated
            } else 0f

            val modificationRate = if (latestMetrics.totalGenerated > 0) {
                latestMetrics.totalModified.toFloat() / latestMetrics.totalGenerated
            } else 0f

            Log.d(TAG, "Metrics for $featureKey: generated=$totalGenerated, accepted=${acceptanceRate}, modified=${modificationRate}")

            when {
                acceptanceRate < OPTIMIZATION_THRESHOLD_ACCEPT_RATE && modificationRate > OPTIMIZATION_THRESHOLD_MODIFY_RATE -> {
                    // High modification rate suggests need for prompt tuning
                    optimizePromptTemplate(featureKey)
                }
                acceptanceRate < OPTIMIZATION_THRESHOLD_ACCEPT_RATE -> {
                    // Low acceptance rate suggests need for variant rotation
                    rotateVariant(featureKey)
                }
                latestMetrics.avgConfidence < 0.6 -> {
                    // Low confidence suggests need for model fine-tuning
                    optimizeModelConfig(featureKey)
                }
                else -> {
                    Log.d(TAG, "Feature $featureKey performance is satisfactory, no optimization needed")
                }
            }

            // Record optimization event
            recordOptimizationEvent(featureKey, EventType.AUTO_OPTIMIZE.name, "Optimization cycle completed with rates: accept=${acceptanceRate}, modify=${modificationRate}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to run optimization cycle for feature: $featureKey", e)
        }
    }

    /**
     * Optimize prompt template based on feedback patterns.
     */
    private suspend fun optimizePromptTemplate(featureKey: String) {
        try {
            Log.d(TAG, "Optimizing prompt template for feature: $featureKey")

            // Get all variants for the feature
            val feature = llmFeatureRepository.getFeatureByFeatureKey(featureKey)
                ?: return

            val variants = llmFeatureRepository.getVariantsByFeatureId(feature.id).first()
            val activeVariants = variants.filter { it.isActive }

            if (activeVariants.size >= 2) {
                // Rotate traffic between variants to find better performing one
                val bestVariant = activeVariants.maxByOrNull { variant ->
                    val variantMetrics = optimizationRepository.getMetricsByFeatureKeyAndDateRange(
                        featureKey,
                        getDateDaysAgo(7),
                        getCurrentDate()
                    ).filter { it.variantId == variant.id }

                    if (variantMetrics.isEmpty()) 0f
                    else {
                        val total = variantMetrics.sumOf { it.totalGenerated.toDouble() }.toInt()
                        if (total == 0) 0f else variantMetrics.sumOf { it.totalAccepted.toDouble() }.toInt().toFloat() / total
                    }
                }

                if (bestVariant != null) {
                    llmFeatureRepository.updateDefaultVariant(featureKey, bestVariant.id)
                    recordOptimizationEvent(featureKey, EventType.AUTO_OPTIMIZE.name, "Promoted variant ${bestVariant.variantName} due to high acceptance rate")
                }
            } else {
                // Single variant case - suggest creating new variant with optimized prompt
                recordOptimizationEvent(featureKey, EventType.AUTO_OPTIMIZE.name, "Suggested creating new variant with optimized prompt template")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to optimize prompt template for feature: $featureKey", e)
        }
    }

    /**
     * Rotate to a different variant to improve performance.
     */
    private suspend fun rotateVariant(featureKey: String) {
        try {
            Log.d(TAG, "Rotating variant for feature: $featureKey")

            val feature = llmFeatureRepository.getFeatureByFeatureKey(featureKey)
                ?: return

            val variants = llmFeatureRepository.getVariantsByFeatureId(feature.id).first()
            val activeVariants = variants.filter { it.isActive }

            if (activeVariants.size > 1) {
                // Deactivate current default and activate another
                val currentDefault = variants.find { it.id == feature.defaultVariantId }
                val otherVariants = activeVariants.filter { it.id != currentDefault?.id }

                if (otherVariants.isNotEmpty()) {
                    val newDefault = otherVariants.random()
                    llmFeatureRepository.updateDefaultVariant(featureKey, newDefault.id)

                    // Reset traffic percentages for fair distribution
                    activeVariants.forEach { variant ->
                        llmFeatureRepository.setVariantTrafficPercentage(variant.id, 100 / activeVariants.size)
                    }

                    recordOptimizationEvent(featureKey, EventType.AUTO_OPTIMIZE.name, "Rotated to variant ${newDefault.variantName}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rotate variant for feature: $featureKey", e)
        }
    }

    /**
     * Optimize model configuration parameters.
     */
    private suspend fun optimizeModelConfig(featureKey: String) {
        try {
            Log.d(TAG, "Optimizing model config for feature: $featureKey")

            recordOptimizationEvent(featureKey, EventType.AUTO_OPTIMIZE.name, "Suggested adjusting temperature and maxTokens parameters")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to optimize model config for feature: $featureKey", e)
        }
    }

    /**
     * Record an optimization event.
     */
    private suspend fun recordOptimizationEvent(
        featureKey: String,
        eventType: String,
        reason: String,
        triggeredBy: String = "optimization_engine"
    ) {
        try {
            val event = OptimizationEvent(
                id = 0L,
                featureKey = featureKey,
                eventType = EventType.valueOf(eventType),
                oldConfig = "",
                newConfig = "",
                reason = reason,
                triggeredBy = EventTriggerer.valueOf(triggeredBy),
                createdAt = System.currentTimeMillis()
            )

            optimizationRepository.insertEvent(event)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record optimization event", e)
        }
    }

    /**
     * Analyze feedback trends over time.
     */
    suspend fun analyzeFeedbackTrends(featureKey: String): Map<String, Any> {
        return try {
            val metrics = optimizationRepository.getMetricsByFeatureKey(featureKey).first()
            val events = optimizationRepository.getEventsByFeatureKey(featureKey).first()

            val recentMetrics = metrics.filter {
                isWithinDays(it.date, 7)
            }

            mapOf(
                "totalSamples" to recentMetrics.sumOf { it.totalGenerated },
                "acceptanceRate" to if (recentMetrics.isNotEmpty()) {
                    val totalAccepted = recentMetrics.sumOf { it.totalAccepted }
                    val totalGenerated = recentMetrics.sumOf { it.totalGenerated }
                    if (totalGenerated > 0) totalAccepted.toFloat() / totalGenerated else 0f
                } else 0f,
                "averageConfidence" to if (recentMetrics.isNotEmpty()) {
                    recentMetrics.map { it.avgConfidence }.average()
                } else 0.0,
                "optimizationEvents" to events.size,
                "lastOptimization" to (events.maxByOrNull { it.createdAt }?.createdAt ?: 0L)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to analyze feedback trends", e)
            emptyMap()
        }
    }

    /**
     * Suggest optimizations based on current metrics.
     */
    suspend fun getOptimizationSuggestions(featureKey: String): List<String> {
        val suggestions = mutableListOf<String>()
        val metrics = optimizationRepository.getMetricsByFeatureKey(featureKey).first()

        if (metrics.isEmpty()) {
            suggestions.add("Collect more sample data (minimum ${MIN_SAMPLES_FOR_OPTIMIZATION} replies)")
            return suggestions
        }

        val latestMetrics = metrics.maxByOrNull { it.date } ?: return suggestions

        val totalGenerated = latestMetrics.totalAccepted + latestMetrics.totalModified + latestMetrics.totalRejected
        if (totalGenerated < MIN_SAMPLES_FOR_OPTIMIZATION) {
            suggestions.add("Insufficient data: ${totalGenerated}/${MIN_SAMPLES_FOR_OPTIMIZATION} samples collected")
        }

        val acceptanceRate = if (totalGenerated > 0) {
            latestMetrics.totalAccepted.toFloat() / totalGenerated
        } else 0f

        val modificationRate = if (totalGenerated > 0) {
            latestMetrics.totalModified.toFloat() / totalGenerated
        } else 0f

        if (acceptanceRate < OPTIMIZATION_THRESHOLD_ACCEPT_RATE && modificationRate > OPTIMIZATION_THRESHOLD_MODIFY_RATE) {
            suggestions.add("High modification rate detected: Consider refining prompt templates")
        }

        if (latestMetrics.avgConfidence < 0.6) {
            suggestions.add("Low confidence scores: Consider model parameter tuning")
        }

        if (suggestions.isEmpty()) {
            suggestions.add("Performance is within acceptable ranges")
        }

        return suggestions
    }

    private fun getCurrentDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    private fun getDateDaysAgo(days: Int): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -days)
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
    }

    private fun isWithinDays(dateStr: String, days: Int): Boolean {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = dateFormat.parse(dateStr)
            val cutoff = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -days)
            }.time

            return date != null && date.after(cutoff)
        } catch (e: Exception) {
            return false
        }
    }
}
