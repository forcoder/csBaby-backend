package com.csbaby.kefu.infrastructure.simple

import com.csbaby.kefu.domain.model.AIModelConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 简化的智能任务路由器 - 无需注解处理
 */
@Singleton
open class SimpleTaskRouter @Inject constructor() {

    /**
     * Enhanced model selection with performance-aware routing.
     */
    open fun selectBestModel(
        taskType: TaskType,
        availableModels: List<AIModelConfig>,
        contextInfo: Map<String, Any>? = null
    ): RoutingResult {
        val modelScores = mutableListOf<ModelScore>()

        for (model in availableModels) {
            val score = calculateEnhancedModelScore(model, taskType, contextInfo)

            if (score > 0.1f) { // Minimum threshold for consideration
                modelScores.add(ModelScore(
                    model = model,
                    score = score,
                    reasoning = generateDetailedRoutingReasoning(score, model, taskType, contextInfo)
                ))
            }
        }

        return when {
            modelScores.isEmpty() -> {
                Timber.d("No suitable models found for $taskType, selecting fallback")
                selectFallbackModel(taskType, availableModels)
            }
            modelScores.size == 1 -> RoutingResult.SingleChoice(modelScores.first())
            else -> {
                val sortedScores = modelScores.sortedByDescending { it.score }
                val topCandidates = sortedScores.take(3)
                RoutingResult.MultipleChoices(topCandidates)
            }
        }
    }

    /**
     * Fallback model selection when no models meet criteria.
     */
    private fun selectFallbackModel(taskType: TaskType, models: List<AIModelConfig>): RoutingResult {
        return when {
            models.isEmpty() -> RoutingResult.NoSuitableModel("没有可用的模型")

            // Sort by basic criteria for fallback
            models.size == 1 -> RoutingResult.SingleChoice(ModelScore(models.first(), 1f, "唯一可用模型"))

            else -> {
                val fallbackModels = models.sortedWith(
                    compareByDescending<AIModelConfig> { it.isEnabled }
                        .thenByDescending { it.lastUsed }
                        .thenBy { it.monthlyCost } // Lower cost first
                ).take(3)

                val candidates = fallbackModels.map { model ->
                    ModelScore(model, 0.5f, "备用模型 - ${getModelDisplayName(model)}")
                }

                if (candidates.size == 1) {
                    RoutingResult.SingleChoice(candidates.first())
                } else {
                    RoutingResult.MultipleChoices(candidates)
                }
            }
        }
    }

    private fun calculateEnhancedModelScore(
        model: AIModelConfig,
        taskType: TaskType,
        contextInfo: Map<String, Any>? = null
    ): Float {
        var score = 0f

        // Base performance metrics (40% weight)
        score += getPerformanceMetricsScore(model) * 0.4f

        // Task-specific capabilities (35% weight)
        score += getTaskCapabilityScore(model, taskType, contextInfo) * 0.35f

        // Cost efficiency (15% weight)
        score += getCostEfficiencyScore(model) * 0.15f

        // Availability and freshness (10% weight)
        score += getAvailabilityScore(model) * 0.1f

        return score.coerceIn(0f, 1f)
    }

    private fun getPerformanceMetricsScore(model: AIModelConfig): Float {
        val key = model.id.toString()
        val metrics = requestMetrics[key]

        if (metrics == null) {
            // No historical data, use optimistic default
            return 0.6f
        }

        var score = 0f

        // Success rate component (0-0.4)
        score += metrics.successRate * 0.4f

        // Response time component (0-0.3) - inverted so faster is better
        val normalizedResponseTime = (8000 - metrics.avgResponseTimeMs.coerceAtMost(8000)) / 8000f
        score += normalizedResponseTime * 0.3f

        // Consistency component (0-0.3) - lower variance is better
        val consistency = when {
            metrics.totalRequests < 10 -> 0.2f // Not enough data
            metrics.failureRate < 0.1f -> 0.3f
            metrics.failureRate < 0.2f -> 0.2f
            metrics.failureRate < 0.3f -> 0.1f
            else -> 0f
        }
        score += consistency

        return score.coerceIn(0f, 1f)
    }

