package com.csbaby.kefu.infrastructure.ai

import com.csbaby.kefu.domain.model.AIModelConfig
import com.csbaby.kefu.domain.model.ModelType
import com.csbaby.kefu.infrastructure.simple.TaskType
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.ConcurrentHashMap

/**
 * Comprehensive tests for AIService performance and functionality.
 */
class AIServiceTest {

    private val mockAIClient = mock<com.csbaby.kefu.data.remote.AIClient>()
    private val mockModelRepository = mock<com.csbaby.kefu.domain.repository.AIModelRepository>()
    private val mockTaskRouter = mock<com.csbaby.kefu.infrastructure.simple.SimpleTaskRouter>()

    @Test
    fun `test cache eviction LRU behavior`() = runTest {
        // Setup
        val service = createTestService()
        val cacheKey1 = "test_key_1"
        val cacheKey2 = "test_key_2"
        val cacheKey3 = "test_key_3"

        // Fill cache beyond capacity
        repeat(10) { index ->
            service.cacheResponse("key_$index", "response_$index")
        }

        // Access key1 to make it most recently used
        service.getCachedResponse(cacheKey1)

        // Add new entry - should evict oldest (key_9)
        service.cacheResponse(cacheKey3, "new_response")

        // Verify key9 is evicted
        assertNull(service.getCachedResponse("key_9"))

        // Verify key1 is still there (was accessed)
        assertNotNull(service.getCachedResponse(cacheKey1))

        // Verify key3 is there
        assertEquals("new_response", service.getCachedResponse(cacheKey3))
    }

    @Test
    fun `test performance metrics calculation`() = runTest {
        // Setup
        val service = createTestService()

        // Simulate some requests
        service.recordRequestSuccess(1L, 1500L, 1000) // modelId=1, responseTime=1500ms, tokens=1000
        service.recordRequestSuccess(1L, 800L, 500)   // modelId=1, responseTime=800ms, tokens=500

        service.recordRequestFailure(2L, 3000L)       // modelId=2 failed

        val stats = service.getPerformanceStats()

        assertEquals(2, stats["total_requests"])
        assertEquals(2, stats["total_successful"])
        assertTrue(stats["overall_success_rate"] as Float > 0.7f)
    }

    @Test
    fun `test adaptive retry strategy`() = runTest {
        // Setup
        val service = createTestService()
        val prompt = "Test prompt"

        // Mock repository to return test models
        whenever(mockModelRepository.getAllModels()).thenReturn(
            kotlinx.coroutines.flow.flowOf(listOf(
                AIModelConfig(
                    id = 1,
                    modelType = ModelType.OPENAI,
                    modelName = "gpt-3.5-turbo",
                    model = "gpt-3.5-turbo",
                    apiKey = "test-key",
                    apiEndpoint = "https://api.openai.com/v1/chat/completions"
                )
            ))
        )

        // Mock client to fail first time, succeed second time
        var callCount = 0
        whenever(mockAIClient.generateCompletion(any(), any(), any(), any())).thenAnswer {
            callCount++
            if (callCount == 1) {
                Result.failure(Exception("Network error"))
            } else {
                Result.success("Test response")
            }
        }

        // Test that retry mechanism works
        val result = service.generateCompletion(prompt)

        assertTrue(result.isSuccess)
        assertEquals("Test response", result.getOrNull())
    }

    @Test
    fun `test error classification`() = runTest {
        val service = createTestService()

        val networkError = Exception("Connection timeout")
        val authError = Exception("Invalid API key")
        val rateLimitError = Exception("Rate limit exceeded")

        assertEquals("NETWORK", service.classifyError(networkError))
        assertEquals("AUTH", service.classifyError(authError))
        assertEquals("RATE_LIMIT", service.classifyError(rateLimitError))
    }

    @Test
    fun `test usage limit calculation`() = runTest {
        val service = createTestService()

        val expensiveModel = AIModelConfig(
            id = 1,
            modelType = ModelType.CLAUDE,
            modelName = "claude-3-opus",
            monthlyCost = 10.0, // Above limit
            model = "claude-3-opus-20240307",
            apiKey = "test-key",
            apiEndpoint = "https://api.anthropic.com/v1/messages"
        )

        val affordableModel = AIModelConfig(
            id = 2,
            modelType = ModelType.OPENAI,
            modelName = "gpt-3.5-turbo",
            monthlyCost = 2.0, // Below limit
            model = "gpt-3.5-turbo",
            apiKey = "test-key",
            apiEndpoint = "https://api.openai.com/v1/chat/completions"
        )

        assertFalse(service.hasReachedUsageLimit(affordableModel))
        assertTrue(service.hasReachedUsageLimit(expensiveModel))
    }

    @Test
    fun `test model performance scoring`() = runTest {
        val service = createTestService()

        val modelWithGoodMetrics = AIModelConfig(
            id = 1,
            modelType = ModelType.OPENAI,
            modelName = "gpt-4",
            maxTokens = 8192,
            monthlyCost = 1.5,
            model = "gpt-4",
            apiKey = "test-key",
            apiEndpoint = "https://api.openai.com/v1/chat/completions"
        )

        val score = service.calculateModelPerformanceScore(modelWithGoodMetrics, 1000)
        assertTrue("Score should be positive", score > 0f)
        assertTrue("High-quality model should have good score", score > 0.5f)
    }

    @Test
    fun `test context-aware routing`() = runTest {
        val service = createTestService()

        val models = listOf(
            AIModelConfig(
                id = 1,
                modelType = ModelType.OPENAI,
                modelName = "gpt-3.5-turbo",
                maxTokens = 4096,
                monthlyCost = 1.0,
                model = "gpt-3.5-turbo",
                apiKey = "test-key",
                apiEndpoint = "https://api.openai.com/v1/chat/completions"
            ),
            AIModelConfig(
                id = 2,
                modelType = ModelType.CLAUDE,
                modelName = "claude-3-haiku",
                maxTokens = 32768,
                monthlyCost = 3.0,
                model = "claude-3-haiku-20240307",
                apiKey = "test-key",
                apiEndpoint = "https://api.anthropic.com/v1/messages"
            )
        )

        // Test with urgent context
        val urgentContext = mapOf("urgency" to "high", "complexity" to "low")
        val urgentResult = service.enhancedSelectBestModel(TaskType.CUSTOMER_SERVICE, models, urgentContext)

        // Should prefer faster, more reliable model for urgent tasks
        assertTrue("Should select appropriate model for urgent task", urgentResult is com.csbaby.kefu.infrastructure.simple.RoutingResult.SingleChoice)
    }

    @Test
    fun `test cache key generation`() = runTest {
        val service = createTestService()

        val prompt = "Test prompt"
        val systemPrompt = "You are a helpful assistant"
        val modelId = 1L

        val cacheKey = service.generateCacheKey(prompt, systemPrompt, modelId)
        assertEquals("1:You are a helpful assistant:Test prompt", cacheKey)
    }

    @Test
    fun `test empty prompt validation`() = runTest {
        val service = createTestService()

        val result = service.validateRequest("test-api-key", "", 1000)
        assertFalse("Empty prompt should be invalid", result.isSuccess)
    }

    private fun createTestService(): AIService {
        return AIService(mockAIClient, mockModelRepository, mockTaskRouter)
    }
}