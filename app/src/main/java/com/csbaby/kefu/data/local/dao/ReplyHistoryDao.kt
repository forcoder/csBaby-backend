package com.csbaby.kefu.data.local.dao

import androidx.room.*
import com.csbaby.kefu.data.local.entity.ReplyHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReplyHistoryDao {
    @Query("SELECT * FROM reply_history ORDER BY sendTime DESC LIMIT :limit")
    fun getRecentReplies(limit: Int = 20): Flow<List<ReplyHistoryEntity>>

    @Query("SELECT * FROM reply_history WHERE sourceApp = :appPackage ORDER BY sendTime DESC LIMIT :limit")
    fun getRepliesByApp(appPackage: String, limit: Int = 20): Flow<List<ReplyHistoryEntity>>

    @Query("SELECT * FROM reply_history WHERE id = :id")
    suspend fun getReplyById(id: Long): ReplyHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReply(reply: ReplyHistoryEntity): Long

    @Update
    suspend fun updateReply(reply: ReplyHistoryEntity)

    @Query("UPDATE reply_history SET finalReply = :finalReply, modified = 1 WHERE id = :id")
    suspend fun updateFinalReply(id: Long, finalReply: String)

    @Delete
    suspend fun deleteReply(reply: ReplyHistoryEntity)

    @Query("DELETE FROM reply_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM reply_history")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM reply_history WHERE modified = 1")
    suspend fun getModifiedCount(): Int

    @Query("SELECT * FROM reply_history WHERE styleApplied = 1 ORDER BY sendTime DESC LIMIT :limit")
    suspend fun getStyleAppliedReplies(limit: Int = 100): List<ReplyHistoryEntity>

    @Query("SELECT * FROM reply_history ORDER BY sendTime DESC")
    suspend fun getAllReplies(): List<ReplyHistoryEntity>

    // 新增的优化查询方法

    /**
     * 分页获取最近回复（避免内存溢出）
     */
    @Query("SELECT * FROM reply_history ORDER BY sendTime DESC LIMIT :limit OFFSET :offset")
    suspend fun getRecentRepliesPaged(limit: Int, offset: Int): List<ReplyHistoryEntity>

    /**
     * 按时间范围查询回复
     */
    @Query("SELECT * FROM reply_history WHERE sendTime >= :startTime AND sendTime <= :endTime ORDER BY sendTime DESC")
    fun getRepliesByTimeRange(startTime: Long, endTime: Long): Flow<List<ReplyHistoryEntity>>

    /**
     * 按应用包名和时间范围查询
     */
    @Query("SELECT * FROM reply_history WHERE sourceApp = :appPackage AND sendTime BETWEEN :startTime AND :endTime ORDER BY sendTime DESC")
    fun getRepliesByAppAndTimeRange(appPackage: String, startTime: Long, endTime: Long): Flow<List<ReplyHistoryEntity>>

    /**
     * 获取统计数据
     */
    @Query("SELECT COUNT(*) as total, SUM(CASE WHEN modified = 1 THEN 1 ELSE 0 END) as modified, SUM(CASE WHEN styleApplied = 1 THEN 1 ELSE 0 END) as styled FROM reply_history")
    suspend fun getReplyStatistics(): ReplyStatistics

    /**
     * 批量插入回复记录
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepliesBatch(replies: List<ReplyHistoryEntity>): List<Long>

    /**
     * 批量删除旧回复记录（按时间）
     */
    @Query("DELETE FROM reply_history WHERE sendTime < :cutoffTime")
    suspend fun deleteOldReplies(cutoffTime: Long): Int

    /**
     * 清理修改过的回复
     */
    @Query("UPDATE reply_history SET modified = 0 WHERE modified = 1")
    suspend fun markAllAsUnmodified()
}
