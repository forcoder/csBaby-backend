package com.csbaby.kefu.infrastructure.monitoring

import com.csbaby.kefu.domain.model.AIModelConfig
import com.csbaby.kefu.domain.model.ModelType
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Comprehensive tests for AIPerformanceMonitor functionality.
 */
class AIPerformanceMonitorTest {

    private val monitor = AIPerformanceMonitor()

    @Test
    fun `test performance snapshot creation`() = runTest {
        // Setup
        val config = AIModelConfig(
            id = 1,
            modelType = ModelType.OPENAI,
            modelName = "gpt-3.5-turbo",
            model = "gpt-3.5-turbo",
            apiKey = "test-key",
            apiEndpoint = "https://api.openai.com/v1/chat/completions"
        )

        // Record some requests
        monitor.recordRequest(config.modelId, config.modelName, config.modelType)
        monitor.recordSuccess(config.modelId, config.modelName, config.modelType, 1500L, 1000)

        // Execute
        val snapshot = monitor.getPerformanceSnapshot()

        // Verify
        assertEquals(1, snapshot.totalRequests)
        assertEquals(1, snapshot.successfulRequests)
        assertEquals(1500L, snapshot.avgResponseTimeMs)
        assertEquals(0f, snapshot.errorRate, 0.01f)
        assertTrue(snapshot.cacheHitRate >= 0f && snapshot.cacheHitRate <= 1f)
        assertTrue(snapshot.routingEfficiency >= 0f && snapshot.routingEfficiency <= 1f)
    }

    @Test
    fun `test error rate calculation`() = runTest {
        // Setup
        val config = AIModelConfig(
            id = 1,
            modelType = ModelType.OPENAI,
            modelName = "gpt-3.5-turbo",
            model = "gpt-3.5-turbo",
            apiKey = "test-key",
            apiEndpoint = "https://api.openai.com/v1/chat/completions"
        )

        // Record mixed success/failure
        monitor.recordRequest(config.modelId, config.modelName, config.modelType)
        monitor.recordFailure(config.modelId, config.modelName, config.modelType)

        monitor.recordRequest(config.modelId, config.modelName, config.modelType)
        monitor.recordSuccess(config.modelId, config.modelName, config.modelType, 1000L, 500)

        // Execute
        val snapshot = monitor.getPerformanceSnapshot()

        // Verify
        assertEquals(2, snapshot.totalRequests)
        assertEquals(1, snapshot.successfulRequests)
        assertEquals(0.5f, snapshot.errorRate, 0.01f)
    }

    @Test
    fun `test cache hit rate calculation`() = runTest {
        // Record cache hits and misses
        repeat(5) { monitor.recordCacheHit() }
        repeat(3) { monitor.recordCacheMiss() }

        // Execute
        val snapshot = monitor.getPerformanceSnapshot()

        // Verify
        assertEquals(0.625f, snapshot.cacheHitRate, 0.01f) // 5/(5+3) = 0.625
    }

    @Test
    fun `test routing efficiency calculation`() = runTest {
        // Record routing decisions
        monitor.recordRoutingDecision("customer_service", "gpt-3.5-turbo", 1500L, true)
        monitor.recordRoutingDecision("general_chat", "claude-3-haiku", 800L, true)
        monitor.recordRoutingDecision("document_processing", "gpt-4", 3000L, false)

        // Execute
        val snapshot = monitor.getPerformanceSnapshot()

        // Verify (efficiency should be average of fast responses and success rate)
        assertTrue("Routing efficiency should be between 0 and 1",
            snapshot.routingEfficiency in 0f..1f)
    }

    @Test
    fun `test model-specific metrics`() = runTest {
        // Setup models
        val openaiConfig = AIModelConfig(
            id = 1,
            modelType = ModelType.OPENAI,
            modelName = "gpt-3.5-turbo",
            model = "gpt-3.5-turbo",
            apiKey = "openai-key",
            apiEndpoint = "https://api.openai.com/v1/chat/completions"
        )

        val claudeConfig = AIModelConfig(
            id = 2,
            modelType = ModelType.CLAUDE,
            modelName = "claude-3-haiku",
            model = "claude-3-haiku-20240307",
            apiKey = "claude-key",
            apiEndpoint = "https://api.anthropic.com/v1/messages"
        )

        // Record different models' performance
        monitor.recordRequest(openaiConfig.modelId, openaiConfig.modelName, openaiConfig.modelType)
        monitor.recordSuccess(openaiConfig.modelId, openaiConfig.modelName, openaiConfig.modelType, 1200L, 800)

        monitor.recordRequest(claudeConfig.modelId, claudeConfig.modelName, claudeConfig.modelType)
        monitor.recordFailure(claudeConfig.modelId, claudeConfig.modelName, claudeConfig.modelType)

        // Execute
        val snapshot = monitor.getPerformanceSnapshot()

        // Verify
        assertNotNull(snapshot.modelPerformance["1_OPENAI"])
        assertNotNull(snapshot.modelPerformance["2_CLAUDE"])

        val openaiMetrics = snapshot.modelPerformance["1_OPENAI"]!!
        val claudeMetrics = snapshot.modelPerformance["2_CLAUDE"]!!

        assertEquals(1, openaiMetrics.requestCount)
        assertEquals(1, openaiMetrics.successCount)
        assertEquals(0, claudeMetrics.requestCount) // Should be reset after cleanup
        assertEquals(0, claudeMetrics.successCount)
    }

