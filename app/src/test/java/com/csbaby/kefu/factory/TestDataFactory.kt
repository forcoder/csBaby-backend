package com.csbaby.kefu.factory

import com.csbaby.kefu.domain.model.*

object TestDataFactory {

    // ========== KeywordRule ==========

    fun keywordRule(
        id: Long = 1L,
        keyword: String = "价格",
        matchType: MatchType = MatchType.CONTAINS,
        replyTemplate: String = "您好，价格是{price}元",
        category: String = "售前咨询",
        applicableScenarios: List<Long> = emptyList(),
        targetType: RuleTargetType = RuleTargetType.ALL,
        targetNames: List<String> = emptyList(),
        priority: Int = 0,
        enabled: Boolean = true,
        createdAt: Long = System.currentTimeMillis(),
        updatedAt: Long = System.currentTimeMillis()
    ) = KeywordRule(
        id = id,
        keyword = keyword,
        matchType = matchType,
        replyTemplate = replyTemplate,
        category = category,
        applicableScenarios = applicableScenarios,
        targetType = targetType,
        targetNames = targetNames,
        priority = priority,
        enabled = enabled,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    fun exactRule(
        id: Long = 1L,
        keyword: String = "你好",
        replyTemplate: String = "您好！请问有什么可以帮您？",
        priority: Int = 0,
        enabled: Boolean = true
    ) = keywordRule(
        id = id,
        keyword = keyword,
        matchType = MatchType.EXACT,
        replyTemplate = replyTemplate,
        priority = priority,
        enabled = enabled
    )

    fun containsRule(
        id: Long = 2L,
        keyword: String = "价格,多少钱,费用",
        replyTemplate: String = "我们的价格是{price}元",
        priority: Int = 5,
        enabled: Boolean = true
    ) = keywordRule(
        id = id,
        keyword = keyword,
        matchType = MatchType.CONTAINS,
        replyTemplate = replyTemplate,
        priority = priority,
        enabled = enabled
    )

    fun regexRule(
        id: Long = 3L,
        keyword: String = "\\d+元",
        replyTemplate: String = "好的，已为您查询",
        priority: Int = 3,
        enabled: Boolean = true
    ) = keywordRule(
        id = id,
        keyword = keyword,
        matchType = MatchType.REGEX,
        replyTemplate = replyTemplate,
        priority = priority,
        enabled = enabled
    )

    fun disabledRule(
        id: Long = 4L,
        keyword: String = "过期",
        replyTemplate: String = "已过期",
        enabled: Boolean = false
    ) = keywordRule(id = id, keyword = keyword, replyTemplate = replyTemplate, enabled = enabled)

    fun propertyRule(
        id: Long = 5L,
        keyword: String = "房源",
        replyTemplate: String = "房源信息如下",
        targetType: RuleTargetType.PROPERTY,
        targetNames: List<String> = listOf("海景别墅", "山景公寓")
    ) = keywordRule(
        id = id,
        keyword = keyword,
        replyTemplate = replyTemplate,
        targetType = targetType,
        targetNames = targetNames
    )

    fun contactRule(
        id: Long = 6L,
        keyword: String = "订单",
        replyTemplate: String = "您的订单已处理",
        targetType: RuleTargetType.CONTACT,
        targetNames: List<String> = listOf("张三")
    ) = keywordRule(
        id = id,
        keyword = keyword,
        replyTemplate = replyTemplate,
        targetType = targetType,
        targetNames = targetNames
    )

    fun groupRule(
        id: Long = 7L,
        keyword: String = "团购",
        replyTemplate: String = "团购详情",
        targetType: RuleTargetType.GROUP,
        targetNames: List<String> = listOf("业主群")
    ) = keywordRule(
        id = id,
        keyword = keyword,
        replyTemplate = replyTemplate,
        targetType = targetType,
        targetNames = targetNames
    )

    // ========== AIModelConfig ==========

    fun aiModelConfig(
        id: Long = 1L,
        modelType: ModelType = ModelType.OPENAI,
        modelName: String = "GPT-3.5",
        model: String = "gpt-3.5-turbo",
        apiKey: String = "sk-test-key",
        apiEndpoint: String = "https://api.openai.com/v1/chat/completions",
        temperature: Float = 0.7f,
        maxTokens: Int = 1000,
        isDefault: Boolean = true,
        isEnabled: Boolean = true,
        monthlyCost: Double = 0.0,
        lastUsed: Long = System.currentTimeMillis(),
        createdAt: Long = System.currentTimeMillis()
    ) = AIModelConfig(
        id = id,
        modelType = modelType,
        modelName = modelName,
        model = model,
        apiKey = apiKey,
        apiEndpoint = apiEndpoint,
        temperature = temperature,
        maxTokens = maxTokens,
        isDefault = isDefault,
        isEnabled = isEnabled,
        monthlyCost = monthlyCost,
        lastUsed = lastUsed,
        createdAt = createdAt
    )

    fun openAIModel(id: Long = 1L, isDefault: Boolean = true) = aiModelConfig(
        id = id, modelType = ModelType.OPENAI, modelName = "GPT-3.5",
        model = "gpt-3.5-turbo", isDefault = isDefault
    )

    fun claudeModel(id: Long = 2L) = aiModelConfig(
        id = id, modelType = ModelType.CLAUDE, modelName = "Claude Haiku",
        model = "claude-3-haiku-20240307",
        apiEndpoint = "https://api.anthropic.com/v1/messages"
    )

    fun zhipuModel(id: Long = 3L) = aiModelConfig(
        id = id, modelType = ModelType.ZHIPU, modelName = "GLM-4",
        model = "glm-4", apiEndpoint = "https://open.bigmodel.cn/api/paas/v4/chat/completions"
    )

    fun tongyiModel(id: Long = 4L) = aiModelConfig(
        id = id, modelType = ModelType.TONGYI, modelName = "Qwen Turbo",
        model = "qwen-turbo", apiEndpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation"
    )

    fun customModel(id: Long = 5L) = aiModelConfig(
        id = id, modelType = ModelType.CUSTOM, modelName = "Custom",
        model = "custom-model", apiEndpoint = "https://custom-api.example.com/v1/chat/completions"
    )

    fun disabledModel(id: Long = 99L) = aiModelConfig(
        id = id, modelType = ModelType.OPENAI, modelName = "Disabled",
        model = "gpt-4", isEnabled = false
    )

    fun exceededModel(id: Long = 100L) = aiModelConfig(
        id = id, modelType = ModelType.OPENAI, modelName = "Exceeded",
        model = "gpt-4", monthlyCost = 15.0
    )

    // ========== UserStyleProfile ==========

    fun userStyleProfile(
        userId: String = "user_001",
        formalityLevel: Float = 0.5f,
        enthusiasmLevel: Float = 0.5f,
        professionalismLevel: Float = 0.5f,
        wordCountPreference: Int = 50,
        commonPhrases: List<String> = emptyList(),
        avoidPhrases: List<String> = emptyList(),
        learningSamples: Int = 0,
        accuracyScore: Float = 0.0f,
        lastTrained: Long = System.currentTimeMillis(),
        createdAt: Long = System.currentTimeMillis()
    ) = UserStyleProfile(
        userId = userId,
        formalityLevel = formalityLevel,
        enthusiasmLevel = enthusiasmLevel,
        professionalismLevel = professionalismLevel,
        wordCountPreference = wordCountPreference,
        commonPhrases = commonPhrases,
        avoidPhrases = avoidPhrases,
        learningSamples = learningSamples,
        accuracyScore = accuracyScore,
        lastTrained = lastTrained,
        createdAt = createdAt
    )

    // ========== ReplyContext ==========

    fun replyContext(
        appPackage: String = "com.example.app",
        scenarioId: String? = null,
        conversationTitle: String? = "张三",
        propertyName: String? = null,
        isGroupConversation: Boolean? = false,
        userId: String = "user_001"
    ) = ReplyContext(
        appPackage = appPackage,
        scenarioId = scenarioId,
        conversationTitle = conversationTitle,
        propertyName = propertyName,
        isGroupConversation = isGroupConversation,
        userId = userId
    )

    fun baijuyiContext(
        propertyName: String? = "海景别墅",
        isGroup: Boolean = false
    ) = replyContext(
        appPackage = "com.myhostex.hostexapp",
        conversationTitle = propertyName,
        propertyName = propertyName,
        isGroupConversation = isGroup
    )

    // ========== ReplyResult ==========

    fun replyResult(
        reply: String = "感谢您的留言",
        source: ReplySource = ReplySource.RULE_MATCH,
        confidence: Float = 0.8f,
        ruleId: Long? = null,
        modelId: Long? = null,
        variantId: Long? = null
    ) = ReplyResult(
        reply = reply,
        source = source,
        confidence = confidence,
        ruleId = ruleId,
        modelId = modelId,
        variantId = variantId
    )

    // ========== ReplyHistory ==========

    fun replyHistory(
        id: Long = 0L,
        sourceApp: String = "com.example.app",
        originalMessage: String = "请问价格是多少",
        generatedReply: String = "您好，价格是100元",
        finalReply: String = "您好，价格是100元",
        ruleMatchedId: Long? = 1L,
        modelUsedId: Long? = null,
        styleApplied: Boolean = false,
        sendTime: Long = System.currentTimeMillis(),
        modified: Boolean = false,
        featureKey: String? = null,
        variantId: Long? = null
    ) = ReplyHistory(
        id = id,
        sourceApp = sourceApp,
        originalMessage = originalMessage,
        generatedReply = generatedReply,
        finalReply = finalReply,
        ruleMatchedId = ruleMatchedId,
        modelUsedId = modelUsedId,
        styleApplied = styleApplied,
        sendTime = sendTime,
        modified = modified,
        featureKey = featureKey,
        variantId = variantId
    )

    // ========== MonitoredMessage ==========

    fun monitoredMessage(
        packageName: String = "com.example.app",
        appName: String = "测试应用",
        title: String = "张三",
        content: String = "你好",
        conversationTitle: String? = "张三",
        isGroupConversation: Boolean? = false,
        timestamp: Long = System.currentTimeMillis()
    ) = com.csbaby.kefu.infrastructure.notification.MessageMonitor.MonitoredMessage(
        packageName = packageName,
        appName = appName,
        title = title,
        content = content,
        conversationTitle = conversationTitle,
        isGroupConversation = isGroupConversation,
        timestamp = timestamp
    )

    // ========== OtaUpdate ==========

    fun otaUpdate(
        versionCode: Int = 100,
        versionName: String = "1.2.0",
        downloadUrl: String = "https://example.com/app-v1.2.0.apk",
        fileSize: Long = 0L,
        md5: String = "abc123def456",
        releaseNotes: String = "修复了一些问题",
        releaseDate: String = "2024-01-01",
        isForceUpdate: Boolean = false,
        minRequiredVersion: Int = 0,
        objectKey: String? = null,
        uploader: String? = "admin",
        uploadTime: String? = null,
        channel: String? = "default",
        downloadCount: Long = 0L,
        signature: String? = null
    ) = com.csbaby.kefu.data.model.OtaUpdate(
        versionCode = versionCode,
        versionName = versionName,
        downloadUrl = downloadUrl,
        fileSize = fileSize,
        md5 = md5,
        releaseNotes = releaseNotes,
        releaseDate = releaseDate,
        isForceUpdate = isForceUpdate,
        minRequiredVersion = minRequiredVersion,
        objectKey = objectKey,
        uploader = uploader,
        uploadTime = uploadTime,
        channel = channel,
        downloadCount = downloadCount,
        signature = signature
    )

    // ========== AI Response JSON ==========

    fun openAIResponse(content: String = "您好，请问有什么可以帮您？") = """
        {
            "id": "chatcmpl-test",
            "object": "chat.completion",
            "created": 1700000000,
            "model": "gpt-3.5-turbo",
            "choices": [
                {
                    "index": 0,
                    "message": {
                        "role": "assistant",
                        "content": "$content"
                    },
                    "finish_reason": "stop"
                }
            ],
            "usage": {
                "prompt_tokens": 10,
                "completion_tokens": 20,
                "total_tokens": 30
            }
        }
    """.trimIndent()

    fun claudeResponse(content: String = "您好，请问有什么可以帮您？") = """
        {
            "id": "msg_test",
            "type": "message",
            "role": "assistant",
            "model": "claude-3-haiku-20240307",
            "content": [
                {
                    "type": "text",
                    "text": "$content"
                }
            ],
            "stop_reason": "end_turn",
            "usage": {
                "input_tokens": 10,
                "output_tokens": 20
            }
        }
    """.trimIndent()

    fun nvidiaResponse(content: String = "您好，请问有什么可以帮您？") = """
        {
            "id": "cmpl-test",
            "object": "chat.completion",
            "choices": [
                {
                    "index": 0,
                    "message": {
                        "role": "assistant",
                        "content": "$content"
                    }
                }
            ]
        }
    """.trimIndent()

    fun styleAnalysisResponse(
        formality: Float = 0.6f,
        enthusiasm: Float = 0.7f,
        professionalism: Float = 0.8f,
        avgWordsPerSentence: Float = 15f
    ) = """
        {
            "formality": $formality,
            "enthusiasm": $enthusiasm,
            "professionalism": $professionalism,
            "avgWordsPerSentence": $avgWordsPerSentence
        }
    """.trimIndent()

    // ========== Import Data ==========

    fun jsonImportSingleRule() = """
        [{"keyword":"价格","replyTemplate":"价格是100元","category":"售前咨询","matchType":"CONTAINS","enabled":true,"priority":0}]
    """.trimIndent()

    fun jsonImportWithRulesWrapper() = """
        {"rules":[{"keyword":"价格","replyTemplate":"价格是100元","category":"售前咨询","matchType":"CONTAINS","enabled":true,"priority":0}],"version":2}
    """.trimIndent()

    fun jsonImportRules() = """
        [{"keyword":"价格","replyTemplate":"价格是{price}元","category":"售前咨询","matchType":"CONTAINS","enabled":true,"priority":5,"targetType":"ALL","targetNames":[]}]
    """.trimIndent()

    fun csvImportWithHeader() = """
        keyword,reply_template,category,match_type,enabled,priority
        价格,价格是100元,售前咨询,CONTAINS,true,0
    """.trimIndent()

    fun csvImportChineseHeader() = """
        规则标题,回复内容,规则分类,触发类型,状态,优先级
        价格咨询,价格是100元,售前咨询,关键词回复,启用,0
    """.trimIndent()

    fun csvImportLegacy() = """
        价格,CONTAINS,价格是100元,售前咨询,ALL,,0,true
    """.trimIndent()

    // ========== Rules Collection ==========

    fun emptyRuleList() = emptyList<KeywordRule>()

    fun singleRuleList() = listOf(containsRule())

    fun multipleRulesList() = listOf(
        containsRule(id = 1L, keyword = "价格,多少钱", priority = 5),
        exactRule(id = 2L, keyword = "你好", priority = 3),
        regexRule(id = 3L, keyword = "\\d+元", priority = 4),
        disabledRule(id = 4L)
    )

    fun rulesWithTargetsList() = listOf(
        propertyRule(id = 1L, targetNames = listOf("海景别墅", "山景公寓")),
        contactRule(id = 2L, targetNames = listOf("张三")),
        groupRule(id = 3L, targetNames = listOf("业主群")),
        containsRule(id = 4L, keyword = "通用")
    )

    fun allCategories() = listOf("售前咨询", "售后服务", "投诉处理", "通用问题", "物流配送", "支付问题", "退换货")
}
