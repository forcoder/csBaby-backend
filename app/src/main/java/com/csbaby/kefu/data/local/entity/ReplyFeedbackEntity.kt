package com.csbaby.kefu.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reply_feedback",
    foreignKeys = [
        ForeignKey(
            entity = ReplyHistoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["replyHistoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["replyHistoryId"]),
        Index(value = ["variantId"])
    ]
)
data class ReplyFeedbackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val replyHistoryId: Long,
    val variantId: Long? = null,
    val userAction: String, // ACCEPTED / MODIFIED / REJECTED
    val modifiedPart: String? = null,
    val userRating: Int? = null,
    val feedbackText: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
