package com.csbaby.kefu.infrastructure.routing

import com.csbaby.kefu.data.local.*
import com.csbaby.kefu.domain.model.*
import com.csbaby.kefu.infrastructure.simple.RoutingResult
import com.csbaby.kefu.infrastructure.simple.TaskType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SmartRouterEngineTest {

    private lateinit var routerEngine: SmartRouterEngine
    private lateinit var mockCapability: ModelCapability

    @Before
    fun setup() {
        // SmartRouterEngine 需要 modelType 和 modelName 参数
        routerEngine = SmartRouterEngine(
            modelType = com.csbaby.kefu.domain.model.ModelType.CUSTOM,
            modelName = "Test Router Engine"
        )

        mockCapability = ModelCapability(
            modelId = "test-model",
            vendorId = "test-vendor",
            maxTokens = 32768,
            supportsStreaming = true,
            capabilities = setOf(ModelFeature.CODING, ModelFeature.HIGH_ACCURACY),
            performanceScores = mapOf(
                TaskType.CODING_ASSISTANCE to 0.9f,
                TaskType.GENERAL_CHAT to 0.8f
            ),
            costPerToken = 0.00002,
            avgResponseTime = 1000
        )
        ModelCapabilitiesRegistry.registerModelCapability(mockCapability)
    }

    @Test
    fun `selectBestModel returns single choice when one model available`() = runTest {
        val model = AIModelConfig(
            model = "test-model",
            modelName = "Test Model",
            apiKey = "test-key",
            apiEndpoint = "https://api.test.com/v1/chat/completions"
        )

        val taskContext = TaskContext(taskType = TaskType.CODING_ASSISTANCE)
        val availableModels = listOf(model)

        val result = routerEngine.selectBestModel(taskContext, availableModels)

        assertTrue(result is RoutingResult.SingleChoice)
        assertEquals(model, result.selectedModel.model)
    }

    @Test
    fun `calculateBasicCapabilityScore gives higher score for coding tasks`() {
        val taskContext = TaskContext(taskType = TaskType.CODING_ASSISTANCE)
        val score = routerEngine.calculateModelScore(
            model = AIModelConfig(apiKey = "key", apiEndpoint = "url"),
            capability = mockCapability,
            taskContext = taskContext
        )

        assertTrue("Coding task should have high score", score > 0.7f)
    }

    @Test
    fun `routingReasoning includes capability explanations`() {
        val model = AIModelConfig(apiKey = "key", apiEndpoint = "url")
        val taskContext = TaskContext(taskType = TaskType.CODING_ASSISTANCE)

        val reasoning = routerEngine.generateRoutingReasoning(
            score = 0.8f,
            model = model,
            capability = mockCapability,
            taskContext = taskContext
        )

        assertTrue("Should mention coding capability", reasoning.contains("代码"))
        assertTrue("Should include score info", reasoning.contains("评分"))
    }

    @Test
    fun `multiple choices returns top candidates`() = runTest {
        val model1 = AIModelConfig(model = "test-model-1", apiKey = "key1", apiEndpoint = "url1")
        val model2 = AIModelConfig(model = "test-model-2", apiKey = "key2", apiEndpoint = "url2")
        val taskContext = TaskContext(taskType = TaskType.GENERAL_CHAT)
        val availableModels = listOf(model1, model2)

        val result = routerEngine.selectBestModel(taskContext, availableModels)

        assertTrue(result is RoutingResult.MultipleChoices)
        assertEquals(2, result.candidates.size)
    }

    @Test
    fun `no suitable model when no capabilities match`() = runTest {
        val capability = ModelCapability(
            modelId = "mismatch-model",
            vendorId = "test-vendor",
            maxTokens = 4096,
            supportsStreaming = true,
            capabilities = emptySet(),
            performanceScores = mapOf(TaskType.GENERAL_CHAT to 0.1f),
            costPerToken = 0.00001,
            avgResponseTime = 500
        )
        ModelCapabilitiesRegistry.registerModelCapability(capability)

        val model = AIModelConfig(
            model = "mismatch-model",
            modelName = "Mismatch Model",
            apiKey = "test-key",
            apiEndpoint = "https://api.test.com/v1/chat/completions"
        )

        val taskContext = TaskContext(taskType = TaskType.CODING_ASSISTANCE)
        val availableModels = listOf(model)

        val result = routerEngine.selectBestModel(taskContext, availableModels)

        assertTrue(result is RoutingResult.NoSuitableModel)
        assertTrue(result.reason.contains("没有适合"))
    }
}

// 测试用的数据类定义
data class TaskContext(
    val taskType: TaskType,
    val inputLength: Int = 0,
    val requiresFastResponse: Boolean = false
)

data class ModelCapability(
    val modelId: String,
    val vendorId: String,
    val maxTokens: Int,
    val supportsStreaming: Boolean,
    val capabilities: Set<ModelFeature>,
    val performanceScores: Map<TaskType, Float>,
    val costPerToken: Double,
    val avgResponseTime: Int
)

enum class ModelFeature {
    CODING,           // 代码能力
    HIGH_ACCURACY,    // 高准确性
    FAST_RESPONSE,    // 快速响应
    LONG_CONTEXT,     // 长上下文支持
    MULTIMODAL,       // 多模态支持
    REASONING         // 推理能力
}

object ModelCapabilitiesRegistry {
    private val capabilities = mutableMapOf<String, ModelCapability>()

    fun registerModelCapability(capability: ModelCapability) {
        capabilities[capability.modelId] = capability
    }

    fun getModelCapability(modelId: String): ModelCapability? {
        return capabilities[modelId]
    }

    fun getAllCapabilities(): List<ModelCapability> {
        return capabilities.values.toList()
    }
}