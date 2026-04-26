package com.csbaby.kefu.data.local.dao

import androidx.room.*
import com.csbaby.kefu.data.local.entity.ReplyFeedbackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReplyFeedbackDao {
    @Query("SELECT * FROM reply_feedback WHERE replyHistoryId = :replyHistoryId LIMIT 1")
    suspend fun getByReplyHistoryId(replyHistoryId: Long): ReplyFeedbackEntity?

    @Query("SELECT * FROM reply_feedback WHERE variantId = :variantId ORDER BY createdAt DESC")
    fun getByVariantId(variantId: Long): Flow<List<ReplyFeedbackEntity>>

    @Query("SELECT * FROM reply_feedback WHERE createdAt BETWEEN :startDate AND :endDate ORDER BY createdAt DESC")
    suspend fun getFeedbacksInDateRange(startDate: Long, endDate: Long): List<ReplyFeedbackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ReplyFeedbackEntity): Long

    @Update
    suspend fun update(entity: ReplyFeedbackEntity)

    @Query("SELECT COUNT(*) FROM reply_feedback WHERE variantId = :variantId AND userAction = :userAction")
    suspend fun getCountByAction(variantId: Long, userAction: String): Int
}
