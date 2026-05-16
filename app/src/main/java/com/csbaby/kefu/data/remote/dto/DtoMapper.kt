package com.csbaby.kefu.data.remote.dto

import com.csbaby.kefu.domain.model.*

// ========== Rule DTO ↔ Domain ==========

fun RuleDto.toDomain(): KeywordRule = KeywordRule(
    id = id,
    keyword = keyword,
    matchType = enumValueOrDefault(matchType, MatchType.CONTAINS),
    replyTemplate = replyTemplate,
    category = category,
    applicableScenarios = emptyList(), // Server doesn't manage scenarios
    targetType = enumValueOrDefault(targetType, RuleTargetType.ALL),
    targetNames = parseTargetNames(targetNames),
    priority = priority,
    enabled = enabled,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun KeywordRule.toDto(): RuleDto = RuleDto(
    id = id.toInt(),
    deviceId = "",
    keyword = keyword,
    matchType = matchType.name,
    replyTemplate = replyTemplate,
    category = category,
    targetType = targetType.name,
    targetNames = com.google.gson.Gson().toJson(targetNames),
    priority = priority,
    enabled = enabled,
    createdAt = createdAt,
    updatedAt = updatedAt
)

// ========== Model DTO ↔ Domain ==========

fun ModelConfigDto.toDomain(): AIModelConfig = AIModelConfig(
    id = id,
    modelType = enumValueOrDefault(modelType, ModelType.OPENAI),
    modelName = name,
    model = model,
    apiKey = apiKey,
    apiEndpoint = apiEndpoint,
    temperature = temperature.toFloat(),
    maxTokens = maxTokens,
    isDefault = isDefault,
    isEnabled = enabled,
    monthlyCost = 0.0,
    lastUsed = 0L,
    createdAt = createdAt
)

fun AIModelConfig.toDto(): ModelConfigDto = ModelConfigDto(
    id = id.toInt(),
    deviceId = "",
    name = modelName,
    modelType = modelType.name,
    model = model,
    apiKey = apiKey,
    apiEndpoint = apiEndpoint,
    temperature = temperature.toDouble(),
    maxTokens = maxTokens,
    isDefault = isDefault,
    enabled = isEnabled,
    createdAt = createdAt,
    updatedAt = ""
)

// ========== History DTO ↔ Domain ==========

fun HistoryEntryDto.toDomain(): ReplyHistory = ReplyHistory(
    id = id,
    sourceApp = platform,
    originalMessage = originalMessage,
    generatedReply = replyContent,
    finalReply = replyContent,
    ruleMatchedId = if (source == "keyword") 0L else null,
    modelUsedId = null,
    styleApplied = false,
    sendTime = 0L,
    modified = false
)

fun ReplyHistory.toDto(): HistoryEntryDto = HistoryEntryDto(
    id = id.toInt(),
    deviceId = "",
    originalMessage = originalMessage,
    replyContent = generatedReply,
    source = "ai",
    modelUsed = "",
    confidence = 0.0,
    responseTimeMs = 0,
    platform = sourceApp,
    customerName = "",
    houseName = "",
    createdAt = ""
)

// ========== Feedback DTO ↔ Domain ==========

fun FeedbackDto.toDomain(): Pair<String, String> = Pair(action, comment)

// ========== Helpers ==========

private fun parseTargetNames(json: String): List<String> {
    return try {
        if (json.isBlank() || json == "[]") emptyList()
        else com.google.gson.Gson().fromJson(json, object : com.google.gson.reflect.TypeToken<List<String>>() {}.type)
    } catch (e: Exception) {
        emptyList()
    }
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T {
    return runCatching { enumValueOf<T>(value) }.getOrDefault(default)
}
