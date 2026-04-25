// 智能任务路由系统演示

/**
 * 简化的智能任务路由器 - 功能验证
 *
 * 核心功能：
 * 1. 根据任务类型自动选择最佳AI模型
 * 2. 多维度评分算法（能力40% + 性能30% + 成本20% + 实时性10%）
 * 3. 自动故障转移和降级处理
 * 4. 完整的单元测试覆盖
 */

package com.csbaby.kefu.demo

import com.csbaby.kefu.domain.model.AIModelConfig
import com.csbaby.kefu.infrastructure.simple.SimpleTaskRouter
import com.csbaby.kefu.infrastructure.simple.TaskType
import com.csbaby.kefu.infrastructure.simple.RoutingResult

fun main() {
    println("🚀 智能任务路由系统演示")
    println("=" * 50)

    // 创建测试模型
    val models = createTestModels()
    val router = SimpleTaskRouter()

    // 演示不同任务类型的路由选择
    demonstrateRouting(router, models)
}

private fun createTestModels(): List<AIModelConfig> {
    return listOf(
        AIModelConfig(
            model = "gpt-4",
            modelName = "GPT-4 Turbo",
            apiKey = "key1",
            apiEndpoint = "https://api.openai.com/v1/chat/completions",
            maxTokens = 128000,
            monthlyCost = 8.0,
            lastUsed = System.currentTimeMillis(),
            isEnabled = true
        ),
        AIModelConfig(
            model = "longcat-pro",
            modelName = "LongCat Pro",
            apiKey = "key2",
            apiEndpoint = "https://api.longcat.ai/v1/chat/completions",
            maxTokens = 65536,
            monthlyCost = 2.5,
            lastUsed = System.currentTimeMillis() - 1800000, // 30分钟前
            isEnabled = true
        ),
        AIModelConfig(
            model = "deepseek-reasoner",
            modelName = "DeepSeek Reasoner",
            apiKey = "key3",
            apiEndpoint = "https://api.deepseek.com/v1/chat/completions",
            maxTokens = 64000,
            monthlyCost = 3.0,
            lastUsed = System.currentTimeMillis() - 3600000, // 1小时前
            isEnabled = true
        )
    )
}

private fun demonstrateRouting(router: SimpleTaskRouter, models: List<AIModelConfig>) {
    val taskTypes = listOf(
        TaskType.CUSTOMER_SERVICE to "客户服务",
        TaskType.CODING_ASSISTANCE to "代码协助",
        TaskType.DOCUMENT_PROCESSING to "文档处理",
        TaskType.REASONING to "逻辑推理",
        TaskType.GENERAL_CHAT to "通用对话"
    )

    taskTypes.forEach { (taskType, taskName) ->
        println("\n📋 任务类型: $taskName")
        println("-" * 30)

        val result = router.selectBestModel(taskType, models)
        when (result) {
            is RoutingResult.SingleChoice -> {
                println("✅ 推荐模型: ${result.selectedModel.model.modelName}")
                println("🎯 模型ID: ${result.selectedModel.model.model}")
                println("⭐ 综合评分: ${String.format("%.1f", result.selectedModel.score * 100)}%")
                println("💡 选择理由: ${result.selectedModel.reasoning}")
                println("📊 配置信息:")
                println("   - Token容量: ${result.selectedModel.model.maxTokens}")
                println("   - 月度费用: ¥${result.selectedModel.model.monthlyCost}")
                println("   - 最后使用: ${formatTime(result.selectedModel.model.lastUsed)}")
            }
            is RoutingResult.MultipleChoices -> {
                println("🔄 多个候选模型 (${result.candidates.size}个):")
                result.candidates.forEachIndexed { index, candidate ->
                    println("   ${index + 1}. ${candidate.model.modelName} (评分: ${String.format("%.1f", candidate.score * 100)}%)")
                }
            }
            is RoutingResult.NoSuitableModel -> {
                println("❌ 错误: ${result.reason}")
            }
        }
    }

    // 演示评分计算
    println("\n📈 评分算法演示")
    println("-" * 30)
    val testModel = models[1] // LongCat Pro
    val scores = mapOf(
        "编码辅助" to router.calculateModelScore(testModel, TaskType.CODING_ASSISTANCE),
        "客户服务" to router.calculateModelScore(testModel, TaskType.CUSTOMER_SERVICE),
        "文档处理" to router.calculateModelScore(testModel, TaskType.DOCUMENT_PROCESSING),
        "逻辑推理" to router.calculateModelScore(testModel, TaskType.REASONING),
        "通用对话" to router.calculateModelScore(testModel, TaskType.GENERAL_CHAT)
    )

    scores.forEach { (taskName, score) ->
        println("$taskName: ${String.format("%.1f", score * 100)}%")
    }
}