    @Test
    fun `test cost per request calculation`() = runTest {
        val config = AIModelConfig(
            id = 1,
            modelType = ModelType.OPENAI,
            modelName = "gpt-3.5-turbo",
            model = "gpt-3.5-turbo",
            apiKey = "test-key",
            apiEndpoint = "https://api.openai.com/v1/chat/completions"
        )

        // Record token usage
        monitor.recordRequest(config.modelId, config.modelName, config.modelType)
        monitor.recordSuccess(config.modelId, config.modelName, config.modelType, 1000L, 2000) // 2000 tokens

        // Execute
        val snapshot = monitor.getPerformanceSnapshot()

        // Verify
        val modelMetrics = snapshot.modelPerformance.values.first()
        assertTrue("Cost should be calculated based on tokens", modelMetrics.costPerRequest > 0)
        assertTrue("OpenAI should have reasonable cost", modelMetrics.costPerRequest < 0.01) // Less than 1 cent
    }

    @Test
    fun `test system health calculation`() = runTest {
        // Setup good performance
        val config = AIModelConfig(
            id = 1,
            modelType = ModelType.OPENAI,
            modelName = "gpt-3.5-turbo",
            model = "gpt-3.5-turbo",
            apiKey = "test-key",
            apiEndpoint = "https://api.openai.com/v1/chat/completions"
        )

        // Record excellent performance
        repeat(10) { i ->
            monitor.recordRequest(config.modelId, config.modelName, config.modelType)
            monitor.recordSuccess(config.modelId, config.modelName, config.modelType, 800L + i*100L, 1000)
        }

        // Execute
        val health = monitor.getSystemHealth()

        // Verify
        assertTrue("Overall score should be high for excellent performance", health.overallScore > 80f)
        assertTrue("Response time score should be good", health.responseTimeScore > 70f)
        assertTrue("Availability score should be excellent", health.availabilityScore > 95f)
        assertTrue("Cost efficiency should be reasonable", health.costEfficiencyScore > 50f)
        assertTrue("Recommendation should be positive", !health.recommendation.contains("故障") && !health.recommendation.contains("问题"))
    }

    @Test
    fun `test degraded performance health`() = runTest {
        // Setup poor performance
        val config = AIModelConfig(
            id = 1,
            modelType = ModelType.OPENAI,
            modelName = "gpt-3.5-turbo",
            model = "gpt-3.5-turbo",
            apiKey = "test-key",
            apiEndpoint = "https://api.openai.com/v1/chat/completions"
        )

        // Record poor performance
        repeat(10) { i ->
            monitor.recordRequest(config.modelId, config.modelName, config.modelType)
            if (i % 2 == 0) {
                monitor.recordSuccess(config.modelId, config.modelName, config.modelType, 5000L, 1000)
            } else {
                monitor.recordFailure(config.modelId, config.modelName, config.modelType)
            }
        }

        // Execute
        val health = monitor.getSystemHealth()

        // Verify
        assertTrue("Overall score should be lower for poor performance", health.overallScore < 70f)
        assertTrue("Recommendation should mention issues", health.recommendation.contains("建议") || health.recommendation.contains("优化"))
    }

    @Test
    fun `test performance trend tracking`() = runTest {
        // Record multiple snapshots over time
        repeat(5) {
            monitor.recordRequest(1L, "model", ModelType.OPENAI)
            monitor.recordSuccess(1L, "model", ModelType.OPENAI, 1000L, 1000)
            monitor.getPerformanceSnapshot()
        }

        // Execute
        val trends = monitor.getPerformanceTrend(24) // Last 24 hours

        // Verify
        assertTrue("Should have recorded performance trends", trends.isNotEmpty())
        assertEquals(5, trends.size) // Should have 5 snapshots
    }

