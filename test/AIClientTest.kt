package com.csbaby.kefu.data.remote

import com.csbaby.kefu.domain.model.AIModelConfig
import com.csbaby.kefu.domain.model.ModelType
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import org.junit.Assert.*
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import retrofit2.Response
import java.util.concurrent.ConcurrentHashMap

/**
 * Comprehensive tests for AIClient implementation.
 */
class AIClientTest {

    private val mockOkHttpClient = mock<okhttp3.OkHttpClient>()
    private val client = AIClientImpl(mockOkHttpClient)

    @Test
    fun `test adaptive rate limit adjustment`() = runTest {
        // Setup model config
        val config = AIModelConfig(
            id = 1,
            modelType = ModelType.OPENAI,
            modelName = "gpt-3.5-turbo",
            model = "gpt-3.5-turbo",
            apiKey = "test-key",
            apiEndpoint = "https://api.openai.com/v1/chat/completions"
        )

        // Simulate successful requests
        repeat(10) { _ ->
            client.recordRequest(config)
            client.recordSuccess(config, 1000L, 500)
        }

        // Rate limit should be increased due to high success rate
        val rateLimit = client.getRateLimit(config.apiKey)
        assertTrue("Rate limit should be increased for good performers", rateLimit.currentLimit > 60)
    }

    @Test
    fun `test adaptive timeout calculation`() = runTest {
        val openaiConfig = AIModelConfig(
            id = 1,
            modelType = ModelType.OPENAI,
            modelName = "gpt-4",
            model = "gpt-4",
            apiKey = "test-key",
            apiEndpoint = "https://api.openai.com/v1/chat/completions"
        )

        val claudeConfig = AIModelConfig(
            id = 2,
            modelType = ModelType.CLAUDE,
            modelName = "claude-3-opus",
            model = "claude-3-opus-20240307",
            apiKey = "test-key",
            apiEndpoint = "https://api.anthropic.com/v1/messages"
        )

        // Claude typically needs longer timeouts
        val claudeTimeout = client.calculateAdaptiveReadTimeout(claudeConfig)
        val openaiTimeout = client.calculateAdaptiveReadTimeout(openaiConfig)

        assertTrue("Claude should have longer read timeout", claudeTimeout >= openaiTimeout)
    }

    @Test
    fun `test request validation`() = runTest {
        val config = AIModelConfig(
            id = 1,
            modelType = ModelType.OPENAI,
            modelName = "gpt-3.5-turbo",
            model = "",
            apiKey = "",
            apiEndpoint = ""
        )

        val messages = listOf(AIClient.Message("user", "Hello"))

        // Test empty API key
        var result = client.validateRequest(config, messages, 1000)
        assertFalse("Empty API key should be invalid", result.isSuccess)

        // Test empty endpoint
        val validConfig = config.copy(apiKey = "test-key")
        result = client.validateRequest(validConfig, messages, 1000)
        assertFalse("Empty endpoint should be invalid", result.isSuccess)

        // Test empty model name
        val validConfig2 = validConfig.copy(apiEndpoint = "https://api.openai.com/v1/chat/completions")
        result = client.validateRequest(validConfig2, messages, 1000)
        assertFalse("Empty model name should be invalid", result.isSuccess)

        // Test valid request
        val validConfig3 = validConfig2.copy(model = "gpt-3.5-turbo")
        result = client.validateRequest(validConfig3, messages, 1000)
        assertTrue("Valid config should pass", result.isSuccess)
    }

    @Test
    fun `test error message classification`() = runTest {
        // Test network errors
        assertEquals(
            "无法连接到 API 服务器，请检查网络连接和 API 地址",
            client.friendlyErrorMessage("UnknownHostException")
        )

        // Test auth errors
        assertEquals(
            "API 密钥无效，请检查密钥是否正确",
            client.friendlyErrorMessage("Incorrect API key provided")
        )

        // Test rate limit errors
        assertEquals(
            "API 请求频率超限，请稍后再试",
            client.friendlyErrorMessage("Too Many Requests")
        )

        // Test server errors
        assertEquals(
            "API 服务器内部错误，请稍后再试",
            client.friendlyErrorMessage("Internal Server Error")
        )
    }

    @Test
    fun `test model-specific headers`() = runTest {
        val openaiConfig = AIModelConfig(
            id = 1,
            modelType = ModelType.OPENAI,
            modelName = "gpt-3.5-turbo",
            model = "gpt-3.5-turbo",
            apiKey = "test-key",
            apiEndpoint = "https://api.openai.com/v1/chat/completions"
        )

        val claudeConfig = AIModelConfig(
            id = 2,
            modelType = ModelType.CLAUDE,
            modelName = "claude-3-haiku",
            model = "claude-3-haiku-20240307",
            apiKey = "test-key",
            apiEndpoint = "https://api.anthropic.com/v1/messages"
        )

        val openaiHeaders = client.getHeadersForModel(openaiConfig)
        assertEquals("Bearer test-key", openaiHeaders["Authorization"])

        val claudeHeaders = client.getHeadersForModel(claudeConfig)
        assertEquals("test-key", claudeHeaders["x-api-key"])
        assertEquals("2023-06-01", claudeHeaders["anthropic-version"])
    }

