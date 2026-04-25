package com.csbaby.kefu.fakes.ai

import com.csbaby.kefu.data.remote.AIClient
import com.csbaby.kefu.domain.model.AIModelConfig

/**
 * Fake AIClient for unit testing.
 * Configurable responses and error injection.
 */
class FakeAIClient : AIClient {

    var generateResult: Result<String> = Result.success("您好，请问有什么可以帮您？")
    var testConnectionResult: Result<Boolean> = Result.success(true)
    var rawRequestResult: Result<String> = Result.success("{}")

    var generateCallCount = 0
    var testConnectionCallCount = 0
    var rawRequestCallCount = 0

    var lastGenerateConfig: AIModelConfig? = null
    var lastGenerateMessages: List<AIClient.Message>? = null
    var lastGenerateTemperature: Float? = null
    var lastGenerateMaxTokens: Int? = null

    var lastTestConnectionConfig: AIModelConfig? = null
    var lastRawRequestEndpoint: String? = null
    var lastRawRequestBody: String? = null

    var shouldRateLimit = false
    var rateLimitMessage = "Rate limit exceeded. Please try again later."

    fun reset() {
        generateResult = Result.success("您好，请问有什么可以帮您？")
        testConnectionResult = Result.success(true)
        rawRequestResult = Result.success("{}")
        generateCallCount = 0
        testConnectionCallCount = 0
        rawRequestCallCount = 0
        lastGenerateConfig = null
        lastGenerateMessages = null
        lastGenerateTemperature = null
        lastGenerateMaxTokens = null
        lastTestConnectionConfig = null
        lastRawRequestEndpoint = null
        lastRawRequestBody = null
        shouldRateLimit = false
    }

    override suspend fun generateCompletion(
        config: AIModelConfig,
        messages: List<AIClient.Message>,
        temperature: Float,
        maxTokens: Int
    ): Result<String> {
        generateCallCount++
        lastGenerateConfig = config
        lastGenerateMessages = messages
        lastGenerateTemperature = temperature
        lastGenerateMaxTokens = maxTokens

        if (shouldRateLimit) {
            return Result.failure(Exception(rateLimitMessage))
        }
        return generateResult
    }

    override suspend fun testConnection(config: AIModelConfig): Result<Boolean> {
        testConnectionCallCount++
        lastTestConnectionConfig = config

        if (shouldRateLimit) {
            return Result.failure(Exception(rateLimitMessage))
        }
        return testConnectionResult
    }

    override suspend fun makeRawRequest(
        config: AIModelConfig,
        endpoint: String,
        requestBody: String
    ): Result<String> {
        rawRequestCallCount++
        lastRawRequestEndpoint = endpoint
        lastRawRequestBody = requestBody

        if (shouldRateLimit) {
            return Result.failure(Exception(rateLimitMessage))
        }
        return rawRequestResult
    }
}
