package com.csbaby.kefu.data.repository

import com.csbaby.kefu.data.local.AuthManager
import com.csbaby.kefu.data.local.EntityMapper.toDomain
import com.csbaby.kefu.data.local.EntityMapper.toEntity
import com.csbaby.kefu.data.local.dao.ReplyHistoryDao
import com.csbaby.kefu.data.remote.backend.HistoryBackendSync
import com.csbaby.kefu.domain.model.ReplyHistory
import com.csbaby.kefu.domain.repository.ReplyHistoryRepository
import com.csbaby.kefu.infrastructure.network.NetworkMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 回复历史 Repository — 后端优先架构
 *
 * 读取策略：网络可用时优先从后端获取，失败时降级到本地
 * 写入策略：网络可用时先写后端成功后更新本地，离线时写本地待同步
 */
@Singleton
class ReplyHistoryRepositoryImpl @Inject constructor(
    private val replyHistoryDao: ReplyHistoryDao,
    private val historyBackendSync: HistoryBackendSync,
    private val networkMonitor: NetworkMonitor,
    private val authManager: AuthManager
) : ReplyHistoryRepository {

    override fun getRecentReplies(limit: Int): Flow<List<ReplyHistory>> {
        val tenantId = authManager.getTenantId() ?: ""
        return replyHistoryDao.getRecentReplies(tenantId, limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getRepliesByApp(appPackage: String, limit: Int): Flow<List<ReplyHistory>> {
        val tenantId = authManager.getTenantId() ?: ""
        return replyHistoryDao.getRepliesByApp(appPackage, tenantId, limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getReplyById(id: Long): ReplyHistory? {
        val tenantId = authManager.getTenantId() ?: ""
        return replyHistoryDao.getReplyById(id, tenantId)?.toDomain()
    }

    override suspend fun insertReply(reply: ReplyHistory): Long {
        if (networkMonitor.isNetworkAvailable) {
            try {
                val result = withTimeoutOrNull(BACKEND_TIMEOUT_MS) {
                    historyBackendSync.pushRecord(reply)
                }
                if (result?.isSuccess == true) {
                    val id = replyHistoryDao.insertReply(reply.toEntity())
                    Timber.d("insertReply: synced to backend first, local id=$id")
                    return id
                }
            } catch (e: Exception) {
                Timber.w(e, "insertReply: backend push failed, caching locally")
            }
        }

        val id = replyHistoryDao.insertReply(reply.toEntity())
        Timber.d("insertReply: cached locally id=$id, will sync later")
        return id
    }

    override suspend fun updateFinalReply(id: Long, finalReply: String) {
        val tenantId = authManager.getTenantId() ?: ""
        replyHistoryDao.updateFinalReply(id, tenantId, finalReply)
        // 历史更新不需要实时同步到后端，由 SyncWorker 批量处理
    }

    override suspend fun deleteReply(id: Long) {
        val tenantId = authManager.getTenantId() ?: ""
        replyHistoryDao.deleteById(id, tenantId)
    }

    override suspend fun getStyleAppliedReplies(limit: Int): List<ReplyHistory> {
        val tenantId = authManager.getTenantId() ?: ""
        return replyHistoryDao.getStyleAppliedReplies(tenantId, limit).map { it.toDomain() }
    }

    override suspend fun getTotalCount(): Int {
        val tenantId = authManager.getTenantId() ?: ""
        return replyHistoryDao.getTotalCount(tenantId)
    }

    override suspend fun getTodayCount(startOfDay: Long): Int {
        val tenantId = authManager.getTenantId() ?: ""
        return replyHistoryDao.getTodayCount(tenantId, startOfDay)
    }

    override suspend fun getModifiedCount(): Int {
        val tenantId = authManager.getTenantId() ?: ""
        return replyHistoryDao.getModifiedCount(tenantId)
    }

    companion object {
        private const val BACKEND_TIMEOUT_MS = 10_000L
    }
}
