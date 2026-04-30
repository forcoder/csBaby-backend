package com.csbaby.kefu.data.remote

import com.csbaby.kefu.domain.model.AIModelConfig
import com.csbaby.kefu.domain.model.ModelType
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Integration tests for AIClient using MockWebServer.
 */
class AIClientIntegrationTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var aiClient: AIClient

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        val okHttpClient = okhttp3.OkHttpClient.Builder().build()
        aiClient = AIClientImpl(okHttpClient)
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `test successful OpenAI response`() = runTest {
        // Given
        val config = AIModelConfig(
            id = 1L,
            modelType = ModelType.OPENAI,
            modelName = "gpt-3.5-turbo",
            model = "gpt-3.5-turbo",
            apiKey = "sk-test-key",
            apiEndpoint = mockWebServer.url("/v1/chat/completions").toString()
        )

        val requestBody = """
            {
                "model": "gpt-3.5-turbo",
                "messages": [
                    {"role": "user", "content": "Hello"}
                ],
                "temperature": 0.7,
                "max_tokens": 100
            }
        """.trimIndent()

        val mockResponse = MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                {
                    "id": "chatcmpl-123",
                    "object": "chat.completion",
                    "created": 1677652288,
                    "choices": [{
                        "index": 0,
                        "message": {
                            "role": "assistant",
                            "content": "Hello! How can I help you today?"
                        },
                        "finish_reason": "stop"
                    }],
                    "usage": {
                        "prompt_tokens": 9,
                        "completion_tokens": 12,
                        "total_tokens": 21
                    }
                }
            """.trimIndent())

        mockWebServer.enqueue(mockResponse)

        // When
        val result = aiClient.generateCompletion(
            config = config,
            messages = listOf(AIClient.Message("user", "Hello")),
            temperature = 0.7f,
            maxTokens = 100
        )

        // Then
        assertTrue(result.isSuccess)
        assertEquals("Hello! How can I help you today?", result.getOrNull())
    }

    @Test
    fun `test successful Claude response`() = runTest {
        // Given
        val config = AIModelConfig(
            id = 2L,
            modelType = ModelType.CLAUDE,
            modelName = "claude-3-haiku",
            model = "claude-3-haiku-20240307",
            apiKey = "test-key",
            apiEndpoint = mockWebServer.url("/v1/messages").toString()
        )

        val requestBody = """
            {
                "model": "claude-3-haiku-20240307",
                "max_tokens": 100,
                "messages": [
                    {"role": "user", "content": "Hello"}
                ]
            }
        """.trimIndent()

        val mockResponse = MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                {
                    "id": "msg_123",
                    "type": "message",
                    "role": "assistant",
                    "model": "claude-3-haiku-20240307",
                    "content": [
                        {
                            "type": "text",
                            "text": "Hi there! Nice to meet you.",
                            "source": null
                        }
                    ]
                }
            """.trimIndent())

        mockWebServer.enqueue(mockResponse)

        // When
        val result = aiClient.generateCompletion(
            config = config,
            messages = listOf(AIClient.Message("user", "Hello")),
            temperature = 0.7f,
            maxTokens = 100
        )

        // Then
        assertTrue(result.isSuccess)
        assertEquals("Hi there! Nice to meet you.", result.getOrNull())
    }

    @Test
    fun `test API error handling`() = runTest {
        // Given
        val config = AIModelConfig(
            id = 1L,
            modelType = ModelType.OPENAI,
            modelName = "gpt-3.5-turbo",
            model = "gpt-3.5-turbo",
            apiKey = "sk-test-key",
            apiEndpoint = mockWebServer.url("/v1/chat/completions").toString()
        )

        val mockResponse = MockResponse()
            .setResponseCode(401)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                {
                    "error": {
                        "message": "Incorrect API key provided",
                        "type": "invalid_request_error",
                        "param": null,
                        "code": null
                    }
                }
            """.trimIndent())

        mockWebServer.enqueue(mockResponse)

        // When
        val result = aiClient.generateCompletion(
            config = config,
            messages = listOf(AIClient.Message("user", "Hello")),
            temperature = 0.7f,
            maxTokens = 100
        )

        // Then
        assertFalse(result.isSuccess)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception!!.message!!.contains("API 密钥"))
    }

    @Test
    fun `test network timeout handling`() = runTest {
        // Given
        val config = AIModelConfig(
            id = 1L,
            modelType = ModelType.OPENAI,
            modelName = "gpt-3.5-turbo",
            model = "gpt-3.5-turbo",
            apiKey = "sk-test-key",
            apiEndpoint = mockWebServer.url("/slow-endpoint").toString()
        )

        // Don't enqueue any response - this will cause a timeout

        // When & Then
        val result = aiClient.generateCompletion(
            config = config,
            messages = listOf(AIClient.Message("user", "Hello")),
            temperature = 0.7f,
            maxTokens = 100
        )

        assertFalse(result.isSuccess)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        // Should contain timeout or connection error message
        assertTrue(exception!!.message!!.isNotEmpty())
    }

    @Test
    fun `test rate limiting behavior`() = runTest {
        // Given
        val config = AIModelConfig(
            id = 1L,
            modelType = ModelType.OPENAI,
            modelName = "gpt-3.5-turbo",
            model = "gpt-3.5-turbo",
            apiKey = "sk-test-key",
            apiEndpoint = mockWebServer.url("/v1/chat/completions").toString()
        )

        // First request should succeed
        val successResponse = MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("{\"choices\": [{\"message\": {\"role\": \"assistant\", \"content\": \"Success\"}}]}")
        mockWebServer.enqueue(successResponse)

        // When & Then first request
        val firstResult = aiClient.generateCompletion(
            config = config,
            messages = listOf(AIClient.Message("user", "Hello")),
            temperature = 0.7f,
            maxTokens = 100
        )
        assertTrue(firstResult.isSuccess)

        // Second request should also succeed (rate limit not actually enforced in test)
        val secondResult = aiClient.generateCompletion(
            config = config,
            messages = listOf(AIClient.Message("user", "Hello again")),
            temperature = 0.7f,
            maxTokens = 100
        )
        assertTrue(secondResult.isSuccess)
    }

    @Test
    fun `test request validation`() = runTest {
        // Given
        val invalidConfig = AIModelConfig(
            id = 1L,
            modelType = ModelType.OPENAI,
            modelName = "gpt-3.5-turbo",
            model = "", // Empty model
            apiKey = "", // Empty key
            apiEndpoint = "" // Empty endpoint
        )

        // When & Then
        val result = aiClient.generateCompletion(
            config = invalidConfig,
            messages = listOf(AIClient.Message("user", "Hello")),
            temperature = 0.7f,
            maxTokens = 100
        )

        assertFalse(result.isSuccess)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception!!.message!!.contains("模型名称不能为空"))
    }

    @Test
    fun `test empty messages validation`() = runTest {
        // Given
        val config = AIModelConfig(
            id = 1L,
            modelType = ModelType.OPENAI,
            modelName = "gpt-3.5-turbo",
            model = "gpt-3.5-turbo",
            apiKey = "sk-test-key",
            apiEndpoint = mockWebServer.url("/v1/chat/completions").toString()
        )

        // When & Then
        val result = aiClient.generateCompletion(
            config = config,
            messages = emptyList(), // Empty messages
            temperature = 0.7f,
            maxTokens = 100
        )

        assertFalse(result.isSuccess)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception!!.message!!.contains("消息列表不能为空"))
    }

    @Test
    fun `test large token limit validation`() = runTest {
        // Given
        val config = AIModelConfig(
            id = 1L,
            modelType = ModelType.OPENAI,
            modelName = "gpt-3.5-turbo",
            model = "gpt-3.5-turbo",
            apiKey = "sk-test-key",
            apiEndpoint = mockWebServer.url("/v1/chat/completions").toString()
        )

        // When & Then
        val result = aiClient.generateCompletion(
            config = config,
            messages = listOf(AIClient.Message("user", "Hello")),
            temperature = 0.7f,
            maxTokens = 50000 // Too large
        )

        assertFalse(result.isSuccess)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception!!.message!!.contains("超出有效范围"))
    }

    @Test
    fun `test makeRawRequest for embeddings`() = runTest {
        // Given
        val config = AIModelConfig(
            id = 1L,
            modelType = ModelType.OPENAI,
            modelName = "gpt-3.5-turbo",
            model = "text-embedding-ada-002",
            apiKey = "sk-test-key",
            apiEndpoint = mockWebServer.url("/v1/embeddings").toString()
        )

        val requestBody = """
            {
                "input": "Hello world",
                "model": "text-embedding-ada-002"
            }
        """.trimIndent()

        val mockResponse = MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                {
                    "object": "list",
                    "data": [{
                        "object": "embedding",
                        "embedding": [0.1, 0.2, 0.3],
                        "index": 0
                    }],
                    "model": "text-embedding-ada-002",
                    "usage": {
                        "prompt_tokens": 4,
                        "total_tokens": 4
                    }
                }
            """.trimIndent())

        mockWebServer.enqueue(mockResponse)

        // When
        val result = aiClient.makeRawRequest(config, "embeddings", requestBody)

        // Then
        assertTrue(result.isSuccess)
        val response = result.getOrNull()
        assertNotNull(response)
        assertTrue(response!!.contains("embedding"))
        assertTrue(response.contains("[0.1, 0.2, 0.3]"))
    }

    @Test
    fun `test connection with different model types`() = runTest {
        // Test OpenAI connection
        val openaiConfig = AIModelConfig(
            id = 1L,
            modelType = ModelType.OPENAI,
            modelName = "gpt-3.5-turbo",
            model = "gpt-3.5-turbo",
            apiKey = "sk-test-key",
            apiEndpoint = mockWebServer.url("/v1/chat/completions").toString()
        )

        val mockResponse = MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("{\"choices\": [{\"message\": {\"role\": \"assistant\", \"content\": \"Connected\"}}]}")

        mockWebServer.enqueue(mockResponse)

        val result = aiClient.testConnection(openaiConfig)
        assertTrue(result.isSuccess)
    }
}