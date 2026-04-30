package com.csbaby.kefu.infrastructure.monitoring

import com.csbaby.kefu.domain.model.AIModelConfig
import com.csbaby.kefu.domain.model.ModelType
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * Comprehensive AI performance monitoring and analytics service.
 */
@Singleton
class AIPerformanceMonitor @Inject constructor() {

    data class PerformanceSnapshot(
        val timestamp: Long,
        val totalRequests: Int,
        val successfulRequests: Int,
        val avgResponseTimeMs: Long,
        val errorRate: Float,
        val modelPerformance: Map<String, ModelMetrics>,
        val cacheHitRate: Float,
        val routingEfficiency: Float
    )

    data class ModelMetrics(
        val modelId: Long,
        val modelName: String,
        val modelType: ModelType,
        val requestCount: Int,
        val successCount: Int,
        val avgResponseTimeMs: Long,
        val errorRate: Float,
        val costPerRequest: Double,
        val tokenUsage: Int,
        val lastUsed: Long
    )

    data class SystemHealth(
        val overallScore: Float, // 0-100 scale
        val responseTimeScore: Float,
        val availabilityScore: Float,
        val costEfficiencyScore: Float,
        val recommendation: String
    )

    private val performanceHistory = mutableListOf<PerformanceSnapshot>()
    private val MAX_HISTORY_SIZE = 1000

    // Current metrics (would be injected from AIService)
    private var totalRequests = 0
    private var successfulRequests = 0
    private var totalResponseTime = 0L
    private var cacheHits = 0
    private var cacheMisses = 0
    private var modelMetrics = mutableMapOf<String, MutableModelMetrics>()
    private var routingDecisions = mutableListOf<RoutingDecision>()

    data class RoutingDecision(
        val taskType: String,
        val selectedModel: String,
        val responseTimeMs: Long,
        val success: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    )

    fun recordRequest(modelId: Long, modelName: String, modelType: ModelType) {
        synchronized(this) {
            totalRequests++
            val key = "${modelId}_${modelType.name}"

            modelMetrics.getOrPut(key) { MutableModelMetrics(modelId, modelName, modelType) }.apply {
                requestCount++
            }
        }
    }

    fun recordSuccess(modelId: Long, modelName: String, modelType: ModelType, responseTimeMs: Long, tokensUsed: Int) {
        synchronized(this) {
            successfulRequests++
            totalResponseTime += responseTimeMs

            val key = "${modelId}_${modelType.name}"
            modelMetrics[key]?.let { metrics ->
                metrics.successCount++
                metrics.responseTimes.add(responseTimeMs)
                if (metrics.responseTimes.size > 100) {
                    metrics.responseTimes.removeAt(0)
                }
                metrics.totalTokens += tokensUsed
                metrics.lastUsed = System.currentTimeMillis()
            }
        }
    }

    fun recordCacheHit() {
        synchronized(this) {
            cacheHits++
        }
    }

    fun recordCacheMiss() {
        synchronized(this) {
            cacheMisses++
        }
    }

    fun recordRoutingDecision(taskType: String, selectedModel: String, responseTimeMs: Long, success: Boolean) {
        synchronized(this) {
            routingDecisions.add(RoutingDecision(taskType, selectedModel, responseTimeMs, success))
            // Keep only recent decisions for analysis
            if (routingDecisions.size > 1000) {
                routingDecisions.removeAt(0)
            }
        }
    }

    fun getPerformanceSnapshot(): PerformanceSnapshot {
        synchronized(this) {
            val now = System.currentTimeMillis()
            val currentTotalRequests = totalRequests
            val currentSuccessful = successfulRequests
            val avgResponseTime = if (totalRequests > 0) totalResponseTime / totalRequests else 0L
            val errorRate = if (currentTotalRequests > 0) (currentTotalRequests - currentSuccessful).toFloat() / currentTotalRequests else 0f
            val cacheHitRate = calculateCacheHitRate()

            return PerformanceSnapshot(
                timestamp = now,
                totalRequests = currentTotalRequests,
                successfulRequests = currentSuccessful,
                avgResponseTimeMs = avgResponseTime,
                errorRate = errorRate,
                modelPerformance = buildModelMetricsMap(),
                cacheHitRate = cacheHitRate,
                routingEfficiency = calculateRoutingEfficiency()
            ).also {
                cleanupOldData(now)
                addToHistory(it)
            }
        }
    }

    private fun calculateCacheHitRate(): Float {
        val totalCacheRequests = cacheHits + cacheMisses
        return if (totalCacheRequests > 0) cacheHits.toFloat() / totalCacheRequests else 0f
    }

