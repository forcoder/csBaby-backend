package com.csbaby.kefu.data.repository

import com.csbaby.kefu.data.local.EntityMapper.toDomain
import com.csbaby.kefu.data.local.EntityMapper.toEntity
import com.csbaby.kefu.data.local.dao.ReplyHistoryDao
import com.csbaby.kefu.data.remote.CsbabyApiService
import com.csbaby.kefu.data.remote.DeviceManager
import com.csbaby.kefu.data.remote.dto.toDomain
import com.csbaby.kefu.data.remote.dto.toDto
import com.csbaby.kefu.domain.model.ReplyHistory
import com.csbaby.kefu.domain.repository.ReplyHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReplyHistoryRepositoryImpl @Inject constructor(
    private val replyHistoryDao: ReplyHistoryDao,
    private val apiService: CsbabyApiService,
    private val deviceManager: DeviceManager
) : ReplyHistoryRepository {

    override fun getRecentReplies(limit: Int): Flow<List<ReplyHistory>> {
        return replyHistoryDao.getRecentReplies(limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getRepliesByApp(appPackage: String, limit: Int): Flow<List<ReplyHistory>> {
        return replyHistoryDao.getRepliesByApp(appPackage, limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getReplyById(id: Long): ReplyHistory? {
        return replyHistoryDao.getReplyById(id)?.toDomain()
    }

    override suspend fun insertReply(reply: ReplyHistory): Long {
        val localId = replyHistoryDao.insertReply(reply.toEntity())
        try {
            deviceManager.ensureRegistered()
            apiService.recordHistory(reply.toDto())
        } catch (e: Exception) {
            Timber.w(e, "Failed to sync history to server")
        }
        return localId
    }

    override suspend fun updateFinalReply(id: Long, finalReply: String) {
        replyHistoryDao.updateFinalReply(id, finalReply)
    }

    override suspend fun deleteReply(id: Long) {
        replyHistoryDao.deleteById(id)
    }

    override suspend fun getStyleAppliedReplies(limit: Int): List<ReplyHistory> {
        return replyHistoryDao.getStyleAppliedReplies(limit).map { it.toDomain() }
    }

    override suspend fun getTotalCount(): Int = replyHistoryDao.getTotalCount()

    override suspend fun getModifiedCount(): Int = replyHistoryDao.getModifiedCount()

    /**
     * 从服务器同步最近的回复历史到本地缓存
     */
    suspend fun syncFromServer(limit: Int = 200): Result<Int> {
        return try {
            deviceManager.ensureRegistered()
            val response = apiService.getHistory(limit = limit, offset = 0)

            var count = 0
            response.items.forEach { dto ->
                try {
                    val domain = dto.toDomain()
                    replyHistoryDao.insertReply(domain.toEntity())
                    count++
                } catch (e: Exception) {
                    Timber.e(e, "Failed to import history from server")
                }
            }
            Timber.i("Synced $count history entries from server")
            Result.success(count)
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync history from server")
            Result.failure(e)
        }
    }

    suspend fun forceRefresh(limit: Int = 200): Result<Int> = syncFromServer(limit)
}
