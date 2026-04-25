package com.csbaby.kefu.infrastructure.simple

import com.csbaby.kefu.domain.model.AIModelConfig

/**
 * 简化的智能任务路由器 - 无需注解处理
 */
class SimpleTaskRouter {

    /**
     * 为给定任务选择最佳模型
     */
    fun selectBestModel(
        taskType: TaskType,
        availableModels: List<AIModelConfig>
    ): RoutingResult {
        val modelScores = mutableListOf<ModelScore>()

        for (model in availableModels) {
            val score = calculateModelScore(model, taskType)

            if (score > 0) {
                modelScores.add(ModelScore(
                    model = model,
                    score = score,
                    reasoning = generateRoutingReasoning(score, model, taskType)
                ))
            }
        }

        return when {
            modelScores.isEmpty() -> RoutingResult.NoSuitableModel("没有适合此任务的模型")
            modelScores.size == 1 -> RoutingResult.SingleChoice(modelScores.first())
            else -> {
                val sortedScores = modelScores.sortedByDescending { it.score }
                val topCandidates = sortedScores.take(3)
                RoutingResult.MultipleChoices(topCandidates)
            }
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