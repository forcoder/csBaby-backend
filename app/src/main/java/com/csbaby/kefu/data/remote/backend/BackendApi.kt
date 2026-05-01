package com.csbaby.kefu.data.remote.backend

import retrofit2.Response
import retrofit2.http.*

/**
 * csBaby 后端 API 接口
 * 所有需要认证的接口在调用前会自动携带 Authorization: Bearer <token>
 */
interface BackendApi {

    // ========== 认证 ==========

    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<AuthResponse>

    @POST("api/auth/heartbeat")
    suspend fun heartbeat(): Response<HeartbeatResponse>

    // ========== 知识库规则 ==========

    @GET("api/rules")
    suspend fun getRules(): Response<List<RuleDto>>

    @POST("api/rules")
    suspend fun createRule(@Body body: RuleDto): Response<RuleDto>

    @PUT("api/rules/{id}")
    suspend fun updateRule(@Path("id") id: Long, @Body body: RuleDto): Response<RuleDto>

    @DELETE("api/rules/{id}")
    suspend fun deleteRule(@Path("id") id: Long): Response<DeleteResponse>

    @POST("api/rules/batch")
    suspend fun batchImportRules(@Body body: BatchImportRequest): Response<BatchImportResponse>

    // ========== 模型配置 ==========

    @GET("api/models")
    suspend fun getModels(): Response<List<ModelConfigDto>>

    @POST("api/models")
    suspend fun createModel(@Body body: ModelConfigDto): Response<ModelConfigDto>

    @PUT("api/models/{id}")
    suspend fun updateModel(@Path("id") id: Long, @Body body: ModelConfigDto): Response<ModelConfigDto>

    @DELETE("api/models/{id}")
    suspend fun deleteModel(@Path("id") id: Long): Response<DeleteResponse>

    @POST("api/models/{id}/test")
    suspend fun testModel(@Path("id") id: Long): Response<ModelTestResponse>

    // ========== AI 生成 ==========

    @POST("api/ai/generate")
    suspend fun generateReply(@Body body: GenerateRequest): Response<GenerateResponse>

    @POST("api/ai/chat")
    suspend fun chat(@Body body: ChatRequest): Response<ChatResponse>

    // ========== 回复历史 ==========

    @GET("api/history")
    suspend fun getHistory(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<HistoryResponse>

    @POST("api/history")
    suspend fun recordHistory(@Body body: HistoryDto): Response<HistoryDto>

    // ========== 反馈 ==========

    @GET("api/feedback")
    suspend fun getFeedback(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<List<FeedbackDto>>

    @POST("api/feedback")
    suspend fun submitFeedback(@Body body: FeedbackDto): Response<FeedbackDto>

    // ========== 优化 ==========

    @GET("api/optimize/metrics")
    suspend fun getOptimizeMetrics(@Query("days") days: Int = 7): Response<List<OptimizeMetricDto>>

    @POST("api/optimize/analyze")
    suspend fun analyzeOptimize(): Response<OptimizeAnalysisResponse>

    // ========== 备份 ==========

    @GET("api/backup")
    suspend fun exportBackup(): Response<BackupDto>

    @POST("api/backup/restore")
    suspend fun restoreBackup(@Body body: BackupDto): Response<RestoreResponse>

    // ========== 健康检查 ==========

    @GET("health")
    suspend fun healthCheck(): Response<HealthResponse>
}