    private fun buildModelMetricsMap(): Map<String, ModelMetrics> {
        return modelMetrics.mapValues { (_, metrics) ->
            ModelMetrics(
                modelId = metrics.modelId,
                modelName = metrics.modelName,
                modelType = metrics.modelType,
                requestCount = metrics.requestCount,
                successCount = metrics.successCount,
                avgResponseTimeMs = if (metrics.responseTimes.isNotEmpty()) {
                    metrics.responseTimes.average().toLong()
                } else 0L,
                errorRate = if (metrics.requestCount > 0) {
                    (metrics.requestCount - metrics.successCount).toFloat() / metrics.requestCount
                } else 0f,
                costPerRequest = calculateCostPerRequest(metrics),
                tokenUsage = metrics.totalTokens,
                lastUsed = metrics.lastUsed
            )
        }
    }

    private fun calculateCostPerRequest(metrics: MutableModelMetrics): Double {
        // Estimate cost based on model type and usage patterns
        val baseCosts = mapOf(
            ModelType.CLAUDE to 0.002,     // per 1K tokens
            ModelType.OPENAI to 0.0015,   // per 1K tokens
            ModelType.ZHIPU to 0.001,     // per 1K tokens
            ModelType.TONGYI to 0.001,    // per 1K tokens
            ModelType.CUSTOM to 0.0      // custom models
        )

        val baseCost = baseCosts[metrics.modelType] ?: 0.0
        val estimatedTokens = max(metrics.totalTokens, metrics.requestCount * 1000) // Assume 1000 tokens per request minimum

        return (estimatedTokens / 1000.0) * baseCost
    }

    private fun calculateRoutingEfficiency(): Float {
        val recentDecisions = routingDecisions.filter { now ->
            now.timestamp > System.currentTimeMillis() - 3600000 // Last hour
        }

        if (recentDecisions.isEmpty()) return 0f

        val fastResponses = recentDecisions.count { it.responseTimeMs < 2000 }
        val successfulRoutes = recentDecisions.count { it.success }

        return ((fastResponses.toFloat() / recentDecisions.size) +
               (successfulRoutes.toFloat() / recentDecisions.size)) / 2f
    }

    private fun cleanupOldData(now: Long) {
        // Remove model metrics older than 24 hours
        modelMetrics.entries.removeAll { (_, metrics) ->
            now - metrics.lastUsed > 86400000
        }

        // Keep only recent routing decisions
        val cutoffTime = now - 86400000 // 24 hours
        routingDecisions.removeAll { it.timestamp < cutoffTime }
    }

    private fun addToHistory(snapshot: PerformanceSnapshot) {
        performanceHistory.add(snapshot)
        if (performanceHistory.size > MAX_HISTORY_SIZE) {
            performanceHistory.removeAt(0)
        }
    }

    fun getSystemHealth(): SystemHealth {
        val snapshot = getPerformanceSnapshot()

        val overallScore = calculateOverallHealthScore(snapshot)
        val responseTimeScore = calculateResponseTimeScore(snapshot)
        val availabilityScore = calculateAvailabilityScore(snapshot)
        val costEfficiencyScore = calculateCostEfficiencyScore(snapshot)

        val recommendation = generateRecommendation(snapshot, overallScore)

        return SystemHealth(
            overallScore = overallScore,
            responseTimeScore = responseTimeScore,
            availabilityScore = availabilityScore,
            costEfficiencyScore = costEfficiencyScore,
            recommendation = recommendation
        )
    }

    private fun calculateOverallHealthScore(snapshot: PerformanceSnapshot): Float {
        var score = 0f

        // Response time component (0-40 points)
        score += when {
            snapshot.avgResponseTimeMs <= 1000 -> 40f
            snapshot.avgResponseTimeMs <= 2000 -> 30f
            snapshot.avgResponseTimeMs <= 3000 -> 20f
            snapshot.avgResponseTimeMs <= 5000 -> 10f
            else -> 0f
        }

        // Availability component (0-35 points)
        score += when {
            snapshot.errorRate <= 0.01f -> 35f
            snapshot.errorRate <= 0.05f -> 25f
            snapshot.errorRate <= 0.1f -> 15f
            snapshot.errorRate <= 0.2f -> 5f
            else -> 0f
        }

        // Cost efficiency component (0-25 points)
        val costEfficiency = snapshot.modelPerformance.values
            .map { it.costPerRequest }
            .average()
        score += when {
            costEfficiency <= 0.001 -> 25f
            costEfficiency <= 0.002 -> 20f
            costEfficiency <= 0.003 -> 15f
            costEfficiency <= 0.005 -> 10f
            else -> 5f
        }

        return min(100f, score)
    }

    private fun calculateResponseTimeScore(snapshot: PerformanceSnapshot): Float {
        return when {
            snapshot.avgResponseTimeMs <= 1000 -> 100f
            snapshot.avgResponseTimeMs <= 1500 -> 80f
            snapshot.avgResponseTimeMs <= 2000 -> 60f
            snapshot.avgResponseTimeMs <= 3000 -> 40f
            snapshot.avgResponseTimeMs <= 5000 -> 20f
            else -> 0f
        }
    }

