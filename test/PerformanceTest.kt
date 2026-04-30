package com.csbaby.kefu.infrastructure.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Performance tests for AI service components.
 */
@RunWith(AndroidJUnit4::class)
class PerformanceTest {

    @Test
    fun `test cache performance with LRU eviction`() = runTest {
        // Given
        val cacheSize = 3
        val cache = LRUCache<String, String>(cacheSize)

        // When - Fill cache beyond capacity
        repeat(cacheSize + 1) { index ->
            cache.put("key_$index", "value_$index")
        }

        // Then - Oldest entry should be evicted
        assertFalse("Oldest key should be evicted", cache.containsKey("key_0"))
        assertTrue("Newest keys should remain", cache.containsKey("key_3"))

        // Test access affects LRU order
        cache.get("key_1") // Make key_1 most recently used
        cache.put("key_4", "new_value") // Should evict key_2 (not accessed)

        assertFalse("key_2 should be evicted", cache.containsKey("key_2"))
        assertTrue("key_1 should still exist", cache.containsKey("key_1"))
        assertTrue("key_4 should exist", cache.containsKey("key_4"))
    }

    @Test
    fun `test concurrent cache operations`() = runTest {
        // Given
        val cache = ConcurrentCache<String, Int>(1000)

        // When - Simulate concurrent operations
        val jobs = (1..100).map { index ->
            kotlinx.coroutines.async {
                cache.put("key_$index", index)
                cache.get("key_$index")
            }
        }

        // Then - All operations should complete without race conditions
        val results = jobs.awaitAll()
        assertEquals(100, results.size)
        results.forEachIndexed { index, result ->
            assertEquals(index + 1, result)
        }
    }

    @Test
    fun `test rate limiting performance`() = runTest {
        // Given
        val rateLimiter = AdaptiveRateLimiter(
            baseLimit = 60,
            windowMs = 60000
        )

        val now = System.currentTimeMillis()

        // When - Add many requests within limit
        val startTime = System.nanoTime()
        repeat(60) { index ->
            assertTrue("Request $index should be allowed",
                rateLimiter.tryAcquire(now + (index * 1000)))
        }
        val firstLimitHitTime = System.nanoTime()

        // Add more requests beyond limit
        val secondBatchStartTime = System.nanoTime()
        repeat(10) { index ->
            assertFalse("Requests beyond limit should be rejected",
                rateLimiter.tryAcquire(now + 61000 + (index * 100)))
        }
        val secondBatchEndTime = System.nanoTime()

        // Verify timing constraints
        val firstLimitDuration = TimeUnit.NANOSECONDS.toMillis(firstLimitHitTime - startTime)
        val secondBatchDuration = TimeUnit.NANOSECONDS.toMillis(secondBatchEndTime - secondBatchStartTime)

        assertTrue("Should process all allowed requests quickly",
            firstLimitDuration < 5000) // Should complete within 5 seconds
        assertTrue("Rejection batch should also be fast",
            secondBatchDuration < 1000) // Rejections should be immediate
    }

    @Test
    fun `test adaptive timeout calculation`() = runTest {
        // Given
        val timeoutCalculator = AdaptiveTimeoutCalculator()

        // Test Claude model (should have longer timeouts)
        val claudeConfig = mapOf(
            "modelType" to "CLAUDE",
            "maxTokens" to 8192,
            "apiEndpoint" to "https://api.anthropic.com"
        )
        val claudeTimeout = timeoutCalculator.calculate(claudeConfig)

        // Test OpenAI model
        val openaiConfig = mapOf(
            "modelType" to "OPENAI",
            "maxTokens" to 4096,
            "apiEndpoint" to "https://api.openai.com"
        )
        val openaiTimeout = timeoutCalculator.calculate(openaiConfig)

        // Test NVIDIA API
        val nvidiaConfig = mapOf(
            "modelType" to "CUSTOM",
            "maxTokens" to 32768,
            "apiEndpoint" to "https://integrate.api.nvidia.com"
        )
        val nvidiaTimeout = timeoutCalculator.calculate(nvidiaConfig)

        // Then
        assertTrue("Claude should have longer timeouts than OpenAI",
            claudeTimeout > openaiTimeout)
        assertTrue("NVIDIA API should have longer timeouts for large tokens",
            nvidiaTimeout >= openaiTimeout)
        assertTrue("All timeouts should be reasonable",
            claudeTimeout < 300000) // Less than 5 minutes
    }

    @Test
    fun `test request metrics collection`() = runTest {
        // Given
        val metricsCollector = RequestMetricsCollector()

        // When - Record various request outcomes
        metricsCollector.recordSuccess(1L, 1500L, 1000) // modelId, duration, tokens
        metricsCollector.recordSuccess(1L, 800L, 500)
        metricsCollector.recordFailure(2L, 3000L)
        metricsCollector.recordSuccess(3L, 2000L, 2000)

        // Then
        val stats = metricsCollector.getStats()
        assertEquals(4, stats.totalRequests)
        assertEquals(3, stats.totalSuccessful)
        assertEquals(1, stats.totalFailures)
        assertTrue("Success rate should be high", stats.successRate > 0.7f)
        assertEquals(3300L, stats.totalDuration) // Sum of successful durations
    }

