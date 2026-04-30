package com.csbaby.kefu.data.remote

import com.csbaby.kefu.domain.model.AIModelConfig
import com.csbaby.kefu.domain.model.ModelType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

interface AIClient {
    suspend fun generateCompletion(
        config: AIModelConfig,
        messages: List<Message>,
        temperature: Float,
        maxTokens: Int
    ): Result<String>

    suspend fun testConnection(config: AIModelConfig): Result<Boolean>

    /**
     * Make a raw API request to a specific endpoint.
     * Used for embeddings and other API calls.
     * 
     * @param config The AI model configuration
     * @param endpoint The API endpoint path (e.g., "embeddings")
     * @param requestBody The JSON request body
     * @return The raw JSON response string
     */
    suspend fun makeRawRequest(
        config: AIModelConfig,
        endpoint: String,
        requestBody: String
    ): Result<String>

    data class Message(
        val role: String, // "system", "user", "assistant"
        val content: String
    )
}

@Singleton
class AIClientImpl @Inject constructor(
    private val okHttpClient: OkHttpClient
) : AIClient {
    
    // Enhanced request throttling with adaptive limits
    private val requestCounters = ConcurrentHashMap<String, AdaptiveRateLimit>()
    private val BASE_REQUEST_LIMITS = mapOf(
        ModelType.CLAUDE to 50,     // Claude has lower rate limits
        ModelType.OPENAI to 100,    // OpenAI GPT models
        ModelType.ZHIPU to 40,      // Zhipu AI
        ModelType.TONGYI to 40,     // Tongyi Qianwen
        ModelType.CUSTOM to 80      // Custom models (user-configurable)
    )

    // Adaptive timeout settings
    private val BASE_READ_TIMEOUT_SECONDS = 30L
    private val BASE_CONNECT_TIMEOUT_SECONDS = 10L
    private val NETWORK_SPEED_THRESHOLD_MS = 1000L // Below this is considered fast network

    data class AdaptiveRateLimit(
        var currentLimit: Int,
        var lastAdjustmentTime: Long = System.currentTimeMillis(),
        var failureCount: Int = 0,
        var successCount: Int = 0
    )
    
    /**
     * Enhanced rate limiting with adaptive controls and model-specific limits.
     */
    private fun isRequestAllowed(config: AIModelConfig): Boolean {
        val now = System.currentTimeMillis()
        val rateLimit = requestCounters.getOrPut(config.apiKey) {
            AdaptiveRateLimit(BASE_REQUEST_LIMITS[config.modelType] ?: 60)
        }

        // Update rate limit based on recent performance
        updateAdaptiveRateLimit(rateLimit, config)

        // Clean old timestamps
        val windowStart = now - 60000L // 1 minute window
        rateLimit.timestamps.removeAll { it < windowStart }

        if (rateLimit.timestamps.size >= rateLimit.currentLimit) {
            Timber.w("Rate limit exceeded for ${config.modelName} (${config.modelType})")

            // Implement exponential backoff if consistently hitting limits
            if (rateLimit.failureCount > 5) {
                rateLimit.currentLimit = (rateLimit.currentLimit * 0.8).toInt().coerceAtLeast(10)
                Timber.d("Reduced rate limit for ${config.modelName} due to failures")
            }

            return false
        }

        rateLimit.timestamps.add(now)
        return true
    }

    private fun updateAdaptiveRateLimit(rateLimit: AdaptiveRateLimit, config: AIModelConfig) {
        val now = System.currentTimeMillis()
        val timeSinceLastAdjustment = now - rateLimit.lastAdjustmentTime

        // Adjust every 5 minutes or after significant changes
        if (timeSinceLastAdjustment > 300000 || Math.abs(rateLimit.successCount - rateLimit.failureCount) > 10) {
            val successRate = if (rateLimit.successCount + rateLimit.failureCount > 0) {
                rateLimit.successCount.toFloat() / (rateLimit.successCount + rateLimit.failureCount)
            } else 0f

            when {
                successRate < 0.7f -> {
                    // Reduce limit for poor performers
                    rateLimit.currentLimit = (rateLimit.currentLimit * 0.9).toInt().coerceAtLeast(20)
                }
                successRate > 0.95f && rateLimit.currentLimit < 100 -> {
                    // Increase limit for excellent performers
                    rateLimit.currentLimit = (rateLimit.currentLimit * 1.1).toInt().coerceAtMost(150)
                }
            }

            rateLimit.lastAdjustmentTime = now
            rateLimit.successCount = 0
            rateLimit.failureCount = 0
        }
    }
    
    /**
     * Get a client with adaptive timeouts based on network conditions.
     */
    private fun getConfiguredClient(config: AIModelConfig): OkHttpClient {
        val readTimeout = calculateAdaptiveReadTimeout(config)
        val connectTimeout = calculateAdaptiveConnectTimeout(config)

        return okHttpClient.newBuilder()
            .readTimeout(readTimeout, TimeUnit.SECONDS)
            .connectTimeout(connectTimeout, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true) // Enable automatic retry for connection issues
            .addInterceptor { chain ->
                val request = chain.request()
                val startTime = System.nanoTime()

                try {
                    val response = chain.proceed(request)

                    // Log response metrics
                    val duration = System.nanoTime() - startTime
                    Timber.d("API Request to ${config.modelName}: ${duration / 1_000_000}ms, Status: ${response.code}")

                    // Add performance headers
                    if (response.code == 429) { // Rate limited
                        Timber.w("Rate limited by ${config.modelName}")
                    }

                    response
                } catch (e: Exception) {
                    val duration = System.nanoTime() - startTime
                    Timber.e(e, "API Request failed after ${duration / 1_000_000}ms to ${config.modelName}")
                    throw e
                }
            }
            .build()
    }

    private fun calculateAdaptiveReadTimeout(config: AIModelConfig): Long {
        return when {
            config.modelType == ModelType.CLAUDE -> BASE_READ_TIMEOUT_SECONDS * 2 // Claude can be slower
            config.maxTokens > 16000 -> BASE_READ_TIMEOUT_SECONDS + 15 // Longer for large tokens
            config.apiEndpoint.contains("nvidia.com") -> BASE_READ_TIMEOUT_SECONDS + 10 // NVIDIA models
            else -> BASE_READ_TIMEOUT_SECONDS
        }.coerceAtMost(120L) // Cap at 2 minutes
    }

    private fun calculateAdaptiveConnectTimeout(config: AIModelConfig): Long {
        return when {
            config.apiEndpoint.contains("china", ignoreCase = true) -> BASE_CONNECT_TIMEOUT_SECONDS + 5 // Chinese APIs might be slower
            config.apiEndpoint.contains("aws", ignoreCase = true) -> BASE_CONNECT_TIMEOUT_SECONDS + 3 // AWS endpoints
            else -> BASE_CONNECT_TIMEOUT_SECONDS
        }.coerceAtLeast(5L) // Minimum 5 seconds
    }

    override suspend fun generateCompletion(
        config: AIModelConfig,
        messages: List<AIClient.Message>,
        temperature: Float,
        maxTokens: Int
    ): Result<String> {
        // Enhanced validation and preparation
        val validationResult = validateRequest(config, messages, maxTokens)
        if (!validationResult.isSuccess) {
            return validationResult
        }

        // Check rate limit with model-specific configuration
        if (!isRequestAllowed(config)) {
            return Result.failure(Exception("请求频率超限，请稍后再试"))
        }

        val startTime = System.currentTimeMillis()

        return try {
            val endpoint = config.apiEndpoint
            val (requestBody, headers) = buildRequest(config, messages, temperature, maxTokens)

            val requestBuilder = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")

            // Add model-specific headers
            headers.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }

            // Add performance tracking header
            requestBuilder.addHeader("X-Request-ID", generateRequestId())

            val request = requestBuilder.build()
            Timber.d("AI Request to ${config.modelName} (${config.modelType}): ${endpoint.take(100)}")
            Timber.v("Request body preview: ${requestBody.toString().take(200)}")

            val response = withContext(Dispatchers.IO) {
                val client = getConfiguredClient(config)
                client.newCall(request).execute()
            }

            val responseBody = response.body?.string() ?: return Result.failure(Exception("服务器返回空响应"))
            val duration = System.currentTimeMillis() - startTime

            Timber.d("AI Response from ${config.modelName}: ${response.code}, Duration: ${duration}ms")

            if (response.isSuccessful) {
                val reply = parseResponse(responseBody, config.modelType)
                Timber.d("AI Parsed Reply from ${config.modelName}: ${reply.take(100)}")

                // Update success metrics
                updateRequestMetrics(config, true, duration)

                Result.success(reply)
            } else {
                val errorMsg = "API Error ${response.code}: $responseBody"
                Timber.e(errorMsg)

                // Update failure metrics
                updateRequestMetrics(config, false, duration)

                // Handle specific HTTP errors
                val friendlyError = handleApiError(response.code, responseBody, config.modelType)
                Result.failure(Exception(friendlyError))
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            Timber.e(e, "AI request failed for ${config.modelName}: ${duration}ms")

            // Update failure metrics
            updateRequestMetrics(config, false, duration)

            val userFriendlyError = friendlyErrorMessage(e.message ?: "未知错误")
            Result.failure(Exception(userFriendlyError, e))
        }
    }

    private fun validateRequest(
        config: AIModelConfig,
        messages: List<Message>,
        maxTokens: Int
    ): Result<Unit> {
        // Validate API key
        if (config.apiKey.isBlank()) {
            return Result.failure(Exception("API密钥不能为空"))
        }

        // Validate endpoint
        if (config.apiEndpoint.isBlank()) {
            return Result.failure(Exception("API地址不能为空"))
        }

        // Validate model name
        if (config.model.isBlank()) {
            return Result.failure(Exception("模型名称不能为空"))
        }

        // Validate messages
        if (messages.isEmpty()) {
            return Result.failure(Exception("消息列表不能为空"))
        }

        val userMessages = messages.filter { it.role == "user" || it.role == "assistant" }
        if (userMessages.isEmpty()) {
            return Result.failure(Exception("至少需要一个用户或助手消息"))
        }

        // Validate token limits
        if (maxTokens <= 0 || maxTokens > 32768) {
            return Result.failure(Exception("token数量超出有效范围 (1-32768)"))
        }

        // Validate message content
        messages.forEach { message ->
            if (message.content.trim().isEmpty()) {
                return Result.failure(Exception("消息内容不能为空"))
            }
            if (message.content.length > 50000) {
                return Result.failure(Exception("单条消息过长，请简化内容"))
            }
        }

        return Result.success(Unit)
    }

    private fun handleApiError(
        statusCode: Int,
        responseBody: String,
        modelType: ModelType
    ): String {
        return when (statusCode) {
            400 -> "请求格式错误，请检查参数设置"
            401 -> "API密钥无效，请检查密钥是否正确"
            403 -> "API访问被拒绝，请检查密钥权限"
            404 -> "API地址或模型不存在，请检查配置"
            429 -> "请求频率超限，请稍后再试"
            500 -> "API服务器内部错误，请稍后重试"
            502 -> "API网关错误，请稍后重试"
            503 -> "API服务暂时不可用，请稍后重试"
            504 -> "API请求超时，请稍后重试"
            else -> "API调用失败 ($statusCode): ${responseBody.take(100)}"
        }
    }

    private fun updateRequestMetrics(config: AIModelConfig, success: Boolean, duration: Long) {
        val now = System.currentTimeMillis()
        val key = "${config.id}_${config.modelType.name}"

        requestCounters.getOrPut(key) { AdaptiveRateLimit(BASE_REQUEST_LIMITS[config.modelType] ?: 60) }.let { rateLimit ->
            synchronized(rateLimit) {
                if (success) {
                    rateLimit.successCount++
                } else {
                    rateLimit.failureCount++
                }

                // Clean old timestamps
                val windowStart = now - 60000L
                rateLimit.timestamps.removeAll { it < windowStart }
            }
        }
    }

    private fun generateRequestId(): String {
        return "req_${System.currentTimeMillis()}_${(Math.random() * 1000).toInt()}"
    }

    override suspend fun testConnection(config: AIModelConfig): Result<Boolean> {
        // Validate config first
        if (config.apiKey.isBlank()) {
            return Result.failure(Exception("API 密钥不能为空"))
        }

        if (config.apiEndpoint.isBlank()) {
            return Result.failure(Exception("API 地址不能为空"))
        }

        // model 字段是实际模型ID（如 gpt-4），是发请求必需的
        if (config.model.isBlank()) {
            return Result.failure(Exception("模型名称不能为空，请填写如 gpt-4、claude-3-opus 等"))
        }

        // Check rate limit
        if (!isRequestAllowed(config.apiKey)) {
            return Result.failure(Exception("请求频率超限，请稍后再试"))
        }

        return try {
            Timber.d("Testing connection with config: model=${config.model}, endpoint=${config.apiEndpoint}, modelType=${config.modelType}")
            val testMessages = listOf(
                AIClient.Message("user", "你好")
            )
            val result = generateCompletion(config, testMessages, 0.7f, 50)
            if (result.isSuccess) {
                val content = result.getOrDefault("")
                if (content.isNotEmpty()) {
                    Timber.d("Test connection successful, received: ${content.take(50)}")
                    Result.success(true)
                } else {
                    val errorMsg = "连接成功但模型返回空响应，请检查模型名称是否正确"
                    Timber.e(errorMsg)
                    Result.failure(Exception(errorMsg))
                }
            } else {
                // generateCompletion 内部已经捕获了网络异常，但 API 返回的错误码在这里处理
                val error = result.exceptionOrNull()?.message ?: "未知错误"
                val friendlyError = friendlyErrorMessage(error)
                Timber.e("Test connection failed: $error -> $friendlyError")
                Result.failure(Exception(friendlyError))
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: "未知错误"
            val friendlyError = friendlyErrorMessage(errorMsg)
            Timber.e(e, "Test connection exception: $errorMsg -> $friendlyError")
            Result.failure(Exception(friendlyError))
        }
    }

    /**
     * 将技术性错误信息转为用户友好的提示
     */
    private fun friendlyErrorMessage(rawMessage: String): String {
        return when {
            rawMessage.contains("UnknownHost", ignoreCase = true) ||
            rawMessage.contains("无法解析", ignoreCase = true) ->
                "无法连接到 API 服务器，请检查网络连接和 API 地址"

            rawMessage.contains("ConnectException", ignoreCase = true) ||
            rawMessage.contains("Connection refused", ignoreCase = true) ->
                "连接被拒绝，请检查 API 地址是否正确"

            rawMessage.contains("SocketTimeout", ignoreCase = true) ||
            rawMessage.contains("timeout", ignoreCase = true) ->
                "连接超时，请检查网络或 API 服务器是否可达"

            rawMessage.contains("401", ignoreCase = true) ||
            rawMessage.contains("Unauthorized", ignoreCase = true) ||
            rawMessage.contains("Incorrect API key", ignoreCase = true) ->
                "API 密钥无效，请检查密钥是否正确"

            rawMessage.contains("403", ignoreCase = true) ||
            rawMessage.contains("Forbidden", ignoreCase = true) ->
                "API 访问被拒绝，请检查密钥权限"

            rawMessage.contains("404", ignoreCase = true) ||
            rawMessage.contains("Not Found", ignoreCase = true) ->
                "API 地址或模型不存在，请检查 API 地址和模型名称"

            rawMessage.contains("429", ignoreCase = true) ||
            rawMessage.contains("Too Many Requests", ignoreCase = true) ||
            rawMessage.contains("rate_limit", ignoreCase = true) ->
                "API 请求频率超限，请稍后再试"

            rawMessage.contains("500", ignoreCase = true) ||
            rawMessage.contains("Internal Server Error", ignoreCase = true) ->
                "API 服务器内部错误，请稍后再试"

            rawMessage.contains("SSL", ignoreCase = true) ||
            rawMessage.contains("certificate", ignoreCase = true) ->
                "SSL 证书验证失败，请检查 API 地址是否使用了 HTTPS"

            else -> "测试失败: $rawMessage"
        }
    }

    override suspend fun makeRawRequest(
        config: AIModelConfig,
        endpoint: String,
        requestBody: String
    ): Result<String> {
        // Check rate limit
        if (!isRequestAllowed(config.apiKey)) {
            return Result.failure(Exception("Rate limit exceeded. Please try again later."))
        }
        
        return try {
            // Build full URL from endpoint
            val baseUrl = config.apiEndpoint.removeSuffix("/")
            val fullUrl = if (endpoint.startsWith("http")) {
                endpoint
            } else {
                // For embeddings, we need to use the base URL + endpoint
                // Most APIs use: https://api.openai.com/v1/embeddings
                val baseEndpoint = baseUrl.substringBeforeLast("/")
                "$baseEndpoint/$endpoint"
            }

            val headers = getHeadersForModel(config)

            val requestBuilder = Request.Builder()
                .url(fullUrl)
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Content-Type", "application/json")

            headers.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }

            val request = requestBuilder.build()
            val response = withContext(Dispatchers.IO) {
                val client = getConfiguredClient()
                client.newCall(request).execute()
            }
            val responseBody = response.body?.string() 
                ?: return Result.failure(Exception("Empty response"))

            Timber.d("Raw API Response from $endpoint: ${responseBody.take(200)}...")

            if (response.isSuccessful) {
                Result.success(responseBody)
            } else {
                Result.failure(Exception("API Error: ${response.code} - $responseBody"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Raw API request failed for $endpoint")
            Result.failure(e)
        }
    }

    /**
     * Get authentication headers for a model type.
     */
    private fun getHeadersForModel(config: AIModelConfig): Map<String, String> {
        // Check if it's NVIDIA API based on the endpoint
        if (config.apiEndpoint.contains("nvidia.com")) {
            return mapOf("Authorization" to "Bearer ${config.apiKey}")
        }
        
        return when (config.modelType) {
            ModelType.OPENAI -> mapOf("Authorization" to "Bearer ${config.apiKey}")
            ModelType.CLAUDE -> mapOf(
                "x-api-key" to config.apiKey,
                "anthropic-version" to "2023-06-01"
            )
            ModelType.ZHIPU -> mapOf("Authorization" to "Bearer ${config.apiKey}")
            ModelType.TONGYI -> mapOf("Authorization" to "Bearer ${config.apiKey}")
            ModelType.CUSTOM -> mapOf("Authorization" to "Bearer ${config.apiKey}")
        }
    }

    private fun buildRequest(
        config: AIModelConfig,
        messages: List<AIClient.Message>,
        temperature: Float,
        maxTokens: Int
    ): Pair<okhttp3.RequestBody, Map<String, String>> {
        // For NVIDIA API, use OpenAI format with proper endpoint
        if (config.apiEndpoint.contains("nvidia.com")) {
            return buildNvidiaRequest(config, messages, temperature, maxTokens)
        }
        
        return when (config.modelType) {
            ModelType.OPENAI -> buildOpenAIRequest(config, messages, temperature, maxTokens)
            ModelType.CLAUDE -> buildClaudeRequest(config, messages, maxTokens)
            ModelType.ZHIPU -> buildZhipuRequest(config, messages, temperature, maxTokens)
            ModelType.TONGYI -> buildTongyiRequest(config, messages, temperature, maxTokens)
            ModelType.CUSTOM -> buildCustomRequest(config, messages, temperature, maxTokens)
        }
    }
    
    /**
     * Build request for NVIDIA API
     */
    private fun buildNvidiaRequest(
        config: AIModelConfig,
        messages: List<AIClient.Message>,
        temperature: Float,
        maxTokens: Int
    ): Pair<okhttp3.RequestBody, Map<String, String>> {
        val json = JSONObject()
        json.put("model", getModelName(config))
        json.put("temperature", temperature)
        json.put("max_tokens", maxTokens)

        val messagesArray = JSONArray()
        messages.forEach { message ->
            val msgObj = JSONObject()
            msgObj.put("role", message.role)
            msgObj.put("content", message.content)
            messagesArray.put(msgObj)
        }
        json.put("messages", messagesArray)

        val headers = mapOf("Authorization" to "Bearer ${config.apiKey}")
        return Pair(json.toString().toRequestBody("application/json".toMediaType()), headers)
    }

    private fun buildOpenAIRequest(
        config: AIModelConfig,
        messages: List<AIClient.Message>,
        temperature: Float,
        maxTokens: Int
    ): Pair<okhttp3.RequestBody, Map<String, String>> {
        val json = JSONObject()
        json.put("model", getModelName(config))
        json.put("temperature", temperature)
        json.put("max_tokens", maxTokens)

        val messagesArray = JSONArray()
        messages.forEach { message ->
            val msgObj = JSONObject()
            msgObj.put("role", message.role)
            msgObj.put("content", message.content)
            messagesArray.put(msgObj)
        }
        json.put("messages", messagesArray)

        val headers = getHeadersForModel(config)
        return Pair(json.toString().toRequestBody("application/json".toMediaType()), headers)
    }

    private fun buildClaudeRequest(
        config: AIModelConfig,
        messages: List<AIClient.Message>,
        maxTokens: Int
    ): Pair<okhttp3.RequestBody, Map<String, String>> {
        val json = JSONObject()
        json.put("model", getModelName(config))
        json.put("max_tokens", maxTokens)

        // Claude API uses "messages" array with system prompt separate
        val messagesArray = JSONArray()
        var systemContent: String? = null

        messages.forEach { message ->
            when (message.role) {
                "system" -> systemContent = message.content
                else -> {
                    val msgObj = JSONObject()
                    msgObj.put("role", message.role)
                    msgObj.put("content", message.content)
                    messagesArray.put(msgObj)
                }
            }
        }
        json.put("messages", messagesArray)

        if (systemContent != null) {
            json.put("system", systemContent)
        }

        val headers = mapOf(
            "x-api-key" to config.apiKey,
            "anthropic-version" to "2023-06-01",
            "anthropic-dangerous-direct-browser-access" to "true"
        )
        return Pair(json.toString().toRequestBody("application/json".toMediaType()), headers)
    }

    private fun buildZhipuRequest(
        config: AIModelConfig,
        messages: List<AIClient.Message>,
        temperature: Float,
        maxTokens: Int
    ): Pair<okhttp3.RequestBody, Map<String, String>> {
        val json = JSONObject()
        json.put("model", getModelName(config))
        json.put("temperature", temperature)
        json.put("max_tokens", maxTokens)

        val messagesArray = JSONArray()
        messages.forEach { message ->
            val msgObj = JSONObject()
            msgObj.put("role", message.role)
            msgObj.put("content", message.content)
            messagesArray.put(msgObj)
        }
        json.put("messages", messagesArray)

        val headers = mapOf("Authorization" to "Bearer ${config.apiKey}")
        return Pair(json.toString().toRequestBody("application/json".toMediaType()), headers)
    }

    private fun buildTongyiRequest(
        config: AIModelConfig,
        messages: List<AIClient.Message>,
        temperature: Float,
        maxTokens: Int
    ): Pair<okhttp3.RequestBody, Map<String, String>> {
        // 通义千问新版 API 已兼容 OpenAI 格式
        return buildOpenAIRequest(config, messages, temperature, maxTokens)
    }

    private fun buildCustomRequest(
        config: AIModelConfig,
        messages: List<AIClient.Message>,
        temperature: Float,
        maxTokens: Int
    ): Pair<okhttp3.RequestBody, Map<String, String>> {
        // For custom models, try to use OpenAI format as default
        return buildOpenAIRequest(config, messages, temperature, maxTokens)
    }

    private fun getModelName(config: AIModelConfig): String {
        // Use model field (model ID like gpt-4) if set, otherwise fallback to modelName or defaults
        if (config.model.isNotBlank()) {
            return config.model
        }
        return when (config.modelType) {
            ModelType.OPENAI -> "gpt-3.5-turbo"
            ModelType.CLAUDE -> "claude-3-haiku-20240307"
            ModelType.ZHIPU -> "glm-4"
            ModelType.TONGYI -> "qwen-turbo"
            ModelType.CUSTOM -> "gpt-3.5-turbo"
        }
    }

    private fun parseResponse(responseBody: String, modelType: ModelType): String {
        return try {
            val json = JSONObject(responseBody)
            // NVIDIA API uses OpenAI-compatible format
            if (responseBody.contains("choices") && responseBody.contains("message")) {
                parseOpenAIResponse(json)
            } else {
                when (modelType) {
                    ModelType.OPENAI, ModelType.ZHIPU, ModelType.TONGYI -> {
                        // Standard chat completion format (OpenAI-compatible)
                        parseOpenAIResponse(json)
                    }
                    ModelType.CLAUDE -> {
                        // Claude has different response format
                        parseClaudeResponse(json)
                    }
                    ModelType.CUSTOM -> {
                        // Try OpenAI format first, then fallback
                        try {
                            parseOpenAIResponse(json)
                        } catch (e: Exception) {
                            parseClaudeResponse(json)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse response")
            extractContentFromResponse(responseBody)
        }
    }

    private fun parseOpenAIResponse(json: JSONObject): String {
        val choices = json.getJSONArray("choices")
        if (choices.length() > 0) {
            val firstChoice = choices.getJSONObject(0)
            val message = firstChoice.getJSONObject("message")
            return message.getString("content")
        } else {
            throw Exception("No choices in response")
        }
    }

    private fun parseClaudeResponse(json: JSONObject): String {
        // Claude response format: {"content": [{"type": "text", "text": "..."}]}
        val content = json.optJSONArray("content")
        if (content != null && content.length() > 0) {
            val firstContent = content.getJSONObject(0)
            return firstContent.getString("text")
        }
        throw Exception("No content in Claude response")
    }

    private fun extractContentFromResponse(responseBody: String): String {
        return try {
            val json = JSONObject(responseBody)
            when {
                json.has("response") -> json.getString("response")
                json.has("text") -> json.getString("text")
                json.has("output") -> json.getString("output")
                json.has("content") -> {
                    val content = json.get("content")
                    if (content is String) content
                    else if (content is JSONArray) {
                        val arr = content as JSONArray
                        if (arr.length() > 0) {
                            val first = arr.getJSONObject(0)
                            if (first.has("text")) first.getString("text")
                            else first.toString()
                        } else responseBody
                    } else responseBody
                }
                json.has("choices") -> {
                    val choices = json.getJSONArray("choices")
                    if (choices.length() > 0) {
                        val first = choices.getJSONObject(0)
                        if (first.has("message")) first.getJSONObject("message").getString("content")
                        else if (first.has("content")) first.getString("content")
                        else first.toString()
                    } else responseBody
                }
                else -> responseBody
            }
        } catch (e: Exception) {
            responseBody
        }
    }
}
