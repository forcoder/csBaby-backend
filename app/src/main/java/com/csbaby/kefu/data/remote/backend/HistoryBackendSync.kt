package com.csbaby.kefu.data.remote.backend

import com.csbaby.kefu.data.local.AuthManager
import com.csbaby.kefu.data.local.dao.ReplyHistoryDao
import com.csbaby.kefu.data.local.entity.ReplyHistoryEntity
import com.csbaby.kefu.domain.model.ReplyHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 回复历史后端同步辅助类
 */
class HistoryBackendSync(
    private val backendClient: BackendClient,
    private val replyHistoryDao: ReplyHistoryDao,
    private val authManager: AuthManager
) {
    /**
     * 从后端拉取最近历史并合并到本地
     */
    suspend fun pullFromBackend(limit: Int = 100): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val tenantId = authManager.getTenantId() ?: ""
            val result = backendClient.getHistory(limit, 0)
            result.fold(
                onSuccess = { historyResponse ->
                    val entities = historyResponse.items.map { it.toEntity().copy(tenantId = tenantId) }
                    var inserted = 0
                    entities.forEach { entity ->
                        val existing = replyHistoryDao.getReplyById(entity.id, tenantId)
                        if (existing == null) {
                            replyHistoryDao.insertReply(entity)
                            inserted++
                        }
                    }
                    Timber.d("Pulled $inserted new history records from backend (${entities.size} total)")
                    Result.success(inserted)
                },
                onFailure = { e ->
                    Timber.w(e, "Failed to pull history from backend")
                    Result.failure(e)
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Error pulling history from backend")
            Result.failure(e)
        }
    }

    /**
     * 推送单条回复记录到后端
     */
    suspend fun pushRecord(reply: ReplyHistory): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val dto = HistoryDto(
                originalMessage = reply.originalMessage,
                replyContent = reply.finalReply.ifBlank { reply.generatedReply },
                source = "ai",
                modelUsed = reply.modelUsedId?.toString() ?: "",
                confidence = 0f,
                responseTimeMs = 0,
                platform = reply.sourceApp,
                customerName = "",
                houseName = ""
            )
            backendClient.recordHistory(dto)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.w(e, "Failed to push history to backend")
            Result.failure(e)
        }
    }

    private fun HistoryDto.toEntity() = ReplyHistoryEntity(
        id = id,
        sourceApp = platform,          // backend.platform -> local.sourceApp
        originalMessage = originalMessage,
        generatedReply = replyContent,  // backend.replyContent -> local.generatedReply
        finalReply = replyContent,      // 后端不区分 generated/final，用同一个值
        ruleMatchedId = null,           // 后端无此字段
        modelUsedId = null,             // 后端用 modelUsed 字符串，本地用 ID
        styleApplied = false,
        sendTime = try { createdAt.toLong() } catch (_: Exception) { System.currentTimeMillis() },
        modified = false,
        featureKey = null,
        variantId = null
    )
}
