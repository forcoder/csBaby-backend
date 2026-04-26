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
    @ColumnInfo(defaultValue = "NULL")
    val variantId: Long? = null,
    val userAction: String, // ACCEPTED / MODIFIED / REJECTED
    @ColumnInfo(defaultValue = "NULL")
    val modifiedPart: String? = null,
    @ColumnInfo(defaultValue = "NULL")
    val userRating: Int? = null,
    @ColumnInfo(defaultValue = "NULL")
    val feedbackText: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
