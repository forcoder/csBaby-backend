package com.csbaby.kefu.data.local.dao

import androidx.room.*
import com.csbaby.kefu.data.local.entity.ReplyHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReplyHistoryDao {
    @Query("SELECT * FROM reply_history WHERE tenantId = :tenantId ORDER BY sendTime DESC LIMIT :limit")
    fun getRecentReplies(tenantId: String, limit: Int = 20): Flow<List<ReplyHistoryEntity>>

    @Query("SELECT * FROM reply_history WHERE sourceApp = :appPackage AND tenantId = :tenantId ORDER BY sendTime DESC LIMIT :limit")
    fun getRepliesByApp(appPackage: String, tenantId: String, limit: Int = 20): Flow<List<ReplyHistoryEntity>>

    @Query("SELECT * FROM reply_history WHERE id = :id AND tenantId = :tenantId")
    suspend fun getReplyById(id: Long, tenantId: String): ReplyHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReply(reply: ReplyHistoryEntity): Long

    @Update
    suspend fun updateReply(reply: ReplyHistoryEntity)

    @Query("UPDATE reply_history SET finalReply = :finalReply, modified = 1 WHERE id = :id AND tenantId = :tenantId")
    suspend fun updateFinalReply(id: Long, tenantId: String, finalReply: String)

    @Delete
    suspend fun deleteReply(reply: ReplyHistoryEntity)

    @Query("DELETE FROM reply_history WHERE id = :id AND tenantId = :tenantId")
    suspend fun deleteById(id: Long, tenantId: String)

    @Query("SELECT COUNT(*) FROM reply_history WHERE tenantId = :tenantId")
    suspend fun getTotalCount(tenantId: String): Int

    @Query("SELECT COUNT(*) FROM reply_history WHERE sendTime >= :startOfDay AND tenantId = :tenantId")
    suspend fun getTodayCount(tenantId: String, startOfDay: Long): Int

    @Query("SELECT COUNT(*) FROM reply_history WHERE modified = 1 AND tenantId = :tenantId")
    suspend fun getModifiedCount(tenantId: String): Int

    @Query("SELECT * FROM reply_history WHERE styleApplied = 1 AND tenantId = :tenantId ORDER BY sendTime DESC LIMIT :limit")
    suspend fun getStyleAppliedReplies(tenantId: String, limit: Int = 100): List<ReplyHistoryEntity>

    @Query("SELECT * FROM reply_history WHERE tenantId = :tenantId ORDER BY sendTime DESC")
    suspend fun getAllReplies(tenantId: String): List<ReplyHistoryEntity>
}