    @Test
    fun `test cost estimation accuracy`() = runTest {
        // Given
        val costEstimator = CostEstimator()

        // Test different model types
        val gpt35Cost = costEstimator.estimatePerToken("gpt-3.5-turbo")
        val gpt4Cost = costEstimator.estimatePerToken("gpt-4")
        val claudeCost = costEstimator.estimatePerToken("claude-3-opus")

        // Then
        assertNotNull("GPT-3.5 cost should be estimated", gpt35Cost)
        assertNotNull("GPT-4 cost should be estimated", gpt4Cost)
        assertNotNull("Claude cost should be estimated", claudeCost)
        assertTrue("GPT-4 should be more expensive than GPT-3.5", gpt4Cost > gpt35Cost)
        assertTrue("Claude should be cheaper than GPT-4", claudeCost < gpt4Cost)
    }

    @Test
    fun `test model selection performance`() = runTest {
        // Given
        val modelSelector = ModelSelector()

        val models = listOf(
            ModelInfo(
                id = 1L,
                name = "gpt-3.5-turbo",
                type = ModelType.OPENAI,
                costPerToken = 0.0015,
                maxTokens = 4096
            ),
            ModelInfo(
                id = 2L,
                name = "claude-3-haiku",
                type = ModelType.CLAUDE,
                costPerToken = 0.003,
                maxTokens = 32768
            ),
            ModelInfo(
                id = 3L,
                name = "glm-4",
                type = ModelType.ZHIPU,
                costPerToken = 0.002,
                maxTokens = 8192
            )
        )

        // Test urgent task routing
        val urgentContext = mapOf("urgency" to "high", "complexity" to "low")
        val urgentResult = modelSelector.selectBestModel(models, urgentContext)

        // Test complex task routing
        val complexContext = mapOf("urgency" to "medium", "complexity" to "high")
        val complexResult = modelSelector.selectBestModel(models, complexContext)

        // Then
        assertNotNull("Should select a model for urgent task", urgentResult)
        assertNotNull("Should select a model for complex task", complexResult)
        assertTrue("Urgent tasks should prefer faster models",
            urgentResult.modelName.contains("gpt-3.5-turbo"))
        assertTrue("Complex tasks should prefer higher capability models",
            complexResult.maxTokens > 8000)
    }

    @Test
    fun `test memory usage efficiency`() = runTest {
        // Given
        val maxCacheSize = 1000
        val cache = MemoryEfficientCache<String, String>(maxCacheSize)

        // When - Fill cache to near capacity
        val fillStartTime = System.nanoTime()
        repeat(maxCacheSize - 1) { index ->
            cache.put("key_$index", "value_$index".repeat(100)) // Larger values
        }
        val fillEndTime = System.nanoTime()

        // Add one more to trigger eviction
        cache.put("overflow_key", "overflow_value".repeat(50))

        val fillDuration = TimeUnit.NANOSECONDS.toMillis(fillEndTime - fillStartTime)

        // Then
        assertTrue("Filling cache should be reasonably fast",
            fillDuration < 1000) // Should complete within 1 second
        assertTrue("Overflow key should be in cache",
            cache.containsKey("overflow_key"))
        assertTrue("First key should have been evicted",
            !cache.containsKey("key_0"))
    }

    @Test
    fun `test error classification performance`() = runTest {
        // Given
        val errorClassifier = ErrorClassifier()

        // Test various error types
        val networkErrors = listOf(
            Exception("UnknownHostException"),
            Exception("SocketTimeoutException"),
            Exception("ConnectException")
        )

        val authErrors = listOf(
            Exception("Invalid API Key"),
            Exception("Unauthorized"),
            Exception("401 Unauthorized")
        )

        val rateLimitErrors = listOf(
            Exception("Too Many Requests"),
            Exception("Rate limit exceeded"),
            Exception("429 Too Many Requests")
        )

        // When & Then
        networkErrors.forEach { error ->
            val start = System.nanoTime()
            val classification = errorClassifier.classify(error)
            val end = System.nanoTime()

            assertEquals("NETWORK", classification)
            assertTrue("Classification should be fast", end - start < 1000000) // < 1ms
        }

        authErrors.forEach { error ->
            assertEquals("AUTH", errorClassifier.classify(error))
        }

        rateLimitErrors.forEach { error ->
            assertEquals("RATE_LIMIT", errorClassifier.classify(error))
        }
    }

    // Helper data classes for testing
    private data class ModelInfo(
        val id: Long,
        val name: String,
        val type: ModelType,
        val costPerToken: Double,
        val maxTokens: Int
    )

    private data class RequestMetrics(
        val totalRequests: Int,
        val totalSuccessful: Int,
        val totalFailures: Int,
        val successRate: Float,
        val totalDuration: Long
    )

    private data class ModelSelector {
        fun selectBestModel(models: List<ModelInfo>, context: Map<String, String>): ModelInfo {
            return when (context["urgency"]) {
                "high" -> models.minByOrNull { it.costPerToken } ?: models.first()
                else -> models.maxByOrNull { it.maxTokens } ?: models.first()
            }
        }
    }

    private class ErrorClassifier {
        fun classify(error: Exception): String {
            return when {
                error.message?.contains("network", ignoreCase = true) == true ||
                error.message?.contains("host", ignoreCase = true) == true -> "NETWORK"
                error.message?.contains("auth", ignoreCase = true) == true ||
                error.message?.contains("unauthorized", ignoreCase = true) == true -> "AUTH"
                error.message?.contains("limit", ignoreCase = true) == true -> "RATE_LIMIT"
                else -> "UNKNOWN"
            }
        }
    }
}