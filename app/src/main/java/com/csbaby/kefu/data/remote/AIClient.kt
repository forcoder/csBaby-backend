package com.csbaby.kefu.data.remote

import com.csbaby.kefu.domain.model.AIModelConfig
import com.csbaby.kefu.domain.model.ModelType
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
    
    // Request throttling
    private val requestCounters = ConcurrentHashMap<String, AtomicInteger>()
    private val MAX_REQUESTS_PER_MINUTE = 60
    private val REQUEST_WINDOW_MS = 60000L
    private val requestTimestamps = ConcurrentHashMap<String, MutableList<Long>>()
    
    // Timeout settings
    private val READ_TIMEOUT_SECONDS = 30L
    private val CONNECT_TIMEOUT_SECONDS = 10L
    
    /**
     * Check if request is allowed based on rate limiting
     */
    private fun isRequestAllowed(apiKey: String): Boolean {
        val now = System.currentTimeMillis()
        val timestamps = requestTimestamps.getOrPut(apiKey) { mutableListOf() }
        
        // Remove timestamps outside the window
        timestamps.removeIf { now - it > REQUEST_WINDOW_MS }
        
        if (timestamps.size >= MAX_REQUESTS_PER_MINUTE) {
            Timber.w("Rate limit reached for API key")
            return false
        }
        
        timestamps.add(now)
        return true
    }
    
    /**
     * Get a client with appropriate timeouts
     */
    private fun getConfiguredClient(): OkHttpClient {
        return okHttpClient.newBuilder()
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun generateCompletion(
        config: AIModelConfig,
        messages: List<AIClient.Message>,
        temperature: Float,
        maxTokens: Int
    ): Result<String> {
        // Check rate limit
        if (!isRequestAllowed(config.apiKey)) {
            return Result.failure(Exception("Rate limit exceeded. Please try again later."))
        }
        
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

            val request = requestBuilder.build()
            Timber.d("AI Request URL: $endpoint")
            Timber.d("AI Request Body: ${requestBody.toString().take(500)}")
            Timber.d("AI Request Headers: $headers")
            
            val client = getConfiguredClient()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return Result.failure(Exception("Empty response"))

            Timber.d("AI Response Status: ${response.code}")
            Timber.d("AI Response Body: $responseBody")

            if (response.isSuccessful) {
                val reply = parseResponse(responseBody, config.modelType)
                Timber.d("AI Parsed Reply: $reply")
                Result.success(reply)
            } else {
                val errorMsg = "API Error: ${response.code} - $responseBody"
                Timber.e(errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Timber.e(e, "AI request failed with exception: ${e.message}")
            Result.failure(Exception("请求失败: ${e.message}", e))
        }
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
            val client = getConfiguredClient()

            val requestBuilder = Request.Builder()
                .url(fullUrl)
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Content-Type", "application/json")

            headers.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }

            val request = requestBuilder.build()
            val response = client.newCall(request).execute()
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
