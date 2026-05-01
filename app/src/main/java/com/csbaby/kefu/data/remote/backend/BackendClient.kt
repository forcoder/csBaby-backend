package com.csbaby.kefu.data.remote.backend

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import timber.log.Timber
import java.io.IOException

/**
 * 后端 API 客户端封装
 * 处理认证、重试、错误转换
 */
class BackendClient(
    private val api: BackendApi,
    private val context: Context
) {
    /**
     * 注册设备并保存凭证
     */
    suspend fun register(appVersion: String): Result<AuthResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.register(
                    RegisterRequest(
                        name = "Android Device",
                        platform = "android",
                        app_version = appVersion
                    )
                )
                if (response.isSuccessful && response.body() != null) {
                    val auth = response.body()!!
                    TokenInterceptor.saveCredentials(context, auth.deviceId, auth.token)
                    Timber.d("Backend registered: deviceId=${auth.deviceId}")
                    Result.success(auth)
                } else {
                    val error = parseError(response)
                    Timber.e("Backend register failed: $error")
                    Result.failure(Exception(error))
                }
            } catch (e: IOException) {
                Timber.e(e, "Backend register network error")
                Result.failure(Exception("网络连接失败，请检查网络"))
            } catch (e: Exception) {
                Timber.e(e, "Backend register error")
                Result.failure(e)
            }
        }
    }

    /**
     * 心跳保活
     */
    suspend fun heartbeat(): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.heartbeat()
                Result.success(response.isSuccessful)
            } catch (e: Exception) {
                Timber.w(e, "Heartbeat failed")
                Result.success(false)
            }
        }
    }

    // ========== 知识库规则 ==========

    suspend fun getRules(): Result<List<RuleDto>> = apiCall { api.getRules() }

    suspend fun createRule(rule: RuleDto): Result<RuleDto> = apiCall { api.createRule(rule) }

    suspend fun updateRule(id: Long, rule: RuleDto): Result<RuleDto> = apiCall { api.updateRule(id, rule) }

    suspend fun deleteRule(id: Long): Result<DeleteResponse> = apiCall { api.deleteRule(id) }

    suspend fun batchImportRules(rules: List<RuleDto>, mode: String): Result<BatchImportResponse> =
        apiCall { api.batchImportRules(BatchImportRequest(rules, mode)) }

    // ========== 模型配置 ==========

    suspend fun getModels(): Result<List<ModelConfigDto>> = apiCall { api.getModels() }

    suspend fun createModel(model: ModelConfigDto): Result<ModelConfigDto> = apiCall { api.createModel(model) }

    suspend fun updateModel(id: Long, model: ModelConfigDto): Result<ModelConfigDto> = apiCall { api.updateModel(id, model) }

    suspend fun deleteModel(id: Long): Result<DeleteResponse> = apiCall { api.deleteModel(id) }

    suspend fun testModel(id: Long): Result<ModelTestResponse> = apiCall { api.testModel(id) }

    // ========== AI 生成 ==========

    suspend fun generateReply(message: String, context: Map<String, String>, style: Map<String, Float>): Result<GenerateResponse> =
        apiCall { api.generateReply(GenerateRequest(message, context, style)) }

    suspend fun chat(messages: List<ChatMessage>): Result<ChatResponse> =
        apiCall { api.chat(ChatRequest(messages)) }

    // ========== 历史记录 ==========

    suspend fun getHistory(limit: Int = 50, offset: Int = 0): Result<HistoryResponse> =
        apiCall { api.getHistory(limit, offset) }

    suspend fun recordHistory(history: HistoryDto): Result<HistoryDto> =
        apiCall { api.recordHistory(history) }

    // ========== 反馈 ==========

    suspend fun submitFeedback(feedback: FeedbackDto): Result<FeedbackDto> =
        apiCall { api.submitFeedback(feedback) }

    // ========== 优化 ==========

    suspend fun getOptimizeMetrics(days: Int = 7): Result<List<OptimizeMetricDto>> =
        apiCall { api.getOptimizeMetrics(days) }

    suspend fun analyzeOptimize(): Result<OptimizeAnalysisResponse> =
        apiCall { api.analyzeOptimize() }

    // ========== 备份 ==========

    suspend fun exportBackup(): Result<BackupDto> = apiCall { api.exportBackup() }

    suspend fun restoreBackup(backup: BackupDto): Result<RestoreResponse> =
        apiCall { api.restoreBackup(backup) }

    // ========== 工具 ==========

    suspend fun healthCheck(): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.healthCheck()
                Result.success(response.isSuccessful)
            } catch (e: Exception) {
                Result.success(false)
            }
        }
    }

    // ========== 内部方法 ==========

    private suspend fun <T> apiCall(call: suspend () -> Response<T>): Result<T> {
        return withContext(Dispatchers.IO) {
            try {
                val response = call()
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    val error = parseError(response)
                    Timber.e("API error: $error")
                    Result.failure(Exception(error))
                }
            } catch (e: IOException) {
                Timber.e(e, "Network error")
                Result.failure(Exception("网络连接失败: ${e.message}"))
            } catch (e: Exception) {
                Timber.e(e, "API call error")
                Result.failure(e)
            }
        }
    }

    private fun parseError(response: Response<*>): String {
        return try {
            val errorBody = response.errorBody()?.string() ?: ""
            if (errorBody.isNotEmpty()) {
                try {
                    val json = org.json.JSONObject(errorBody)
                    json.optString("error", "未知错误 (${response.code()})")
                } catch (e: Exception) {
                    "请求失败 (${response.code()}): $errorBody"
                }
            } else {
                "请求失败 (${response.code()})"
            }
        } catch (e: Exception) {
            "请求失败 (${response.code()})"
        }
    }
}
