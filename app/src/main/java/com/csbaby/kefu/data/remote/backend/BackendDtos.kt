package com.csbaby.kefu.data.remote.backend

import com.google.gson.annotations.SerializedName

// ========== 认证 ==========

data class RegisterRequest(
    val name: String = "",
    val platform: String = "android",
    val app_version: String = ""
)

data class AuthResponse(
    @SerializedName("device_id") val deviceId: String = "",
    val token: String = "",
    @SerializedName("expires_in") val expiresIn: Long = 0
)

data class HeartbeatResponse(
    val status: String = ""
)

// ========== 知识库规则 ==========

data class RuleDto(
    val id: Long = 0,
    val keyword: String = "",
    @SerializedName("match_type") val matchType: String = "CONTAINS",
    @SerializedName("reply_template") val replyTemplate: String = "",
    val category: String = "",
    @SerializedName("target_type") val targetType: String = "ALL",
    @SerializedName("target_names") val targetNames: String = "[]",
    val priority: Int = 0,
    val enabled: Int = 1,
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("updated_at") val updatedAt: String = ""
)

data class BatchImportRequest(
    val rules: List<RuleDto> = emptyList(),
    val mode: String = "append"  // append or override
)

data class BatchImportResponse(
    val status: String = "",
    val imported: Int = 0,
    val total: Int = 0
)

data class DeleteResponse(
    val status: String = "",
    val id: Long = 0
)

// ========== 模型配置 ==========

data class ModelConfigDto(
    val id: Long = 0,
    val name: String = "",
    @SerializedName("model_type") val modelType: String = "OPENAI",
    val model: String = "",
    @SerializedName("api_key") val apiKey: String = "",
    @SerializedName("api_endpoint") val apiEndpoint: String = "",
    val temperature: Float = 0.7f,
    @SerializedName("max_tokens") val maxTokens: Int = 2000,
    @SerializedName("is_default") val isDefault: Int = 0,
    val enabled: Int = 1,
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("updated_at") val updatedAt: String = ""
)

data class ModelTestResponse(
    val success: Boolean = false,
    val model: String = "",
    val tokens: Int = 0,
    val error: String = ""
)

// ========== AI 生成 ==========

data class GenerateRequest(
    val message: String = "",
    val context: Map<String, String> = emptyMap(),
    val style: Map<String, Float> = emptyMap()
)

data class GenerateResponse(
    val reply: String = "",
    val source: String = "ai",
    @SerializedName("rule_id") val ruleId: Long? = null,
    @SerializedName("model_used") val modelUsed: String = "",
    val confidence: Float = 0f,
    @SerializedName("response_time_ms") val responseTimeMs: Long = 0,
    @SerializedName("tokens_used") val tokensUsed: Int = 0
)

data class ChatRequest(
    val messages: List<ChatMessage> = emptyList()
)

data class ChatMessage(
    val role: String = "",
    val content: String = ""
)

data class ChatResponse(
    val reply: String = "",
    @SerializedName("model_used") val modelUsed: String = "",
    @SerializedName("tokens_used") val tokensUsed: Int = 0,
    @SerializedName("response_time_ms") val responseTimeMs: Long = 0
)

// ========== 回复历史 ==========

data class HistoryDto(
    val id: Long = 0,
    @SerializedName("original_message") val originalMessage: String = "",
    @SerializedName("reply_content") val replyContent: String = "",
    val source: String = "ai",
    @SerializedName("model_used") val modelUsed: String = "",
    val confidence: Float = 0f,
    @SerializedName("response_time_ms") val responseTimeMs: Long = 0,
    val platform: String = "",
    @SerializedName("customer_name") val customerName: String = "",
    @SerializedName("house_name") val houseName: String = "",
    @SerializedName("created_at") val createdAt: String = ""
)

data class HistoryResponse(
    val items: List<HistoryDto> = emptyList(),
    val total: Int = 0,
    val limit: Int = 50,
    val offset: Int = 0
)

// ========== 反馈 ==========

data class FeedbackDto(
    val id: Long = 0,
    @SerializedName("reply_history_id") val replyHistoryId: Long? = null,
    val action: String = "",
    @SerializedName("modified_text") val modifiedText: String = "",
    val rating: Int = 0,
    val comment: String = "",
    @SerializedName("created_at") val createdAt: String = ""
)

// ========== 优化 ==========

data class OptimizeMetricDto(
    val id: Long = 0,
    val date: String = "",
    @SerializedName("total_generated") val totalGenerated: Int = 0,
    @SerializedName("total_accepted") val totalAccepted: Int = 0,
    @SerializedName("total_modified") val totalModified: Int = 0,
    @SerializedName("total_rejected") val totalRejected: Int = 0,
    @SerializedName("avg_confidence") val avgConfidence: Float = 0f,
    @SerializedName("avg_response_time_ms") val avgResponseTimeMs: Int = 0
)

data class OptimizeAnalysisResponse(
    val status: String = "",
    val message: String = "",
    @SerializedName("period_days") val periodDays: Int = 0,
    @SerializedName("total_generated") val totalGenerated: Int = 0,
    @SerializedName("total_accepted") val totalAccepted: Int = 0,
    @SerializedName("total_modified") val totalModified: Int = 0,
    @SerializedName("total_rejected") val totalRejected: Int = 0,
    @SerializedName("accept_rate") val acceptRate: Float = 0f,
    @SerializedName("modify_rate") val modifyRate: Float = 0f,
    @SerializedName("reject_rate") val rejectRate: Float = 0f,
    val suggestions: List<String> = emptyList()
)

// ========== 备份 ==========

data class BackupDto(
    val version: Int = 1,
    @SerializedName("device_id") val deviceId: String = "",
    val rules: List<RuleDto> = emptyList(),
    val models: List<ModelConfigDto> = emptyList(),
    val history: List<HistoryDto> = emptyList(),
    val feedback: List<FeedbackDto> = emptyList(),
    val metrics: List<OptimizeMetricDto> = emptyList()
)

data class RestoreResponse(
    val status: String = "",
    val restored: Map<String, Int> = emptyMap()
)

// ========== 健康检查 ==========

data class HealthResponse(
    val status: String = "",
    val service: String = ""
)