private fun formatTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val hours = diff / (1000 * 60 * 60)
    return if (hours < 1) "刚刚使用" else "${hours}小时前"
}

/**
 * 实际业务集成示例
 */
class BusinessIntegrationExample {

    /**
     * 在AIService中集成智能路由
     */
    suspend fun processCustomerMessage(message: String): Result<String> {
        // 创建任务上下文
        val taskContext = TaskContext(
            taskType = TaskType.CUSTOMER_SERVICE,
            inputLength = message.length,
            requiresFastResponse = true
        )

        // 使用智能路由选择模型
        return aiService.generateCompletionWithRouting(
            prompt = message,
            systemPrompt = "你是专业的客服助手",
            taskType = TaskType.CUSTOMER_SERVICE,
            temperature = 0.7f
        )
    }

    /**
     * 代码分析任务
     */
    suspend fun analyzeCode(code: String): Result<String> {
        return aiService.generateCompletionWithRouting(
            prompt = "请分析以下代码：$code",
            systemPrompt = "你是一个经验丰富的程序员",
            taskType = TaskType.CODING_ASSISTANCE,
            temperature = 0.3f
        )
    }

    /**
     * 文档摘要任务
     */
    suspend fun summarizeDocument(document: String): Result<String> {
        return aiService.generateCompletionWithRouting(
            prompt = "请总结以下内容：$document",
            systemPrompt = "你是一个专业的文档分析师",
            taskType = TaskType.DOCUMENT_PROCESSING,
            temperature = 0.5f
        )
    }

    /**
     * 推理任务
     */
    suspend fun performReasoning(reasoningTask: String): Result<String> {
        return aiService.generateCompletionWithRouting(
            prompt = "请进行推理分析：$reasoningTask",
            systemPrompt = "你是一个逻辑严密的思考者",
            taskType = TaskType.REASONING,
            temperature = 0.4f
        )
    }
}

/**
 * 路由策略优化建议
 */
object RoutingOptimization {

    /**
     * 根据业务需求调整评分权重
     */
    fun getOptimizedWeights(businessType: String): Map<String, Float> {
        return when (businessType) {
            "客服中心" -> mapOf(
                "实时性" to 0.4f,
                "成本控制" to 0.3f,
                "稳定性" to 0.3f
            )
            "开发团队" -> mapOf(
                "代码能力" to 0.5f,
                "长上下文" to 0.3f,
                "性价比" to 0.2f
            )
            "内容创作" -> mapOf(
                "创意能力" to 0.4f,
                "稳定性" to 0.4f,
                "多模态" to 0.2f
            )
            "数据分析" -> mapOf(
                "大token支持" to 0.6f,
                "准确性" to 0.3f,
                "成本效益" to 0.1f
            )
            else -> mapOf(
                "综合能力" to 0.4f,
                "性能表现" to 0.3f,
                "成本效益" to 0.2f,
                "实时性" to 0.1f
            )
        }
    }

    /**
     * 模型选择策略
     */
    fun getModelSelectionStrategy(budget: Double, priority: String): String {
        return when {
            budget > 10.0 && priority == "accuracy" -> "优先选择高精度模型如GPT-4、DeepSeek-R"
            budget < 3.0 && priority == "cost" -> "选择低成本模型如LongCat、通义千问Turbo"
            budget in 3.0..10.0 && priority == "balance" -> "平衡选择各厂商的中高端模型"
            else -> "根据具体任务类型选择最合适的模型"
        }
    }
}