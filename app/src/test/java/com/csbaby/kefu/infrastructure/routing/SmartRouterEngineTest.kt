package com.csbaby.kefu.infrastructure.routing

import com.csbaby.kefu.data.local.*
import com.csbaby.kefu.domain.model.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SmartRouterEngineTest {

    private lateinit var routerEngine: SmartRouterEngine
    private lateinit var mockCapability: ModelCapability

    @Before
    fun setup() {
        routerEngine = SmartRouterEngine()

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