    private fun getTaskCapabilityScore(
        model: AIModelConfig,
        taskType: TaskType,
        contextInfo: Map<String, Any>? = null
    ): Float {
        var score = 0f

        when (taskType) {
            TaskType.GENERAL_CHAT -> {
                score += getGeneralChatScore(model, contextInfo)
            }
            TaskType.CUSTOMER_SERVICE -> {
                score += getCustomerServiceScore(model, contextInfo)
            }
            TaskType.DOCUMENT_PROCESSING -> {
                score += getDocumentProcessingScore(model, contextInfo)
            }
            TaskType.REASONING -> {
                score += getReasoningScore(model, contextInfo)
            }
            TaskType.CODING_ASSISTANCE -> {
                score += getCodingScore(model, contextInfo)
            }
            TaskType.VISION_TASKS -> {
                score += getVisionScore(model, contextInfo)
            }
            TaskType.TRANSLATION -> {
                score += getTranslationScore(model, contextInfo)
            }
            TaskType.DATA_ANALYSIS -> {
                score += getDataAnalysisScore(model, contextInfo)
            }
            TaskType.SUMMARIZATION -> {
                score += getSummarizationScore(model, contextInfo)
            }
        }

        return score.coerceIn(0f, 1f)
    }

    private fun getGeneralChatScore(model: AIModelConfig, contextInfo: Map<String, Any>?): Float {
        var score = 0f

        // Token capacity for conversation history
        if (model.maxTokens >= 4096) score += 0.2f
        if (model.maxTokens >= 8192) score += 0.1f

        // Model type preferences
        when (model.modelType) {
            ModelType.OPENAI -> score += 0.3f // GPT models excel at general chat
            ModelType.CLAUDE -> score += 0.25f // Claude good for conversations
            ModelType.ZHIPU -> score += 0.2f
            ModelType.TONGYI -> score += 0.2f
            ModelType.CUSTOM -> score += 0.1f
        }

        // Context-aware adjustments
        contextInfo?.let { ctx ->
            ctx["urgency"]?.let { urgency ->
                if (urgency == "high") {
                    if (model.maxTokens >= 4096) score += 0.1f // Fast models for urgent chats
                }
            }
        }

        return score
    }

    private fun getCustomerServiceScore(model: AIModelConfig, contextInfo: Map<String, Any>?): Float {
        var score = 0f

        // Customer service needs reliability and moderate speed
        if (model.maxTokens >= 4096) score += 0.2f
        if (model.monthlyCost < 3.0) score += 0.2f // Cost-effective for high volume

        // Recent usage indicates availability
        val hoursSinceLastUse = (System.currentTimeMillis() - model.lastUsed) / 3600000
        if (hoursSinceLastUse < 24) score += 0.1f

        // Model type suitability
        when (model.modelType) {
            ModelType.OPENAI -> score += 0.25f // Good for customer service
            ModelType.CLAUDE -> score += 0.2f
            else -> score += 0.1f
        }

        return score
    }

    private fun getDocumentProcessingScore(model: AIModelConfig, contextInfo: Map<String, Any>?): Float {
        var score = 0f

        // Long context is critical for document processing
        when {
            model.maxTokens >= 32768 -> score += 0.4f
            model.maxTokens >= 16384 -> score += 0.3f
            model.maxTokens >= 8192 -> score += 0.2f
        }

        // Claude excels at long documents
        if (model.modelType == ModelType.CLAUDE) score += 0.2f

        // Recent usage for stability
        val daysSinceLastUse = (System.currentTimeMillis() - model.lastUsed) / 86400000
        if (daysSinceLastUse < 7) score += 0.1f

        return score.coerceAtMost(1f)
    }

    private fun getReasoningScore(model: AIModelConfig, contextInfo: Map<String, Any>?): Float {
        var score = 0f

        // High token capacity for complex reasoning
        when {
            model.maxTokens >= 16384 -> score += 0.4f
            model.maxTokens >= 8192 -> score += 0.2f
        }

        // High-quality reasoning models
        when {
            model.model.contains("opus", ignoreCase = true) -> score += 0.3f
            model.model.contains("sonnet", ignoreCase = true) -> score += 0.2f
            model.modelType == ModelType.OPENAI -> score += 0.2f
        }

        return score.coerceAtMost(1f)
    }

    private fun getCodingScore(model: AIModelConfig, contextInfo: Map<String, Any>?): Float {
        var score = 0f

        // Code assistance benefits from large context
        if (model.maxTokens >= 8192) score += 0.2f

        // OpenAI models are strong for coding
        if (model.modelType == ModelType.OPENAI || model.model.contains("gpt", ignoreCase = true)) {
            score += 0.3f
        }

        return score.coerceAtMost(1f)
    }