    @Test
    fun `test NVIDIA API support`() = runTest {
        val nvidiaConfig = AIModelConfig(
            id = 1,
            modelType = ModelType.CUSTOM,
            modelName = "nvidia-model",
            model = "nemotron-4-340b-instruct",
            apiKey = "nv-api-key",
            apiEndpoint = "https://integrate.api.nvidia.com/v1/chat/completions"
        )

        val headers = client.getHeadersForModel(nvidiaConfig)
        assertEquals("Bearer nv-api-key", headers["Authorization"])
    }

    @Test
    fun `test request ID generation`() = runTest {
        val requestId1 = client.generateRequestId()
        val requestId2 = client.generateRequestId()

        assertTrue("Request ID should contain timestamp", requestId1.contains("req_"))
        assertTrue("Request IDs should be different", requestId1 != requestId2)
    }

    @Test
    fun `test adaptive connect timeout`() = runTest {
        val chinaConfig = AIModelConfig(
            id = 1,
            modelType = ModelType.ZHIPU,
            modelName = "glm-4",
            model = "glm-4",
            apiKey = "test-key",
            apiEndpoint = "https://open.bigmodel.cn/api/paas/v4/chat/completions"
        )

        val awsConfig = AIModelConfig(
            id = 2,
            modelType = ModelType.OPENAI,
            modelName = "gpt-3.5-turbo",
            model = "gpt-3.5-turbo",
            apiKey = "test-key",
            apiEndpoint = "https://api.openai.com/v1/chat/completions"
        )

        val chinaTimeout = client.calculateAdaptiveConnectTimeout(chinaConfig)
        val awsTimeout = client.calculateAdaptiveConnectTimeout(awsConfig)

        assertTrue("China APIs may need longer connection timeouts", chinaTimeout >= awsTimeout)
    }

    @Test
    fun `test token usage tracking`() = runTest {
        val config = AIModelConfig(
            id = 1,
            modelType = ModelType.OPENAI,
            modelName = "gpt-3.5-turbo",
            model = "gpt-3.5-turbo",
            apiKey = "test-key",
            apiEndpoint = "https://api.openai.com/v1/chat/completions"
        )

        client.recordRequest(config)
        client.recordSuccess(config, 1200L, 800)

        val metrics = client.getMetrics(config.apiKey)
        assertNotNull("Metrics should be recorded", metrics)
        assertEquals(1, metrics!!.requestCount)
        assertEquals(1, metrics.successCount)
    }

    @Test
    fun `test cost estimation`() = runTest {
        val gptConfig = AIModelConfig(
            id = 1,
            modelType = ModelType.OPENAI,
            modelName = "gpt-3.5-turbo",
            model = "gpt-3.5-turbo",
            apiKey = "test-key",
            apiEndpoint = "https://api.openai.com/v1/chat/completions"
        )

        val claudeConfig = AIModelConfig(
            id = 2,
            modelType = ModelType.CLAUDE,
            modelName = "claude-3-haiku",
            model = "claude-3-haiku-20240307",
            apiKey = "test-key",
            apiEndpoint = "https://api.anthropic.com/v1/messages"
        )

        val gptCostPerToken = client.estimateCostPerToken(gptConfig, 1000)
        val claudeCostPerToken = client.estimateCostPerToken(claudeConfig, 1000)

        // Claude should be cheaper than GPT-3.5
        assertTrue("Claude should be cheaper per token", claudeCostPerToken < gptCostPerToken)
    }

    @Test
    fun `test model name resolution`() = runTest {
        val configWithoutModelField = AIModelConfig(
            id = 1,
            modelType = ModelType.OPENAI,
            modelName = "gpt-3.5-turbo",
            model = "", // Empty model field
            apiKey = "test-key",
            apiEndpoint = "https://api.openai.com/v1/chat/completions"
        )

        val resolvedName = client.getModelName(configWithoutModelField)
        assertEquals("gpt-3.5-turbo", resolvedName) // Should use default

        val configWithModelField = AIModelConfig(
            id = 2,
            modelType = ModelType.CLAUDE,
            modelName = "claude-3-haiku",
            model = "claude-3-haiku-20240307", // Specific model
            apiKey = "test-key",
            apiEndpoint = "https://api.anthropic.com/v1/messages"
        )

        val resolvedName2 = client.getModelName(configWithModelField)
        assertEquals("claude-3-haiku-20240307", resolvedName2) // Should use specific model
    }
}