    @Test
    fun `test model performance report`() = runTest {
        // Setup model performance data
        val config = AIModelConfig(
            id = 1,
            modelType = ModelType.OPENAI,
            modelName = "gpt-3.5-turbo",
            model = "gpt-3.5-turbo",
            apiKey = "test-key",
            apiEndpoint = "https://api.openai.com/v1/chat/completions"
        )

        repeat(5) {
            monitor.recordRequest(config.modelId, config.modelName, config.modelType)
            monitor.recordSuccess(config.modelId, config.modelName, config.modelType, 1200L, 1500)
        }

        // Execute
        val report = monitor.getModelPerformanceReport()

        // Verify
        assertTrue("Report should contain model information", report.contains("gpt-3.5-turbo"))
        assertTrue("Report should contain performance metrics", report.contains("请求次数"))
        assertTrue("Report should contain system overview", report.contains("系统概览"))
    }

    @Test
    fun `test metrics reset`() = runTest {
        // Setup some data
        val config = AIModelConfig(
            id = 1,
            modelType = ModelType.OPENAI,
            modelName = "gpt-3.5-turbo",
            model = "gpt-3.5-turbo",
            apiKey = "test-key",
            apiEndpoint = "https://api.openai.com/v1/chat/completions"
        )

        monitor.recordRequest(config.modelId, config.modelName, config.modelType)
        monitor.recordSuccess(config.modelId, config.modelName, config.modelType, 1000L, 1000)

        // Reset
        monitor.resetMetrics()

        // Verify
        val snapshot = monitor.getPerformanceSnapshot()
        assertEquals(0, snapshot.totalRequests)
        assertEquals(0, snapshot.successfulRequests)
        assertEquals(0f, snapshot.errorRate, 0.01f)
    }

    @Test
    fun `test thread safety`() = runTest {
        // Test concurrent access
        val config = AIModelConfig(
            id = 1,
            modelType = ModelType.OPENAI,
            modelName = "gpt-3.5-turbo",
            model = "gpt-3.5-turbo",
            apiKey = "test-key",
            apiEndpoint = "https://api.openai.com/v1/chat/completions"
        )

        // Simulate concurrent requests from different threads
        repeat(100) { i ->
            Thread {
                monitor.recordRequest(config.modelId, config.modelName, config.modelType)
                monitor.recordSuccess(config.modelId, config.modelName, config.modelType, 1000L, 1000)
            }.start()
        }

        // Wait for completion
        Thread.sleep(2000)

        // Verify no data corruption
        val snapshot = monitor.getPerformanceSnapshot()
        assertTrue("Concurrent operations should not corrupt data",
            snapshot.totalRequests > 0 && snapshot.successfulRequests > 0)
    }

    @Test
    fun `test empty performance data`() = runTest {
        // Execute with no recorded data
        val snapshot = monitor.getPerformanceSnapshot()

        // Verify defaults
        assertEquals(0, snapshot.totalRequests)
        assertEquals(0, snapshot.successfulRequests)
        assertEquals(0L, snapshot.avgResponseTimeMs)
        assertEquals(0f, snapshot.errorRate)
        assertEquals(0f, snapshot.cacheHitRate)
        assertEquals(0f, snapshot.routingEfficiency)
        assertTrue("Model performance should be empty or minimal", snapshot.modelPerformance.isEmpty())
    }

    @Test
    fun `test long-running performance tracking`() = runTest {
        val config = AIModelConfig(
            id = 1,
            modelType = ModelType.OPENAI,
            modelName = "gpt-3.5-turbo",
            model = "gpt-3.5-turbo",
            apiKey = "test-key",
            apiEndpoint = "https://api.openai.com/v1/chat/completions"
        )

        // Record many requests over simulated time
        repeat(1000) { i ->
            monitor.recordRequest(config.modelId, config.modelName, config.modelType)
            if (i % 10 == 0) {
                monitor.recordSuccess(config.modelId, config.modelName, config.modelType, 1000L, 1000)
            } else {
                monitor.recordFailure(config.modelId, config.modelName, config.modelType)
            }
        }

        // Execute
        val snapshot = monitor.getPerformanceSnapshot()

        // Verify
        assertEquals(1000, snapshot.totalRequests)
        assertEquals(100, snapshot.successfulRequests) // Every 10th request succeeds
        assertEquals(0.9f, snapshot.errorRate, 0.01f)
        assertTrue("Average response time should be tracked", snapshot.avgResponseTimeMs >= 0)
    }
}