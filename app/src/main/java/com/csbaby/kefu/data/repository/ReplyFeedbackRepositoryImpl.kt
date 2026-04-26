package com.csbaby.kefu.data.repository

import com.csbaby.kefu.data.local.EntityMapper.toDomain
import com.csbaby.kefu.data.local.EntityMapper.toEntity
import com.csbaby.kefu.data.local.dao.ReplyFeedbackDao
import com.csbaby.kefu.domain.model.FeedbackAction
import com.csbaby.kefu.domain.model.ReplyFeedback
import com.csbaby.kefu.domain.repository.ReplyFeedbackRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReplyFeedbackRepositoryImpl @Inject constructor(
    private val replyFeedbackDao: ReplyFeedbackDao
) : ReplyFeedbackRepository {

    override suspend fun getFeedbackByReplyHistoryId(replyHistoryId: Long): ReplyFeedback? {
        return replyFeedbackDao.getByReplyHistoryId(replyHistoryId)?.toDomain()
    }

    override fun getFeedbacksByVariantId(variantId: Long): Flow<List<ReplyFeedback>> {
        return replyFeedbackDao.getByVariantId(variantId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getFeedbacksInDateRange(startDate: Long, endDate: Long): List<ReplyFeedback> {
        return replyFeedbackDao.getFeedbacksInDateRange(startDate, endDate).map { it.toDomain() }
    }

    override suspend fun insertFeedback(feedback: ReplyFeedback): Long {
        return replyFeedbackDao.insert(feedback.toEntity())
    }

    override suspend fun updateFeedback(feedback: ReplyFeedback) {
        replyFeedbackDao.update(feedback.toEntity())
    }

    override suspend fun getCountByAction(variantId: Long, action: FeedbackAction): Int {
        return replyFeedbackDao.getCountByAction(variantId, action.name)
    }
}
