package com.csbaby.kefu.infrastructure.reply

import android.content.Context
import android.util.Log
import com.csbaby.kefu.data.local.PreferencesManager
import com.csbaby.kefu.domain.model.ReplyContext
import com.csbaby.kefu.domain.model.ReplyResult
import com.csbaby.kefu.domain.model.ReplySource
import com.csbaby.kefu.domain.repository.MessageBlacklistRepository
import com.csbaby.kefu.infrastructure.knowledge.KnowledgeBaseManager
import com.csbaby.kefu.infrastructure.notification.MessageMonitor
import com.csbaby.kefu.infrastructure.search.HybridSearchEngine
import com.csbaby.kefu.infrastructure.window.FloatingWindowService
import com.csbaby.kefu.infrastructure.llm.LLMFeatureManager
import com.csbaby.kefu.infrastructure.llm.OptimizationEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reply Orchestrator - Coordinates the entire reply generation workflow.
 * Handles message reception, reply generation, and UI update.
 */
@Singleton
class ReplyOrchestrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val replyGenerator: ReplyGenerator,
    private val messageMonitor: MessageMonitor,
    private val preferencesManager: PreferencesManager,
    private val knowledgeBaseManager: KnowledgeBaseManager,
    private val blacklistRepository: MessageBlacklistRepository,
    private val llmFeatureManager: LLMFeatureManager,
    private val optimizationEngine: OptimizationEngine
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var currentJob: Job? = null
    private var collectorJob: Job? = null
    private var matcherJob: Job? = null
    private var iconObserverJob: Job? = null

    /**
     * Start orchestrating reply generation for incoming messages.
     */
    @Synchronized
    fun start() {
        Log.d(TAG, "ReplyOrchestrator.start() called, collectorJob.active=${collectorJob?.isActive}")

        // 始终启动/重启 iconObserverJob，确保监听 floatingIconEnabled
        iconObserverJob?.cancel()
        iconObserverJob = scope.launch {
            Log.d(TAG, "ReplyOrchestrator: iconObserverJob started, watching floatingIconEnabled")
            preferencesManager.userPreferencesFlow.collect { prefs ->
                Log.d(TAG, "ReplyOrchestrator: floatingIconEnabled changed to ${prefs.floatingIconEnabled}")
                if (prefs.floatingIconEnabled) {
                    // 如果开启了悬浮图标，则显示图标
                    Log.d(TAG, "ReplyOrchestrator: calling FloatingWindowService.showIconOnly")
                    FloatingWindowService.showIconOnly(context)
                } else {
                    // 如果关闭了悬浮图标，则隐藏
                    FloatingWindowService.hide(context)
                }
            }
        }

        try {
            // Initialize default LLM features
            scope.launch {
                try {
                    Log.d(TAG, "ReplyOrchestrator: initializing default LLM features")
                    llmFeatureManager.initializeDefaultFeatures()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize default LLM features", e)
                }
            }

            // 始终确保 matcherJob 运行（初始化知识库匹配器）
            if (matcherJob?.isActive != true) {
                matcherJob?.cancel()
                matcherJob = scope.launch {
                    try {
                        Log.d(TAG, "ReplyOrchestrator: initializing knowledge base matcher")
                        knowledgeBaseManager.initializeMatcher()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to initialize knowledge base matcher", e)
                    }
                }
            }

            if (collectorJob?.isActive == true) {
                Log.d(TAG, "ReplyOrchestrator: already running, skip starting collectorJob")
                return
            }

            collectorJob = scope.launch {
                try {
                    Log.d(TAG, "ReplyOrchestrator: messageFlow collection started")
                    messageMonitor.messageFlow.collect { message ->
                        Log.d(TAG, "ReplyOrchestrator: message received from flow: ${message.packageName} / ${previewForLog(message.content)}")
                        handleNewMessage(message)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error collecting messages", e)
                }
            }

            // Start periodic optimization task
            scope.launch {
                try {
                    Log.d(TAG, "ReplyOrchestrator: starting periodic optimization task")
                    while (true) {
                        delay(OPTIMIZATION_CHECK_INTERVAL)
                        try {
                            optimizationEngine.runOptimizationCycle(FEATURE_REPLY_GENERATION)
                        } catch (e: Exception) {
                            Log.e(TAG, "Periodic optimization failed", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Optimization task failed", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ReplyOrchestrator", e)
        }
    }

    /**
     * Stop orchestrating.
     */
    @Synchronized
    fun stop() {
        currentJob?.cancel()
        collectorJob?.cancel()
        matcherJob?.cancel()
        iconObserverJob?.cancel()
        collectorJob = null
        matcherJob = null
        iconObserverJob = null
    }

    /**
     * Handle a new incoming message.
     */
    private fun handleNewMessage(message: MessageMonitor.MonitoredMessage) {
        try {
            // 过滤占位消息（如"给你发送了新消息"、"向你发送了一条消息"等）
            if (shouldSkipMessage(message)) {
                Log.d(
                    TAG,
                    "Skip placeholder message. title=${previewForLog(message.title)}, conversation=${previewForLog(message.conversationTitle)}, content=${previewForLog(message.content)}, package=${message.packageName}"
                )
                return
            }

            currentJob?.cancel()

            currentJob = scope.launch {
                try {
                    // 检查黑名单
                    if (blacklistRepository.shouldFilterMessage(
                        content = message.content,
                        sender = message.conversationTitle,
                        packageName = message.packageName
                    )) {
                        Log.d(
                            TAG,
                            "Message filtered by blacklist. content=${previewForLog(message.content)}, package=${message.packageName}"
                        )
                        return@launch
                    }

                    val isBaijuyiMessage = isBaijuyiMessage(message)
                if (isBaijuyiMessage) {
                    Log.d(
                        TAG,
                        "Baijuyi message received by ReplyOrchestrator. title=${previewForLog(message.title)}, conversation=${previewForLog(message.conversationTitle)}, content=${previewForLog(message.content)}, timestamp=${message.timestamp}"
                    )
                }

                val preferences = preferencesManager.userPreferencesFlow.first()
                if (!preferences.monitoringEnabled) {
                    if (isBaijuyiMessage) {
                        Log.d(TAG, "Baijuyi message skipped in ReplyOrchestrator: monitoring disabled")
                    }
                    return@launch
                }

                val monitoredApps = preferences.selectedApps
                if (message.packageName !in monitoredApps) {

                    if (isBaijuyiMessage) {
                        Log.d(TAG, "Baijuyi message skipped in ReplyOrchestrator: package not selected")
                    }
                    return@launch
                }

                val propertyName = extractPropertyName(message)
                val replyContext = ReplyContext(
                    appPackage = message.packageName,
                    scenarioId = null,
                    conversationTitle = message.conversationTitle ?: message.title,
                    propertyName = propertyName,
                    isGroupConversation = message.isGroupConversation,
                    userId = preferences.currentUserId
                )

                if (isBaijuyiMessage) {
                    Log.d(
                        TAG,
                        "Baijuyi reply context built. conversation=${previewForLog(replyContext.conversationTitle)}, property=${previewForLog(replyContext.propertyName)}, isGroup=${replyContext.isGroupConversation}, floatingEnabled=${preferences.floatingWindowEnabled}"
                    )
                }

                val result = replyGenerator.generateReply(
                    message = message.content,
                    context = replyContext
                )

                if (isBaijuyiMessage) {
                    Log.d(
                        TAG,
                        "Baijuyi reply generated. source=${result.source}, confidence=${result.confidence}, ruleId=${result.ruleId ?: -1}, modelId=${result.modelId ?: -1}, reply=${previewForLog(result.reply)}"
                    )
                }

                if (preferences.floatingWindowEnabled) {
                    if (isBaijuyiMessage) {
                        Log.d(TAG, "Baijuyi reply forwarding to floating window")
                    }
                    showFloatingWindow(message, result)
                } else if (isBaijuyiMessage) {
                    Log.d(TAG, "Baijuyi reply not shown: floating window disabled")
                }

            } catch (e: CancellationException) {

                Log.d(TAG, "Reply generation cancelled for a newer message")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate reply", e)
            }
        }
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleNewMessage", e)
        }
    }

    /**
     * Show floating window with reply suggestion.
     */
    private fun showFloatingWindow(message: MessageMonitor.MonitoredMessage, result: ReplyResult) {
        val displayData = FloatingWindowService.DisplayData(
            originalMessage = message.content,
            suggestedReply = result.reply,
            source = result.source.name,
            confidence = result.confidence,
            targetPackage = message.packageName,
            conversationTitle = (message.conversationTitle ?: message.title).ifBlank { message.appName },
            ruleId = result.ruleId ?: -1L,
            modelId = result.modelId ?: -1L
        )

        if (isBaijuyiMessage(message)) {
            Log.d(
                TAG,
                "Baijuyi floating display prepared. conversation=${previewForLog(displayData.conversationTitle)}, source=${displayData.source}, ruleId=${displayData.ruleId}, modelId=${displayData.modelId}"
            )
        }

        FloatingWindowService.show(context, displayData)
    }


    /**
     * Manually trigger reply generation for a message.
     */
    suspend fun generateReplyForMessage(
        message: String,
        appPackage: String
    ): ReplyResult {
        val preferences = preferencesManager.userPreferencesFlow.first()
        val context = ReplyContext(
            appPackage = appPackage,
            scenarioId = null,
            userId = preferences.currentUserId
        )

        return replyGenerator.generateReply(message, context)
    }

    /**
     * Record user's final reply (after they may have modified it).
     */
    suspend fun recordFinalReply(
        originalMessage: String,
        generatedReply: String,
        finalReply: String,
        appPackage: String,
        source: ReplySource,
        confidence: Float,
        ruleId: Long? = null,
        modelId: Long? = null
    ) {
        val preferences = preferencesManager.userPreferencesFlow.first()
        val context = ReplyContext(
            appPackage = appPackage,
            scenarioId = null,
            userId = preferences.currentUserId
        )

        // Get current active variant for tracking
        val variant = llmFeatureManager.getActiveVariant(FEATURE_REPLY_GENERATION).getOrNull()

        val result = ReplyResult(
            reply = generatedReply,
            source = source,
            confidence = confidence,
            ruleId = ruleId,
            modelId = modelId,
            variantId = variant?.id
        )

        replyGenerator.recordUserReply(
            originalMessage = originalMessage,
            generatedReply = generatedReply,
            finalReply = finalReply,
            context = context,
            result = result
        )

        // Update optimization metrics
        val modified = generatedReply != finalReply
        llmFeatureManager.updateMetrics(
            featureKey = FEATURE_REPLY_GENERATION,
            variantId = variant?.id ?: 0L,
            accepted = !modified,
            modified = modified,
            rejected = false,
            confidence = confidence
        )
    }


    /**
     * Generate multiple suggestions for a message.
     */
    suspend fun generateSuggestions(
        message: String,
        appPackage: String,
        count: Int = 3
    ): List<ReplyResult> {
        val preferences = preferencesManager.userPreferencesFlow.first()
        val context = ReplyContext(
            appPackage = appPackage,
            scenarioId = null,
            userId = preferences.currentUserId
        )

        return replyGenerator.generateSuggestions(message, context, count)
    }

    /**
     * Search knowledge base rules using hybrid search (keyword + semantic).
     * Supports multiple search modes based on user preference.
     * 
     * Search strategy:
     * 1. Always perform keyword search in keyword field
     * 2. If semantic search enabled, also do hybrid/semantic search
     * 3. Also search in reply templates, categories, and target names
     * 4. Merge and deduplicate all results
     */
    suspend fun searchKnowledgeRules(
        query: String,
        mode: HybridSearchMode = HybridSearchMode.AUTO
    ): List<FloatingWindowService.KnowledgeRuleItem> {
        Log.d(TAG, "Searching knowledge rules for: $query (mode=$mode)")
        
        return try {
            // Determine search mode based on preference
            val preferences = preferencesManager.userPreferencesFlow.first()
            val searchMode = when {
                mode == HybridSearchMode.AUTO -> {
                    when (preferences.searchMode) {
                        "KEYWORD" -> HybridSearchEngine.SearchMode.KEYWORD_ONLY
                        "SEMANTIC" -> HybridSearchEngine.SearchMode.SEMANTIC_ONLY
                        else -> HybridSearchEngine.SearchMode.HYBRID
                    }
                }
                mode == HybridSearchMode.KEYWORD -> HybridSearchEngine.SearchMode.KEYWORD_ONLY
                mode == HybridSearchMode.SEMANTIC -> HybridSearchEngine.SearchMode.SEMANTIC_ONLY
                else -> HybridSearchEngine.SearchMode.HYBRID
            }
            
            // 1. Always perform keyword search in keyword field (most reliable)
            val keywordSearchResults = knowledgeBaseManager.searchRulesByKeyword(query, limit = 20).map { rule ->
                KnowledgeBaseManager.HybridSearchResult(
                    rule = rule,
                    keywordScore = 1.0f,
                    semanticScore = 0f,
                    combinedScore = 1.0f,
                    matchedText = null,
                    matchType = com.csbaby.kefu.infrastructure.search.SearchType.KEYWORD
                )
            }
            Log.d(TAG, "Keyword search found ${keywordSearchResults.size} results")
            
            // 2. If semantic search enabled, also do hybrid/semantic search
            val semanticSearchResults = if (preferences.semanticSearchEnabled) {
                try {
                    knowledgeBaseManager.hybridSearch(
                        query = query,
                        context = null,
                        mode = searchMode
                    ).also { results ->
                        Log.d(TAG, "Semantic/hybrid search found ${results.size} results")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Semantic search failed, falling back to keyword-only", e)
                    emptyList()
                }
            } else {
                emptyList()
            }
            
            // 3. Also search in reply templates, categories, and target names
            val templateMatchedRules = try {
                knowledgeBaseManager.getAllRules()
                    .first()
                    .filter { rule ->
                        // Skip rules that already matched via keyword search
                        keywordSearchResults.none { it.rule.id == rule.id } &&
                        (rule.replyTemplate.contains(query, ignoreCase = true) ||
                            rule.category.contains(query, ignoreCase = true) ||
                            rule.targetNames.any { it.contains(query, ignoreCase = true) })
                    }
                    .map { rule ->
                        KnowledgeBaseManager.HybridSearchResult(
                            rule = rule,
                            keywordScore = 0f,
                            semanticScore = 0f,
                            combinedScore = 0.6f,  // 回复模板匹配给一个中等评分
                            matchedText = null,
                            matchType = com.csbaby.kefu.infrastructure.search.SearchType.KEYWORD
                        )
                    }
                    .also { results ->
                        Log.d(TAG, "Template/category/target search found ${results.size} results")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Template search failed", e)
                emptyList()
            }

            // 4. Merge all results, deduplicate and sort by score
            val allResults = (keywordSearchResults + semanticSearchResults + templateMatchedRules)
                .distinctBy { it.rule.id }
                .sortedByDescending { it.combinedScore }
                .take(15)

            Log.d(TAG, "Total merged results: ${allResults.size}")

            allResults.map { result ->
                FloatingWindowService.KnowledgeRuleItem(
                    keyword = result.rule.keyword,
                    replyTemplate = result.rule.replyTemplate,
                    matchType = result.rule.matchType.name,
                    targetNames = result.rule.targetNames,
                    // Additional metadata for display
                    score = result.combinedScore,
                    matchTypeLabel = when (result.matchType) {
                        com.csbaby.kefu.infrastructure.search.SearchType.KEYWORD -> "关键词匹配"
                        com.csbaby.kefu.infrastructure.search.SearchType.SEMANTIC -> "语义匹配"
                        com.csbaby.kefu.infrastructure.search.SearchType.HYBRID -> "混合匹配"
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to search knowledge rules", e)
            emptyList()
        }
    }
    
    /**
     * Hybrid search mode enum.
     */
    enum class HybridSearchMode {
        AUTO,       // Use preference setting
        KEYWORD,    // Force keyword search
        SEMANTIC,   // Force semantic search
        HYBRID      // Force hybrid search
    }

    private fun extractPropertyName(message: MessageMonitor.MonitoredMessage): String? {
        if (message.packageName != PreferencesManager.BAIJUYI_PACKAGE) {
            return null
        }

        val propertyName = listOf(message.conversationTitle, message.title)
            .map { it?.trim().orEmpty() }
            .firstOrNull { it.isNotBlank() }

        Log.d(
            TAG,
            "Baijuyi property resolved. property=${previewForLog(propertyName)}, conversation=${previewForLog(message.conversationTitle)}, title=${previewForLog(message.title)}"
        )

        return propertyName
    }

    private fun shouldSkipMessage(message: MessageMonitor.MonitoredMessage): Boolean {
        // 常见的占位通知文本模式
        val plainPlaceholderPatterns = listOf(
            "给你发送了新消息",
            "向你发送了一条消息",
            "向你发送了新消息",
            "发来一条消息",
            "发来新消息",
            "新消息来了",
            "有新消息"
        )

        val content = message.content.trim()
        val title = message.title.trim()
        val conversation = message.conversationTitle?.trim().orEmpty()

        // 如果内容是空或太短（小于2个字）
        if (content.length < 2) {
            return true
        }

        // 检查纯文本占位符：使用 contains 匹配，避免变体漏过
        for (pattern in plainPlaceholderPatterns) {
            if (content.contains(pattern) || title.contains(pattern) || conversation.contains(pattern)) {
                return true
            }
        }

        // 检查媒体占位符：精确匹配 [图片] 单独出现的情况，或作为内容的一部分
        val fullContentRegex = Regex("^\\[图片\\]$|^\\[表情\\]$|^\\[语音\\]$|^\\[视频\\]$|^\\[文件\\]$")
        val partialMediaRegex = Regex("\\[图片\\]|\\[表情\\]|\\[语音\\]|\\[视频\\]|\\[文件\\]")

        if (content.matches(fullContentRegex)) {
            return true
        }

        // 如果内容仅包含媒体占位符（如 "发送了 [图片] "），也应该跳过
        if (content.contains(partialMediaRegex)) {
            // 进一步检查：只有媒体占位符，无实质文字
            val withoutMedia = content
                .replace(partialMediaRegex, "")
                .replace(Regex("[\\s,.，、。]+"), "")
                .trim()
            if (withoutMedia.isEmpty()) {
                return true
            }
        }

        return false
    }

    private fun isBaijuyiMessage(message: MessageMonitor.MonitoredMessage): Boolean {
        return message.packageName == PreferencesManager.BAIJUYI_PACKAGE
    }

    private fun previewForLog(value: String?): String {
        val sanitized = value.orEmpty()
            .replace("\n", "\\n")
            .trim()
        return if (sanitized.length <= 120) sanitized else sanitized.take(117) + "..."
    }

    companion object {
        private const val TAG = "ReplyOrchestrator"
        private const val FEATURE_REPLY_GENERATION = "reply_generation"
        private const val OPTIMIZATION_CHECK_INTERVAL = 24L * 60L * 60L * 1000L // 1 day
    }
}


