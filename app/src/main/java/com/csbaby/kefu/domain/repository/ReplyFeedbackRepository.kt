package com.csbaby.kefu.domain.repository

import com.csbaby.kefu.domain.model.FeedbackAction
import com.csbaby.kefu.domain.model.ReplyFeedback
import kotlinx.coroutines.flow.Flow

interface ReplyFeedbackRepository {
    suspend fun getFeedbackByReplyHistoryId(replyHistoryId: Long): ReplyFeedback?
    fun getFeedbacksByVariantId(variantId: Long): Flow<List<ReplyFeedback>>
    suspend fun getFeedbacksInDateRange(startDate: Long, endDate: Long): List<ReplyFeedback>
    suspend fun insertFeedback(feedback: ReplyFeedback): Long
    suspend fun updateFeedback(feedback: ReplyFeedback)
    suspend fun getCountByAction(variantId: Long, action: FeedbackAction): Int
}