    private fun calculateAvailabilityScore(snapshot: PerformanceSnapshot): Float {
        val availability = 1f - snapshot.errorRate
        return (availability * 100f).coerceIn(0f, 100f)
    }

    private fun calculateCostEfficiencyScore(snapshot: PerformanceSnapshot): Float {
        val avgCost = snapshot.modelPerformance.values
            .map { it.costPerRequest }
            .average()

        return when {
            avgCost <= 0.001 -> 100f
            avgCost <= 0.002 -> 80f
            avgCost <= 0.003 -> 60f
            avgCost <= 0.005 -> 40f
            avgCost <= 0.01 -> 20f
            else -> 0f
        }
    }

    private fun generateRecommendation(snapshot: PerformanceSnapshot, overallScore: Float): String {
        return when {
            overallScore >= 90f -> "系统运行状态极佳，所有指标都处于优秀水平。"

            overallScore >= 75f -> {
                val issues = mutableListOf<String>()

                if (snapshot.avgResponseTimeMs > 2000) {
                    issues.add("响应时间较长")
                }
                if (snapshot.errorRate > 0.05f) {
                    issues.add("错误率偏高")
                }
                if (snapshot.cacheHitRate < 0.3f) {
                    issues.add("缓存利用率低")
                }

                if (issues.isEmpty()) {
                    "系统运行良好，建议继续监控性能趋势。"
                } else {
                    "建议优化：${issues.joinToString("、")}。"
                }
            }

            overallScore >= 60f -> {
                val issues = mutableListOf<String>()

                if (snapshot.avgResponseTimeMs > 3000) {
                    issues.add("响应时间过长")
                }
                if (snapshot.errorRate > 0.1f) {
                    issues.add("错误率较高")
                }
                if (snapshot.cacheHitRate < 0.2f) {
                    issues.add("需要优化缓存策略")
                }
                if (snapshot.routingEfficiency < 0.5f) {
                    issues.add("路由效率有待提升")
                }

                "系统存在明显性能问题：${issues.joinToString("、")}。建议立即关注。"
            }

            else -> {
                val criticalIssues = mutableListOf<String>()

                if (snapshot.errorRate > 0.2f) {
                    criticalIssues.add("API错误率过高")
                }
                if (snapshot.avgResponseTimeMs > 5000) {
                    criticalIssues.add("响应时间严重超时")
                }
                if (snapshot.modelPerformance.isEmpty()) {
                    criticalIssues.add("没有活跃模型")
                }

                "⚠️ 系统严重故障！${criticalIssues.joinToString("；")}。需要紧急干预。"
            }
        }
    }

    fun getPerformanceTrend(durationHours: Int = 24): List<PerformanceSnapshot> {
        val cutoffTime = System.currentTimeMillis() - durationHours * 3600000L
        return performanceHistory.filter { it.timestamp > cutoffTime }
    }

    fun getModelPerformanceReport(): String {
        val snapshot = getPerformanceSnapshot()
        val builder = StringBuilder()

        builder.appendln("🤖 AI 模型性能报告")
        builder.appendln("=".repeat(50))

        snapshot.modelPerformance.values.sortedByDescending { it.requestCount }.forEach { model ->
            builder.appendln()
            builder.appendln("📊 ${model.modelName} (${model.modelType.name})")
            builder.appendln("   请求次数: ${model.requestCount}")
            builder.appendln("   成功率: ${((model.successCount.toFloat() / model.requestCount) * 100).toInt()}%")
            builder.appendln("   平均响应时间: ${model.avgResponseTimeMs}ms")
            builder.appendln("   每次请求成本: ¥${String.format("%.6f", model.costPerRequest)}")
            builder.appendln("   Token使用量: ${model.tokenUsage}")
        }

        builder.appendln()
        builder.appendln("📈 系统概览:")
        builder.appendln("   总请求数: ${snapshot.totalRequests}")
        builder.appendln("   缓存命中率: ${(snapshot.cacheHitRate * 100).toInt()}%")
        builder.appendln("   路由效率: ${(snapshot.routingEfficiency * 100).toInt()}%")

        return builder.toString()
    }

    // Reset all metrics (useful for testing or system restart)
    fun resetMetrics() {
        synchronized(this) {
            totalRequests = 0
            successfulRequests = 0
            totalResponseTime = 0L
            cacheHits = 0
            cacheMisses = 0
            modelMetrics.clear()
            routingDecisions.clear()
            performanceHistory.clear()
        }
    }

    // Internal mutable classes for thread-safe operations
    private data class MutableModelMetrics(
        val modelId: Long,
        val modelName: String,
        val modelType: ModelType,
        var requestCount: Int = 0,
        var successCount: Int = 0,
        val responseTimes: MutableList<Long> = mutableListOf(),
        var totalTokens: Int = 0,
        var lastUsed: Long = System.currentTimeMillis()
    )
}