    private fun getVisionScore(model: AIModelConfig, contextInfo: Map<String, Any>?): Float {
        var score = 0f

        // Vision capabilities
        if (model.apiEndpoint.contains("vision") || model.model.contains("vision")) {
            score += 0.5f
        }

        // Token capacity for image analysis
        if (model.maxTokens >= 4096) score += 0.2f

        return score.coerceAtMost(1f)
    }

    private fun getTranslationScore(model: AIModelConfig, contextInfo: Map<String, Any>?): Float {
        var score = 0f

        // Translation needs balanced performance
        if (model.maxTokens >= 8192) score += 0.2f
        if (model.monthlyCost < 2.0) score += 0.2f

        // All model types can handle translation reasonably well
        score += 0.3f

        return score.coerceAtMost(1f)
    }

    private fun getDataAnalysisScore(model: AIModelConfig, contextInfo: Map<String, Any>?): Float {
        var score = 0f

        // Data analysis needs high token capacity
        if (model.maxTokens >= 16384) score += 0.3f
        if (model.maxTokens >= 8192) score += 0.1f

        // Recent usage indicates model stability
        val daysSinceLastUse = (System.currentTimeMillis() - model.lastUsed) / 86400000
        if (daysSinceLastUse < 7) score += 0.2f

        return score.coerceAtMost(1f)
    }

    private fun getSummarizationScore(model: AIModelConfig, contextInfo: Map<String, Any>?): Float {
        var score = 0f

        // Summarization needs good context understanding
        if (model.maxTokens >= 16384) score += 0.3f
        if (model.maxTokens >= 8192) score += 0.1f

        // Claude is particularly good at summarization
        if (model.modelType == ModelType.CLAUDE) score += 0.2f

        return score.coerceAtMost(1f)
    }

    private fun getCostEfficiencyScore(model: AIModelConfig): Float {
        return when {
            model.monthlyCost <= 1.0 -> 0.3f
            model.monthlyCost <= 3.0 -> 0.2f
            model.monthlyCost <= 5.0 -> 0.1f
            else -> 0f
        }
    }

    private fun getAvailabilityScore(model: AIModelConfig): Float {
        val now = System.currentTimeMillis()
        val hoursSinceLastUse = (now - model.lastUsed) / 3600000

        return when {
            hoursSinceLastUse < 1 -> 0.2f // Very fresh
            hoursSinceLastUse < 6 -> 0.15f // Recent
            hoursSinceLastUse < 24 -> 0.1f // Today
            else -> 0.05f // Older but still valid
        }
    }

    private fun generateDetailedRoutingReasoning(
        score: Float,
        model: AIModelConfig,
        taskType: TaskType,
        contextInfo: Map<String, Any>?
    ): String {
        val reasons = mutableListOf<String>()
        val metrics = requestMetrics[model.id.toString()]

        // Performance-based reasons
        metrics?.let { m ->
            if (m.successRate > 0.95f) reasons.add("历史成功率${(m.successRate * 100).toInt()}%")
            if (m.avgResponseTimeMs < 2000) reasons.add("平均响应时间${m.avgResponseTimeMs}ms")
        }

        // Task-specific reasons
        val capabilityReasons = getTaskCapabilityReasons(model, taskType)
        reasons.addAll(capabilityReasons)

        // Context-based reasons
        contextInfo?.let { ctx ->
            ctx["urgency"]?.let { urgency ->
                if (urgency == "high") reasons.add("适合紧急任务")
            }
            ctx["complexity"]?.let { complexity ->
                if (complexity == "high") reasons.add("复杂任务优化")
            }
        }

        // Cost reasons
        when {
            model.monthlyCost <= 1.0 -> reasons.add("高性价比")
            model.monthlyCost <= 3.0 -> reasons.add("成本效益好")
        }

        return reasons.take(5).joinToString("；") // Limit to top 5 reasons
    }

