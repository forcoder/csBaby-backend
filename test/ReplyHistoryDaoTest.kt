package com.csbaby.kefu.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.csbaby.kefu.data.local.AppDatabase
import com.csbaby.kefu.data.local.entity.ReplyHistoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Comprehensive Room database tests for ReplyHistoryDao.
 */
@RunWith(AndroidJUnit4::class)
class ReplyHistoryDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ReplyHistoryDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.replyHistoryDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun `insert and retrieve reply history`() = runTest {
        // Given
        val reply = ReplyHistoryEntity(
            sourceApp = "com.example.app",
            originalMessage = "Hello, how are you?",
            generatedReply = "I'm doing well, thank you!",
            finalReply = "I'm doing well, thank you for asking!",
            ruleMatchedId = 1L,
            modelUsedId = 2L,
            styleApplied = true,
            sendTime = System.currentTimeMillis()
        )

        // When
        val insertedId = dao.insertReply(reply)
        val retrievedReply = dao.getReplyById(insertedId)

        // Then
        assertEquals(1L, insertedId)
        assertNotNull(retrievedReply)
        assertEquals("com.example.app", retrievedReply!!.sourceApp)
        assertEquals("Hello, how are you?", retrievedReply.originalMessage)
        assertTrue(retrievedReply.styleApplied)
    }

    @Test
    fun `get recent replies ordered by time`() = runTest {
        // Given
        val reply1 = createReply("com.example.app1", "Message 1", 1000)
        val reply2 = createReply("com.example.app2", "Message 2", 2000)
        val reply3 = createReply("com.example.app3", "Message 3", 3000)

        dao.insertReply(reply1)
        dao.insertReply(reply2)
        dao.insertReply(reply3)

        // When
        val recentRepliesFlow = dao.getRecentReplies(3)
        val recentReplies = recentRepliesFlow.first()

        // Then
        assertEquals(3, recentReplies.size)
        // Should be ordered by sendTime DESC
        assertEquals("Message 3", recentReplies[0].originalMessage)
        assertEquals("Message 2", recentReplies[1].originalMessage)
        assertEquals("Message 1", recentReplies[2].originalMessage)
    }

    @Test
    fun `get replies by specific app package`() = runTest {
        // Given
        val reply1 = createReply("com.example.app1", "Message 1", 1000)
        val reply2 = createReply("com.example.app2", "Message 2", 2000)
        val reply3 = createReply("com.example.app1", "Message 3", 3000) // Same app

        dao.insertReply(reply1)
        dao.insertReply(reply2)
        dao.insertReply(reply3)

        // When
        val appRepliesFlow = dao.getRepliesByApp("com.example.app1")
        val appReplies = appRepliesFlow.first()

        // Then
        assertEquals(2, appReplies.size)
        assertEquals("com.example.app1", appReplies[0].sourceApp)
        assertEquals("com.example.app1", appReplies[1].sourceApp)
    }

    @Test
    fun `update final reply correctly`() = runTest {
        // Given
        val reply = createReply("com.example.app", "Original message", 1000)
        dao.insertReply(reply)

        val updatedFinalReply = "This is the final version of the reply"

        // When
        dao.updateFinalReply(reply.id, updatedFinalReply)
        val updatedReply = dao.getReplyById(reply.id)

        // Then
        assertNotNull(updatedReply)
        assertEquals(updatedFinalReply, updatedReply!!.finalReply)
        assertTrue(updatedReply.modified)
    }

    @Test
    fun `count total and modified replies`() = runTest {
        // Given
        val reply1 = createReply("com.example.app1", "Message 1", 1000)
        val reply2 = createReply("com.example.app2", "Message 2", 2000)
        val reply3 = createModifiedReply("com.example.app3", "Message 3", 3000)

        dao.insertReply(reply1)
        dao.insertReply(reply2)
        dao.insertReply(reply3)

        // When
        val totalCount = dao.getTotalCount()
        val modifiedCount = dao.getModifiedCount()

        // Then
        assertEquals(3, totalCount)
        assertEquals(1, modifiedCount)
    }

    @Test
    fun `get style applied replies only`() = runTest {
        // Given
        val styleReply = createStyleAppliedReply("com.example.app1", "Styled message", 1000)
        val normalReply = createReply("com.example.app2", "Normal message", 2000)

        dao.insertReply(styleReply)
        dao.insertReply(normalReply)

        // When
        val styleReplies = dao.getStyleAppliedReplies(10)

        // Then
        assertEquals(1, styleReplies.size)
        assertTrue(styleReplies[0].styleApplied)
        assertEquals("Styled message", styleReplies[0].originalMessage)
    }

    @Test
    fun `delete reply by id`() = runTest {
        // Given
        val reply = createReply("com.example.app", "Message to delete", 1000)
        dao.insertReply(reply)

        // When
        dao.deleteById(reply.id)
        val deletedReply = dao.getReplyById(reply.id)

        // Then
        assertNull(deletedReply)
    }

    @Test
    fun `getAllReplies returns complete list`() = runTest {
        // Given
        val reply1 = createReply("com.example.app1", "Message 1", 3000)
        val reply2 = createReply("com.example.app2", "Message 2", 2000)
        val reply3 = createReply("com.example.app3", "Message 3", 1000)

        dao.insertReply(reply1)
        dao.insertReply(reply2)
        dao.insertReply(reply3)

        // When
        val allReplies = dao.getAllReplies()

        // Then
        assertEquals(3, allReplies.size)
        // Should be ordered by sendTime DESC
        assertEquals("Message 1", allReplies[0].originalMessage)
        assertEquals("Message 2", allReplies[1].originalMessage)
        assertEquals("Message 3", allReplies[2].originalMessage)
    }

    @Test
    fun `flow updates when data changes`() = runTest {
        // Given
        val recentRepliesFlow = dao.getRecentReplies()
        val initialReplies = recentRepliesFlow.first()
        assertEquals(0, initialReplies.size)

        val reply = createReply("com.example.app", "New message", 1000)
        dao.insertReply(reply)

        // When
        val updatedReplies = recentRepliesFlow.first()

        // Then
        assertEquals(1, updatedReplies.size)
        assertEquals("New message", updatedReplies[0].originalMessage)
    }

    // Helper functions
    private fun createReply(
        sourceApp: String,
        message: String,
        timestamp: Long
    ): ReplyHistoryEntity {
        return ReplyHistoryEntity(
            sourceApp = sourceApp,
            originalMessage = message,
            generatedReply = "Generated: $message",
            finalReply = "Final: $message",
            sendTime = timestamp
        )
    }

    private fun createModifiedReply(
        sourceApp: String,
        message: String,
        timestamp: Long
    ): ReplyHistoryEntity {
        return ReplyHistoryEntity(
            sourceApp = sourceApp,
            originalMessage = message,
            generatedReply = "Generated: $message",
            finalReply = "Modified final: $message",
            modified = true,
            sendTime = timestamp
        )
    }

    private fun createStyleAppliedReply(
        sourceApp: String,
        message: String,
        timestamp: Long
    ): ReplyHistoryEntity {
        return ReplyHistoryEntity(
            sourceApp = sourceApp,
            originalMessage = message,
            generatedReply = "Generated: $message",
            finalReply = "Styled final: $message",
            styleApplied = true,
            sendTime = timestamp
        )
    }
}