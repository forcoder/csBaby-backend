package com.csbaby.kefu.infrastructure.ai

import com.csbaby.kefu.data.remote.AIClient
import com.csbaby.kefu.domain.model.AIModelConfig
import com.csbaby.kefu.domain.model.ModelType
import com.csbaby.kefu.domain.model.UserStyleProfile
import com.csbaby.kefu.domain.repository.AIModelRepository
import com.csbaby.kefu.infrastructure.simple.SimpleTaskRouter
import com.csbaby.kefu.infrastructure.simple.TaskType
import com.csbaby.kefu.infrastructure.simple.RoutingResult
import com.csbaby.kefu.infrastructure.style.StyleLearningEngine
import kotlinx.coroutines.delay
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI Service for handling AI model interactions.
 */
@Singleton
class AIService @Inject constructor(
    private val aiClient: AIClient,
    private val aiModelRepository: AIModelRepository,
    private val simpleTaskRouter: SimpleTaskRouter
) {
    
    // AI response cache with LRU eviction
    private val responseCache = object : LinkedHashMap<String, CachedResponse>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedResponse>?): Boolean {
            return size > CACHE_SIZE
        }
    }
    private const val CACHE_SIZE = 800
    private const val CACHE_EXPIRY_MS = 1800000L // 30 minutes for better freshness

    // Performance monitoring
    private val requestMetrics = ConcurrentHashMap<String, RequestMetrics>()
    private val PERFORMANCE_WINDOW_MS = 3600000L // 1 hour performance window

    // Enhanced retry settings
    private const val MAX_RETRIES = 4
    private const val INITIAL_RETRY_DELAY_MS = 500L
    private const val MAX_RETRY_DELAY_MS = 8000L
    
    data class CachedResponse(
        val response: String,
        val timestamp: Long,
        val tokenCount: Int = 0 // For cost estimation
    )

    data class RequestMetrics(
        var totalRequests: Int = 0,
        var successfulRequests: Int = 0,
        var avgResponseTimeMs: Long = 0L,
        var lastUpdated: Long = System.currentTimeMillis(),
        var failureRate: Float = 0f
    )
    
    /**
     * Generate cache key for AI requests
     */
    private fun generateCacheKey(prompt: String, systemPrompt: String?, modelId: Long?): String {
        return "${modelId ?: "default"}:${systemPrompt ?: ""}:$prompt"
    }
    
    /**
     * Get cached response if available and not expired
     */
    private fun getCachedResponse(key: String): String? {
        val cached = responseCache[key]
        if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_EXPIRY_MS) {
            Timber.d("Using cached AI response")
            return cached.response
        }
        // Remove expired cache
        if (cached != null) {
            responseCache.remove(key)
        }
        return null
    }
    
    /**
     * Cache AI response
     */
    private fun cacheResponse(key: String, response: String) {
        if (responseCache.size >= CACHE_SIZE) {
            // Remove oldest entry
            val oldestKey = responseCache.entries.minByOrNull { it.value.timestamp }?.key
            oldestKey?.let { responseCache.remove(it) }
        }
        responseCache[key] = CachedResponse(response, System.currentTimeMillis())
    }
    
    /**
     * Enhanced retry mechanism with exponential backoff and error classification
     */
    private suspend fun <T> withRetry(
        modelId: Long,
        block: suspend () -> Result<T>
    ): Result<T> {
        var lastException: Exception? = null
        var currentDelay = INITIAL_RETRY_DELAY_MS

        for (attempt in 1..MAX_RETRIES) {
            val startTime = System.currentTimeMillis()
            val result = block()
            val responseTime = System.currentTimeMillis() - startTime

            // Update metrics even on failure
            updateRequestMetrics(modelId, result.isSuccess, responseTime)

            if (result.isSuccess) {
                return result
            }

            lastException = result.exceptionOrNull() as? Exception
            Timber.w("AI request attempt $attempt failed for model $modelId: ${lastException?.message}")

            // Classify error and adjust retry strategy
            if (attempt < MAX_RETRIES && shouldRetry(lastException)) {
                Timber.d("Retrying model $modelId in ${currentDelay}ms (error type: ${classifyError(lastException)})")
                delay(currentDelay)
                currentDelay = (currentDelay * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
            } else {
                break
            }
        }

        return Result.failure(lastException ?: Exception("All retry attempts failed for model $modelId"))
    }

    private fun shouldRetry(exception: Exception?): Boolean {
        if (exception == null) return false

        val message = exception.message?.lowercase() ?: ""

        // Don't retry certain error types
        when {
            message.contains("invalid api key", ignoreCase = true) ||
            message.contains("unauthorized", ignoreCase = true) ||
            message.contains("forbidden", ignoreCase = true) ||
            message.contains("model not found", ignoreCase = true) ||
            message.contains("insufficient_quota", ignoreCase = true) -> return false

            // Retry on network issues and rate limits
            message.contains("timeout", ignoreCase = true) ||
            message.contains("connection", ignoreCase = true) ||
            message.contains("rate limit", ignoreCase = true) ||
            message.contains("too many requests", ignoreCase = true) -> return true

            // Retry on server errors
            message.contains("500", ignoreCase = true) ||
            message.contains("502", ignoreCase = true) ||
            message.contains("503", ignoreCase = true) ||
            message.contains("service unavailable", ignoreCase = true) -> return true

            else -> return true // Unknown errors, try again
        }
    }

    private fun classifyError(exception: Exception?): String {
        val message = exception?.message?.lowercase() ?: "unknown"

        return when {
            message.contains("network", ignoreCase = true) ||
            message.contains("connection", ignoreCase = true) ||
            message.contains("timeout", ignoreCase = true) -> "NETWORK"

            message.contains("rate limit", ignoreCase = true) ||
            message.contains("too many requests", ignoreCase = true) -> "RATE_LIMIT"

            message.contains("authentication", ignoreCase = true) ||
            message.contains("api key", ignoreCase = true) -> "AUTH"

            message.contains("server error", ignoreCase = true) ||
            message.contains("500", ignoreCase = true) ||
            message.contains("502", ignoreCase = true) -> "SERVER_ERROR"

            message.contains("not found", ignoreCase = true) ||
            message.contains("404", ignoreCase = true) -> "NOT_FOUND"

            else -> "UNKNOWN"
        }
    }
    /**
     * Enhanced completion with intelligent model selection and performance-based routing.
     */
    suspend fun generateCompletion(
        prompt: String,
        systemPrompt: String? = null,
        temperature: Float? = null,
        maxTokens: Int? = null,
        preferredModelType: ModelType? = null
    ): Result<String> {
        val startTime = System.currentTimeMillis()

        // Get all enabled models with usage limits
        val models = mutableListOf<AIModelConfig>()
        aiModelRepository.getAllModels().collect {
            models.addAll(it.filter { model ->
                model.isEnabled && !hasReachedUsageLimit(model) &&
                (preferredModelType == null || model.modelType == preferredModelType)
            })
        }

        if (models.isEmpty()) {
            return Result.failure(Exception("没有可用的AI模型，请检查模型配置"))
        }

        // Sort models by performance metrics and availability
        val sortedModels = sortModelsByPerformance(models, prompt.length)

        // Try each model with enhanced retry logic
        var lastException: Exception? = null
        for (model in sortedModels) {
            val result = generateCompletionWithModel(
                modelId = model.id,
                prompt = prompt,
                systemPrompt = systemPrompt,
                temperature = temperature,
                maxTokens = maxTokens
            )

            if (result.isSuccess) {
                val responseTime = System.currentTimeMillis() - startTime
                Timber.i("Successfully used model ${model.modelName}, response time: ${responseTime}ms")

                // Update model as preferred for similar tasks
                if (responseTime < 2000) { // Fast responses make good defaults
                    aiModelRepository.setDefaultModel(model.id)
                }

                return result
            }

            lastException = result.exceptionOrNull() as? Exception
            Timber.w("Model ${model.modelName} failed: ${lastException?.message}")

            // If it's a rate limit error, break early to avoid wasting time
            if (lastException?.message?.contains("rate limit", ignoreCase = true) == true) {
                break
            }
        }

        val totalTime = System.currentTimeMillis() - startTime
        Timber.e("All AI models failed after ${totalTime}ms: ${lastException?.message}")
        return Result.failure(lastException ?: Exception("所有AI模型都尝试失败，请检查模型配置和网络连接"))
    }

    private fun sortModelsByPerformance(models: List<AIModelConfig>, promptLength: Int): List<AIModelConfig> {
        // Calculate performance score for each model
        return models.map { model ->
            val performanceScore = calculateModelPerformanceScore(model, promptLength)
            Pair(model, performanceScore)
        }.sortedByDescending { it.second }.map { it.first }
    }

    private fun calculateModelPerformanceScore(model: AIModelConfig, promptLength: Int): Float {
        var score = 0f

        // Base score from historical performance
        val metrics = requestMetrics[model.id.toString()]
        if (metrics != null) {
            // Higher success rate is better
            score += metrics.successRate * 0.3f

            // Faster average response time is better (inverted)
            val normalizedResponseTime = (5000 - metrics.avgResponseTimeMs.coerceAtMost(5000)) / 5000f
            score += normalizedResponseTime * 0.2f

            // Lower failure rate is better
            score += (1 - metrics.failureRate) * 0.2f
        }

        // Token capacity consideration
        when {
            promptLength > 8000 && model.maxTokens >= 32768 -> score += 0.3f
            promptLength > 4000 && model.maxTokens >= 16384 -> score += 0.2f
            promptLength > 2000 && model.maxTokens >= 8192 -> score += 0.1f
        }

        // Cost efficiency (lower cost per token is better)
        val costPerToken = model.monthlyCost / model.maxTokens
        score += (1 - minOf(costPerToken / 0.001f, 1f)) * 0.1f

        return score.coerceIn(0f, 1f)
    }

    private fun updateRequestMetrics(modelId: Long, success: Boolean, responseTime: Long) {
        val key = modelId.toString()
        val now = System.currentTimeMillis()

        requestMetrics.getOrPut(key) { RequestMetrics() }.let { metrics ->
            synchronized(metrics) {
                metrics.totalRequests++
                if (success) {
                    metrics.successfulRequests++

                    // Update average response time with exponential moving average
                    val alpha = 0.3f
                    metrics.avgResponseTimeMs = (metrics.avgResponseTimeMs * (1 - alpha) +
                                                  responseTime * alpha).toLong()
                }

                metrics.lastUpdated = now
                metrics.failureRate = (metrics.totalRequests - metrics.successfulRequests) /
                                     metrics.totalRequests.toFloat()

                // Clean up old metrics
                if (now - metrics.lastUpdated > PERFORMANCE_WINDOW_MS) {
                    requestMetrics.remove(key)
                }
            }
        }
    }

    /**
     * Get performance statistics for monitoring and debugging.
     */
    fun getPerformanceStats(): Map<String, Any> {
        val stats = mutableMapOf<String, Any>()

        val totalRequests = requestMetrics.values.sumOf { it.totalRequests }
        val totalSuccessful = requestMetrics.values.sumOf { it.successfulRequests }
        val overallSuccessRate = if (totalRequests > 0) totalSuccessful.toFloat() / totalRequests else 0f

        stats["total_requests"] = totalRequests
        stats["total_successful"] = totalSuccessful
        stats["overall_success_rate"] = overallSuccessRate
        stats["cached_responses"] = responseCache.size
        stats["active_models"] = requestMetrics.size

        return stats
    }

    /**
     * Enhanced usage limit check with dynamic thresholds.
     */
    private fun hasReachedUsageLimit(model: AIModelConfig): Boolean {
        // Dynamic limits based on model type and performance
        val baseLimits = mapOf(
            ModelType.CLAUDE to 5.0,    // Claude is expensive but reliable
            ModelType.OPENAI to 8.0,    // OpenAI GPT models
            ModelType.ZHIPU to 3.0,     // Zhipu AI
            ModelType.TONGYI to 3.0,    // Tongyi Qianwen
            ModelType.CUSTOM to 2.0     // Custom models
        )

        val limit = baseLimits[model.modelType] ?: 2.0

        // Add dynamic adjustment based on recent performance
        val metrics = requestMetrics[model.id.toString()]
        val performanceMultiplier = if (metrics != null) {
            when {
                metrics.failureRate > 0.3f -> 0.7f  // Reduce limit for poor performers
                metrics.successRate > 0.9f -> 1.2f   // Increase limit for good performers
                else -> 1.0f
            }
        } else 1.0f

        val adjustedLimit = limit * performanceMultiplier

        return model.monthlyCost >= adjustedLimit
    }

    /**
     * Generate completion with a specific model.
     */
    suspend fun generateCompletionWithModel(
        modelId: Long,
        prompt: String,
        systemPrompt: String? = null,
        temperature: Float? = null,
        maxTokens: Int? = null
    ): Result<String> {
        val model = aiModelRepository.getModelById(modelId)
            ?: return Result.failure(Exception("Model not found"))

        // Check cache first
        val cacheKey = generateCacheKey(prompt, systemPrompt, modelId)
        getCachedResponse(cacheKey)?.let {
            return Result.success(it)
        }

        val messages = buildMessages(prompt, systemPrompt)
        val result = withRetry {
            aiClient.generateCompletion(
                config = model,
                messages = messages,
                temperature = temperature ?: model.temperature,
                maxTokens = maxTokens ?: model.maxTokens
            )
        }

        // Update usage statistics on success
        result.onSuccess {
            // Cache the response
            cacheResponse(cacheKey, it)
            
            // Estimate cost (simplified)
            val estimatedCost = estimateCost(prompt.length + it.length, model)
            aiModelRepository.addCost(modelId, estimatedCost)
            aiModelRepository.updateLastUsed(modelId)
        }

        return result
    }

    /**
     * Enhanced completion with intelligent task routing and context awareness.
     */
    suspend fun generateCompletionWithRouting(
        prompt: String,
        systemPrompt: String? = null,
        taskType: TaskType,
        temperature: Float? = null,
        maxTokens: Int? = null,
        contextInfo: Map<String, Any>? = null
    ): Result<String> {
        val startTime = System.currentTimeMillis()

        // Get available models with dynamic filtering
        val models = mutableListOf<AIModelConfig>()
        aiModelRepository.getAllModels().collect {
            models.addAll(it.filter { model ->
                model.isEnabled && !hasReachedUsageLimit(model)
            })
        }

        if (models.isEmpty()) {
            return Result.failure(Exception("没有可用的AI模型，请检查模型配置"))
        }

        // Enhanced smart routing with performance awareness
        val routingResult = enhancedSelectBestModel(taskType, models, contextInfo)

        val selectedModel = when (routingResult) {
            is RoutingResult.SingleChoice -> routingResult.selectedModel.model
            is RoutingResult.MultipleChoices -> {
                // If multiple candidates, pick the one with best recent performance
                val bestPerformer = routingResult.candidates.maxByOrNull { modelScore ->
                    requestMetrics[modelScore.model.id.toString()]?.successRate ?: 0f
                }
                bestPerformer?.model ?: models.first()
            }
            is RoutingResult.NoSuitableModel -> {
                Timber.d("Using fallback model selection for task: $taskType")
                // Fallback with performance-based sorting
                sortModelsByPerformance(models, prompt.length).first()
            }
        }

        Timber.i("Enhanced routing selected model: ${selectedModel.modelName} for task: $taskType")

        // Generate completion with selected model using enhanced retry
        val result = generateCompletionWithModel(
            modelId = selectedModel.id,
            prompt = prompt,
            systemPrompt = systemPrompt,
            temperature = temperature ?: selectedModel.temperature,
            maxTokens = maxTokens ?: selectedModel.maxTokens
        )

        // Log routing decision for analysis
        val responseTime = System.currentTimeMillis() - startTime
        logRoutingDecision(taskType, selectedModel, responseTime, result.isSuccess)

        return result
    }

    private fun enhancedSelectBestModel(
        taskType: TaskType,
        models: List<AIModelConfig>,
        contextInfo: Map<String, Any>? = null
    ): RoutingResult {
        val modelScores = mutableListOf<ModelScore>()

        for (model in models) {
            val score = calculateEnhancedModelScore(model, taskType, contextInfo)

            if (score > 0.1f) { // Minimum threshold
                modelScores.add(ModelScore(
                    model = model,
                    score = score,
                    reasoning = generateEnhancedRoutingReasoning(score, model, taskType, contextInfo)
                ))
            }
        }

        return when {
            modelScores.isEmpty() -> RoutingResult.NoSuitableModel("没有适合此任务的模型")
            modelScores.size == 1 -> RoutingResult.SingleChoice(modelScores.first())
            else -> {
                val sortedScores = modelScores.sortedByDescending { it.score }
                val topCandidates = sortedScores.take(3)
                RoutingResult.MultipleChoices(topCandidates)
            }
        }
    }

    private fun calculateEnhancedModelScore(
        model: AIModelConfig,
        taskType: TaskType,
        contextInfo: Map<String, Any>? = null
    ): Float {
        var score = 0f

        // Base performance from historical metrics
        val metrics = requestMetrics[model.id.toString()]
        if (metrics != null) {
            score += metrics.successRate * 0.25f
            val normalizedResponseTime = (3000 - metrics.avgResponseTimeMs.coerceAtMost(3000)) / 3000f
            score += normalizedResponseTime * 0.15f
        }

        // Task-specific scoring
        score += getTaskSpecificScore(taskType, model, contextInfo)

        // Cost efficiency (lower cost per token is better)
        val costEfficiency = when (model.monthlyCost) {
            in 0.0..1.0 -> 0.3f
            in 1.0..3.0 -> 0.2f
            in 3.0..5.0 -> 0.1f
            else -> 0f
        }
        score += costEfficiency

        // Model capability matching
        score += getCapabilityScore(model, contextInfo)

        // Recent usage preference (avoid overused models)
        val hoursSinceLastUse = (System.currentTimeMillis() - model.lastUsed) / 3600000
        if (hoursSinceLastUse > 24) score += 0.1f // Prefer models not used today

        return score.coerceIn(0f, 1f)
    }

    private fun getTaskSpecificScore(taskType: TaskType, model: AIModelConfig, contextInfo: Map<String, Any>?): Float {
        var score = 0f

        when (taskType) {
            TaskType.CUSTOMER_SERVICE -> {
                // Prioritize fast, reliable models for customer service
                if (model.maxTokens >= 4096) score += 0.1f
                if (model.monthlyCost < 3.0) score += 0.15f
            }
            TaskType.DOCUMENT_PROCESSING -> {
                // Long context models preferred
                if (model.maxTokens >= 32768) score += 0.25f
                if (model.modelType == ModelType.CLAUDE) score += 0.1f // Claude excels at long context
            }
            TaskType.REASONING -> {
                // High-quality reasoning models
                if (model.maxTokens >= 16384) score += 0.2f
                if (model.model.contains("opus") || model.model.contains("sonnet")) score += 0.1f
            }
            TaskType.CONTENT_WRITING -> {
                // Creative writing capabilities
                if (model.maxTokens >= 8192) score += 0.15f
                if (model.modelType == ModelType.OPENAI) score += 0.1f
            }
            TaskType.TRANSLATION -> {
                // Balanced performance and cost
                if (model.maxTokens >= 8192) score += 0.15f
                if (model.monthlyCost < 2.0) score += 0.1f
            }
            TaskType.VISION_TASKS -> {
                // Vision-capable models
                if (model.apiEndpoint.contains("vision") || model.model.contains("vision")) score += 0.3f
                if (model.maxTokens >= 4096) score += 0.1f
            }
            else -> {
                // General tasks - balance of speed and capability
                if (model.maxTokens >= 4096) score += 0.1f
                if (model.monthlyCost < 2.0) score += 0.1f
            }
        }

        // Context-aware adjustments
        contextInfo?.let { ctx ->
            ctx["urgency"]?.let { urgency ->
                if (urgency == "high" && model.maxTokens >= 4096) score += 0.1f
            }
            ctx["complexity"]?.let { complexity ->
                if (complexity == "high" && model.maxTokens >= 16384) score += 0.15f
            }
        }

        return score
    }

    private fun getCapabilityScore(model: AIModelConfig, contextInfo: Map<String, Any>?): Float {
        var score = 0f

        // API endpoint capabilities
        if (model.apiEndpoint.contains("nvidia.com")) {
            score += 0.1f // NVIDIA models often have good performance
        }

        // Model name hints
        when {
            model.model.contains("turbo", ignoreCase = true) -> score += 0.1f // Fast models
            model.model.contains("pro", ignoreCase = true) -> score += 0.05f // Professional models
            model.model.contains("mini", ignoreCase = true) -> score += 0.05f // Efficient models
        }

        return score
    }

    private fun generateEnhancedRoutingReasoning(
        score: Float,
        model: AIModelConfig,
        taskType: TaskType,
        contextInfo: Map<String, Any>?
    ): String {
        val reasons = mutableListOf<String>()

        // Performance reasons
        val metrics = requestMetrics[model.id.toString()]
        metrics?.let { m ->
            if (m.successRate > 0.95f) reasons.add("高成功率 (${(m.successRate * 100).toInt()}%)")
            if (m.avgResponseTimeMs < 2000) reasons.add("响应快速 (${m.avgResponseTimeMs}ms)")
        }

        // Task-specific reasons
        when (taskType) {
            TaskType.CUSTOMER_SERVICE -> {
                if (model.monthlyCost < 3.0) reasons.add("成本效益好")
                if (model.maxTokens >= 4096) reasons.add("支持客户服务对话")
            }
            TaskType.DOCUMENT_PROCESSING -> {
                if (model.maxTokens >= 32768) reasons.add("长文档处理能力")
                if (model.modelType == ModelType.CLAUDE) reasons.add("Claude擅长长文本处理")
            }
            TaskType.REASONING -> {
                if (model.maxTokens >= 16384) reasons.add("复杂推理能力")
                if (model.model.contains("opus")) reasons.add("高级推理模型")
            }
            TaskType.VISION_TASKS -> {
                if (model.model.contains("vision")) reasons.add("图像理解能力")
            }
            else -> {
                if (model.maxTokens >= 8192) reasons.add("通用任务能力强")
                if (model.monthlyCost < 2.0) reasons.add("高性价比")
            }
        }

        // Context-based reasons
        contextInfo?.let { ctx ->
            ctx["urgency"]?.let { urgency ->
                if (urgency == "high") reasons.add("适合紧急任务")
            }
            ctx["complexity"]?.let { complexity ->
                if (complexity == "high") reasons.add("复杂任务优化")
            }
        }

        return reasons.joinToString("；")
    }

    private fun logRoutingDecision(
        taskType: TaskType,
        selectedModel: AIModelConfig,
        responseTime: Long,
        success: Boolean
    ) {
        Timber.d("Routing Decision - Task: $taskType, Model: ${selectedModel.modelName}, " +
                "ResponseTime: ${responseTime}ms, Success: $success")
    }

    /**
     * Test connection to a model.
     */
    suspend fun testModelConnection(modelId: Long): Result<Boolean> {
        val model = aiModelRepository.getModelById(modelId)
            ?: return Result.failure(Exception("Model not found"))

        return aiClient.testConnection(model)
    }

    /**
     * Test connection with config directly.
     */
    suspend fun testConnection(config: AIModelConfig): Result<Boolean> {
        return aiClient.testConnection(config)
    }

    /**
     * Analyze text style using AI.
     */
    suspend fun analyzeTextStyle(text: String): Result<TextStyleAnalysis> {
        val analysisPrompt = """
            Analyze the writing style of the following text and provide metrics:
            1. Formality level (0-1, where 0 is very casual and 1 is very formal)
            2. Enthusiasm level (0-1, where 0 is neutral and 1 is very enthusiastic)
            3. Professionalism level (0-1, where 0 is casual and 1 is very professional)
            4. Average word count per sentence
            
            Text to analyze:
            "$text"
            
            Provide your analysis in JSON format:
            {"formality": 0.0-1.0, "enthusiasm": 0.0-1.0, "professionalism": 0.0-1.0, "avgWordsPerSentence": number}
        """.trimIndent()

        return withRetry {
            generateCompletion(analysisPrompt, systemPrompt = "You are a text style analysis assistant.")
        }.mapCatching { response ->
            parseStyleAnalysis(response)
        }
    }

    /**
     * Adjust text to match a style profile.
     */
    suspend fun adjustStyle(
        text: String,
        styleProfile: UserStyleProfile
    ): Result<String> {
        val adjustmentPrompt = """
            Rewrite the following text to match the specified style:
            
            Original text:
            "$text"
            
            Target style:
            - Formality: ${(styleProfile.formalityLevel * 100).toInt()}%
            - Enthusiasm: ${(styleProfile.enthusiasmLevel * 100).toInt()}%
            - Professionalism: ${(styleProfile.professionalismLevel * 100).toInt()}%
            
            Keep the meaning and key information intact, only adjust the writing style.
        """.trimIndent()

        val systemPrompt = buildStyleSystemPrompt(styleProfile)

        return withRetry {
            generateCompletion(adjustmentPrompt, systemPrompt = systemPrompt)
        }
    }

    private fun buildMessages(prompt: String, systemPrompt: String?): List<AIClient.Message> {
        val messages = mutableListOf<AIClient.Message>()

        if (!systemPrompt.isNullOrBlank()) {
            messages.add(AIClient.Message("system", systemPrompt))
        }

        messages.add(AIClient.Message("user", prompt))

        return messages
    }

    private fun buildStyleSystemPrompt(profile: UserStyleProfile): String {
        val formalityDesc = when {
            profile.formalityLevel < 0.3 -> "very casual, conversational"
            profile.formalityLevel < 0.5 -> "somewhat casual"
            profile.formalityLevel < 0.7 -> "somewhat formal"
            else -> "formal, professional"
        }

        val enthusiasmDesc = when {
            profile.enthusiasmLevel < 0.3 -> "reserved, neutral"
            profile.enthusiasmLevel < 0.5 -> "calm"
            profile.enthusiasmLevel < 0.7 -> "friendly"
            else -> "enthusiastic, warm"
        }

        val professionalismDesc = when {
            profile.professionalismLevel < 0.3 -> "friendly and approachable"
            profile.professionalismLevel < 0.5 -> "knowledgeable"
            profile.professionalismLevel < 0.7 -> "professional"
            else -> "expert-level professional"
        }

        return "You are writing as a customer service representative who is $formalityDesc, $enthusiasmDesc, and $professionalismDesc."
    }

    private fun estimateCost(totalTokens: Int, model: AIModelConfig): Double {
        // Simplified cost estimation
        val costPer1kTokens = when (model.modelType) {
            ModelType.OPENAI -> 0.002 // GPT-3.5-turbo
            ModelType.CLAUDE -> 0.001 // Claude Haiku
            ModelType.ZHIPU -> 0.001 // Zhipu
            ModelType.TONGYI -> 0.001 // Qwen
            ModelType.CUSTOM -> 0.0
        }
        return totalTokens / 1000.0 * costPer1kTokens
    }


    private fun parseStyleAnalysis(response: String): TextStyleAnalysis {
        return try {
            val json = response.trim().let {
                // Remove markdown code blocks if present
                if (it.startsWith("```")) {
                    it.substringAfter("```json").substringBefore("```").trim()
                } else {
                    it
                }
            }
            val obj = org.json.JSONObject(json)
            TextStyleAnalysis(
                formality = obj.getDouble("formality").toFloat().coerceIn(0f, 1f),
                enthusiasm = obj.getDouble("enthusiasm").toFloat().coerceIn(0f, 1f),
                professionalism = obj.getDouble("professionalism").toFloat().coerceIn(0f, 1f),
                avgWordsPerSentence = obj.getDouble("avgWordsPerSentence").toFloat()
            )
        } catch (e: Exception) {
            // Return default analysis on parse failure
            TextStyleAnalysis(
                formality = 0.5f,
                enthusiasm = 0.5f,
                professionalism = 0.5f,
                avgWordsPerSentence = 15f
            )
        }
    }

    data class TextStyleAnalysis(
        val formality: Float,
        val enthusiasm: Float,
        val professionalism: Float,
        val avgWordsPerSentence: Float
    )
}