    private fun getTaskCapabilityReasons(model: AIModelConfig, taskType: TaskType): List<String> {
        val reasons = mutableListOf<String>()

        when (taskType) {
            TaskType.GENERAL_CHAT -> {
                if (model.maxTokens >= 8192) reasons.add("支持长对话")
                when (model.modelType) {
                    ModelType.OPENAI -> reasons.add("GPT系列，对话能力强")
                    ModelType.CLAUDE -> reasons.add("Claude，自然语言处理优秀")
                    else -> reasons.add("通用对话模型")
                }
            }
            TaskType.CUSTOMER_SERVICE -> {
                if (model.monthlyCost < 3.0) reasons.add("客服成本优化")
                if (model.maxTokens >= 4096) reasons.add("支持客户服务场景")
            }
            TaskType.DOCUMENT_PROCESSING -> {
                if (model.maxTokens >= 32768) reasons.add("超长上下文文档处理")
                if (model.modelType == ModelType.CLAUDE) reasons.add("Claude擅长文档理解")
            }
            TaskType.REASONING -> {
                if (model.maxTokens >= 16384) reasons.add("复杂推理能力")
                if (model.model.contains("opus", ignoreCase = true)) reasons.add("高级推理模型")
            }
            TaskType.CODING_ASSISTANCE -> {
                if (model.modelType == ModelType.OPENAI) reasons.add("OpenAI在代码生成方面表现优异")
                if (model.maxTokens >= 8192) reasons.add("支持代码分析")
            }
            TaskType.VISION_TASKS -> {
                if (model.model.contains("vision", ignoreCase = true)) reasons.add("具备图像理解能力")
                if (model.maxTokens >= 4096) reasons.add("支持多模态任务")
            }
            else -> {
                if (model.maxTokens >= 4096) reasons.add("通用任务能力强")
                if (model.monthlyCost < 2.0) reasons.add("性价比高")
            }
        }

        return reasons
    }

    private fun getModelDisplayName(model: AIModelConfig): String {
        return when (model.modelType) {
            ModelType.OPENAI -> "OpenAI"
            ModelType.CLAUDE -> "Claude"
            ModelType.ZHIPU -> "智谱AI"
            ModelType.TONGYI -> "通义千问"
            ModelType.CUSTOM -> "自定义模型"
        }
    }

    private fun calculateModelScore(
        model: AIModelConfig,
        taskType: TaskType
    ): Float {
        var score = 0f

        // 基础评分规则
        when (taskType) {
            TaskType.GENERAL_CHAT -> {
                // 通用对话平衡性能和成本
                if (model.maxTokens >= 4096) score += 0.2f
                if (model.monthlyCost < 2.0) score += 0.3f
            }
            TaskType.CUSTOMER_SERVICE -> {
                // 客服需要快速响应
                if (model.lastUsed > System.currentTimeMillis() - 3600000) score += 0.3f // 最近1小时内使用的优先
                if (model.monthlyCost < 3.0) score += 0.2f // 成本控制
            }
            TaskType.INFORMATION_QUERY -> {
                // 信息查询需要平衡性能和成本
                if (model.maxTokens >= 4096) score += 0.2f
                if (model.monthlyCost < 1.5) score += 0.2f
            }
            TaskType.DATA_ANALYSIS -> {
                // 数据分析需要大token和稳定性
                if (model.maxTokens >= 16384) score += 0.3f
                if (model.lastUsed > System.currentTimeMillis() - 86400000) score += 0.2f // 近期使用过的优先
            }
            TaskType.DOCUMENT_PROCESSING, TaskType.SUMMARIZATION -> {
                // 文档处理需要长上下文
                if (model.maxTokens >= 32768) score += 0.5f
                if (model.lastUsed > System.currentTimeMillis() - 86400000) score += 0.2f // 近期使用过的优先
            }
            TaskType.REASONING, TaskType.MATHEMATICAL_CALC -> {
                // 推理任务需要高精度和大token
                if (model.maxTokens >= 16384) score += 0.4f
                if (model.monthlyCost < 5.0) score += 0.1f // 成本考量
            }
            TaskType.CODING_ASSISTANCE, TaskType.CODE_REVIEW -> {
                // 代码任务优先选择支持长上下文的模型
                if (model.maxTokens >= 8192) score += 0.3f
                if (model.apiEndpoint.contains("openai") || model.model.contains("gpt")) score += 0.2f
            }
            TaskType.CONTENT_WRITING -> {
                // 内容创作需要创意能力和稳定性
                if (model.maxTokens >= 8192) score += 0.2f
                if (model.lastUsed > System.currentTimeMillis() - 86400000) score += 0.3f
            }
            TaskType.TRANSLATION -> {
                // 翻译需要平衡性能和准确性
                if (model.maxTokens >= 8192) score += 0.3f
                if (model.monthlyCost < 3.0) score += 0.2f
            }
            TaskType.IMAGE_DESCRIPTION, TaskType.VISION_TASKS -> {
                // 图像描述需要多模态能力
                if (model.apiEndpoint.contains("vision") || model.model.contains("vision")) score += 1.0f
                if (model.maxTokens >= 4096) score += 0.2f
            }
        }

        // 默认加分项
        if (model.isEnabled && !model.monthlyCost.isNaN()) {
            score += 0.1f
        }

        return score.coerceIn(0f, 1f)
    }

