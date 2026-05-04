package com.csbaby.kefu.data.local.dao

import androidx.room.*
import com.csbaby.kefu.data.local.entity.ReplyFeedbackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReplyFeedbackDao {
    @Query("SELECT * FROM reply_feedback WHERE replyHistoryId = :replyHistoryId AND tenantId = :tenantId LIMIT 1")
    suspend fun getByReplyHistoryId(replyHistoryId: Long, tenantId: String): ReplyFeedbackEntity?

    @Query("SELECT * FROM reply_feedback WHERE variantId = :variantId AND tenantId = :tenantId ORDER BY createdAt DESC")
    fun getByVariantId(variantId: Long, tenantId: String): Flow<List<ReplyFeedbackEntity>>

    @Query("SELECT * FROM reply_feedback WHERE createdAt BETWEEN :startDate AND :endDate AND tenantId = :tenantId ORDER BY createdAt DESC")
    suspend fun getFeedbacksInDateRange(startDate: Long, endDate: Long, tenantId: String): List<ReplyFeedbackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ReplyFeedbackEntity): Long

    @Update
    suspend fun update(entity: ReplyFeedbackEntity)

    @Query("SELECT COUNT(*) FROM reply_feedback WHERE variantId = :variantId AND userAction = :userAction AND tenantId = :tenantId")
    suspend fun getCountByAction(variantId: Long, userAction: String, tenantId: String): Int
}
