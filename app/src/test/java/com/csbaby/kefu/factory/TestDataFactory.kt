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
        enabled: Boolean = true,
        targetType: RuleTargetType = RuleTargetType.ALL
    ) = keywordRule(
        id = id,
        keyword = keyword,
        matchType = MatchType.CONTAINS,
        replyTemplate = replyTemplate,
        priority = priority,
        enabled = enabled,
        targetType = targetType
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
        targetNames: List<String> = listOf("海景别墅", "山景公寓")
    ) = keywordRule(
        id = id,
        keyword = keyword,
        replyTemplate = replyTemplate,
        targetType = RuleTargetType.PROPERTY,
        targetNames = targetNames
    )

    fun contactRule(
        id: Long = 6L,
        keyword: String = "订单",
        replyTemplate: String = "您的订单已处理",
        targetNames: List<String> = listOf("张三")
    ) = keywordRule(
        id = id,
        keyword = keyword,
        replyTemplate = replyTemplate,
        targetType = RuleTargetType.CONTACT,
        targetNames = targetNames
    )

    fun groupRule(
        id: Long = 7L,
        keyword: String = "团购",
        replyTemplate: String = "团购详情",
        targetNames: List<String> = listOf("业主群")
    ) = keywordRule(
        id = id,
        keyword = keyword,
        replyTemplate = replyTemplate,
        targetType = RuleTargetType.GROUP,
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
        isDefault: Boolean = false,
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

    fun openAIModel(
        id: Long = 1L,
        isDefault: Boolean = true,
        isEnabled: Boolean = true,
        lastUsed: Long = System.currentTimeMillis()
    ) = aiModelConfig(
        id = id, modelType = ModelType.OPENAI, modelName = "GPT-3.5",
        model = "gpt-3.5-turbo", isDefault = isDefault,
        isEnabled = isEnabled, lastUsed = lastUsed
    )

    fun claudeModel(
        id: Long = 2L,
        isDefault: Boolean = false,
        isEnabled: Boolean = true,
        lastUsed: Long = System.currentTimeMillis()
    ) = aiModelConfig(
        id = id, modelType = ModelType.CLAUDE, modelName = "Claude Haiku",
        model = "claude-3-haiku-20240307",
        apiEndpoint = "https://api.anthropic.com/v1/messages",
        isDefault = isDefault, isEnabled = isEnabled, lastUsed = lastUsed
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
        containsRule(id = 4L, keyword = "通用", targetType = RuleTargetType.ALL)
    )
}