    private fun generateRoutingReasoning(
        score: Float,
        model: AIModelConfig,
        taskType: TaskType
    ): String {
        val reasons = mutableListOf<String>()

        when (taskType) {
            TaskType.GENERAL_CHAT -> {
                if (model.maxTokens >= 4096)
                    reasons.add("支持通用对话")
                if (model.monthlyCost < 2.0)
                    reasons.add("性价比高")
                if (score > 0.7f)
                    reasons.add("综合评分最高 (${String.format("%.1f", score * 100)}%)")
            }
            TaskType.CUSTOMER_SERVICE -> {
                if (model.lastUsed > System.currentTimeMillis() - 3600000)
                    reasons.add("响应速度快，适合实时客服")
                if (model.monthlyCost < 3.0)
                    reasons.add("成本效益好")
            }
            TaskType.INFORMATION_QUERY -> {
                if (model.maxTokens >= 4096)
                    reasons.add("支持信息查询任务")
                if (model.monthlyCost < 1.5)
                    reasons.add("低成本高效")
            }
            TaskType.DATA_ANALYSIS -> {
                if (model.maxTokens >= 16384)
                    reasons.add("支持大数据分析")
                if (model.lastUsed > System.currentTimeMillis() - 86400000)
                    reasons.add("近期使用过，性能稳定")
            }
            TaskType.DOCUMENT_PROCESSING, TaskType.SUMMARIZATION -> {
                if (model.maxTokens >= 32768)
                    reasons.add("擅长处理长文档和复杂文本")
                if (model.lastUsed > System.currentTimeMillis() - 86400000)
                    reasons.add("近期使用过，性能稳定")
            }
            TaskType.REASONING, TaskType.MATHEMATICAL_CALC -> {
                if (model.maxTokens >= 16384)
                    reasons.add("支持复杂推理所需的长时间上下文")
                if (model.monthlyCost < 5.0)
                    reasons.add("性价比高的推理模型")
            }
            TaskType.CODING_ASSISTANCE, TaskType.CODE_REVIEW -> {
                if (model.maxTokens >= 8192)
                    reasons.add("支持足够长的上下文用于代码分析")
                if (model.model.contains("gpt"))
                    reasons.add("GPT模型在代码生成方面表现优秀")
            }
            TaskType.CONTENT_WRITING -> {
                if (model.maxTokens >= 8192)
                    reasons.add("支持内容创作")
                if (model.lastUsed > System.currentTimeMillis() - 86400000)
                    reasons.add("近期使用过，创作能力稳定")
            }
            TaskType.TRANSLATION -> {
                if (model.maxTokens >= 8192)
                    reasons.add("支持翻译任务")
                if (model.monthlyCost < 3.0)
                    reasons.add("翻译性价比高")
            }
            TaskType.IMAGE_DESCRIPTION, TaskType.VISION_TASKS -> {
                if (model.apiEndpoint.contains("vision") || model.model.contains("vision"))
                    reasons.add("具备图像理解能力")
                if (model.maxTokens >= 4096)
                    reasons.add("支持多模态任务")
            }
        }

        return reasons.joinToString("；")
    }
}

// 数据类定义
data class ModelScore(
    val model: AIModelConfig,
    val score: Float,
    val reasoning: String
)

sealed class RoutingResult {
    data class SingleChoice(val selectedModel: ModelScore) : RoutingResult()
    data class MultipleChoices(val candidates: List<ModelScore>) : RoutingResult()
    data class NoSuitableModel(val reason: String) : RoutingResult()
}

enum class TaskType {
    GENERAL_CHAT,        // 通用对话
    CUSTOMER_SERVICE,    // 客户服务
    INFORMATION_QUERY,   // 信息查询
    CODING_ASSISTANCE,   // 编程协助
    DATA_ANALYSIS,       // 数据分析
    DOCUMENT_PROCESSING, // 文档处理
    REASONING,           // 逻辑推理
    MATHEMATICAL_CALC,   // 数学计算
    CODE_REVIEW,         // 代码审查
    CONTENT_WRITING,     // 内容创作
    TRANSLATION,         // 翻译任务
    SUMMARIZATION,       // 文本摘要
    IMAGE_DESCRIPTION,   // 图像描述
    VISION_TASKS         // 视觉任务
}