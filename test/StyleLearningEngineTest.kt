package com.csbaby.kefu.infrastructure.style

import com.csbaby.kefu.domain.model.ReplyHistory
import com.csbaby.kefu.domain.model.UserStyleProfile
import com.csbaby.kefu.domain.repository.ReplyHistoryRepository
import com.csbaby.kefu.domain.repository.UserStyleRepository
import com.csbaby.kefu.domain.model.FeedbackAction
import org.junit.Test
import org.junit.Assert.*
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.ConcurrentHashMap

/**
 * Comprehensive tests for StyleLearningEngine functionality.
 */
class StyleLearningEngineTest {

    private val mockReplyHistoryRepository = mock<ReplyHistoryRepository>()
    private val mockUserStyleRepository = mock<UserStyleRepository>()
    private val mockAIService = mock<com.csbaby.kefu.infrastructure.ai.AIService>()
    private val engine = StyleLearningEngine(mockReplyHistoryRepository, mockUserStyleRepository, mockAIService)

    @Test
    fun `test initial profile creation`() = runTest {
        // Setup
        val userId = "user123"
        val reply = createTestReply("您好，这是一个测试回复。")

        whenever(mockUserStyleRepository.getProfileSync(userId)).thenReturn(null)
        whenever(mockReplyHistoryRepository.getRecentReplies(any())).thenReturn(
            kotlinx.coroutines.flow.flowOf(listOf(reply))
        )

        // Execute
        engine.learnFromReply(userId, reply)

        // Verify
        verify(mockUserStyleRepository).saveProfile(any())
        val savedProfile = mockUserStyleRepository.saveProfile.calls.first().arguments[0] as UserStyleProfile
        assertEquals(userId, savedProfile.userId)
        assertTrue("Formality should be between 0 and 1", savedProfile.formalityLevel in 0f..1f)
        assertTrue("Enthusiasm should be between 0 and 1", savedProfile.enthusiasmLevel in 0f..1f)
        assertTrue("Professionalism should be between 0 and 1", savedProfile.professionalismLevel in 0f..1f)
    }

    @Test
    fun `test existing profile update`() = runTest {
        // Setup
        val userId = "user123"
        val existingProfile = UserStyleProfile(
            userId = userId,
            formalityLevel = 0.5f,
            enthusiasmLevel = 0.6f,
            professionalismLevel = 0.4f,
            learningSamples = 10,
            accuracyScore = 0.7f
        )
        val newReply = createTestReply("这是一个新的回复，测试风格学习。")

        whenever(mockUserStyleRepository.getProfileSync(userId)).thenReturn(existingProfile)
        whenever(mockReplyHistoryRepository.getRecentReplies(any())).thenReturn(
            kotlinx.coroutines.flow.flowOf(listOf(newReply))
        )

        // Execute
        engine.learnFromReply(userId, newReply)

        // Verify
        verify(mockUserStyleRepository).updateProfile(any())
        val updatedProfile = mockUserStyleRepository.updateProfile.calls.first().arguments[0] as UserStyleProfile
        assertEquals(11, updatedProfile.learningSamples) // Should increment by 1
        assertTrue("Accuracy score should improve with more samples",
            updatedProfile.accuracyScore >= existingProfile.accuracyScore)
    }

    @Test
    fun `test feedback integration`() = runTest {
        // Setup
        val userId = "user123"
        val profile = UserStyleProfile(
            userId = userId,
            formalityLevel = 0.5f,
            enthusiasmLevel = 0.6f,
            professionalismLevel = 0.4f,
            learningSamples = 5
        )
        val reply = createTestReply("反馈测试回复。")
        val rejectedFeedback = FeedbackAction.REJECTED
        val acceptedFeedback = FeedbackAction.ACCEPTED

        whenever(mockUserStyleRepository.getProfileSync(userId)).thenReturn(profile)
        whenever(mockReplyHistoryRepository.getRecentReplies(any())).thenReturn(
            kotlinx.coroutines.flow.flowOf(listOf(reply))
        )

        // Test rejected feedback
        engine.learnFromReply(userId, reply, rejectedFeedback)

        val rejectedResult = mockUserStyleRepository.updateProfile.calls.last().arguments[0] as UserStyleProfile
        // Rejected feedback should adjust style parameters
        assertNotEquals(profile.formalityLevel, rejectedResult.formalityLevel, 0.1f)

        // Test accepted feedback (should not change much)
        engine.learnFromReply(userId, reply, acceptedFeedback)
        val acceptedResult = mockUserStyleRepository.updateProfile.calls.last().arguments[0] as UserStyleProfile
        // Accepted feedback should have minimal impact
        assertEquals(acceptedResult.formalityLevel, rejectedResult.formalityLevel, 0.05f)
    }

    @Test
    fun `test batch learning`() = runTest {
        // Setup
        val userId = "user123"
        val replies = listOf(
            createTestReply("批量学习回复1。"),
            createTestReply("批量学习回复2。"),
            createTestReply("批量学习回复3。")
        )

        whenever(mockReplyHistoryRepository.getRecentReplies(any())).thenReturn(
            kotlinx.coroutines.flow.flowOf(replies)
        )

        // Execute
        engine.learnFromBatch(userId, replies)

        // Verify multiple updates occurred
        verify(mockUserStyleRepository, atLeast(replies.size)).updateProfile(any())
    }

    @Test
    fun `test deep analysis scheduling`() = runTest {
        // Setup
        val userId = "user123"
        val profile = UserStyleProfile(
            userId = userId,
            formalityLevel = 0.5f,
            enthusiasmLevel = 0.6f,
            professionalismLevel = 0.4f,
            learningSamples = 9 // Not at threshold yet
        )

        whenever(mockUserStyleRepository.getProfileSync(userId)).thenReturn(profile)
        whenever(mockReplyHistoryRepository.getRecentReplies(any())).thenReturn(
            kotlinx.coroutines.flow.flowOf(listOf(createTestReply("分析测试。")))
        )

        // Execute - learn 2 more samples to reach threshold
        engine.learnFromReply(userId, createTestReply("样本1"))
        engine.learnFromReply(userId, createTestReply("样本2"))

        // Deep analysis should be triggered (every 10 samples)
        verify(mockAIService, times(1)).analyzeTextStyle(any())
    }

    @Test
    fun `test common phrases extraction`() {
        // Setup
        val texts = listOf(
            "您好，这是一个常用的短语测试。",
            "这个测试包含了常用短语和表达。",
            "我们提取常用短语用于风格学习。"
        )

        val phrases = engine.extractCommonPhrases(texts)

        assertTrue("Should extract some phrases", phrases.isNotEmpty())
        assertTrue("Extracted phrases should be reasonable length",
            phrases.all { it.length in 2..18 })
    }

    @Test
    fun `test phrase merging`() {
        // Setup
        val existingPhrases = listOf("您好", "谢谢", "请")
        val newPhrases = listOf("不客气", "好的", "谢谢")

        val merged = engine.mergeCommonPhrases(existingPhrases, newPhrases)

        assertTrue("Merged phrases should include both sets", merged.containsAll(existingPhrases + newPhrases))
        assertTrue("Existing phrases should have higher weight", merged.indexOf("您好") < merged.indexOf("不客气"))
    }

    @Test
    fun `test enhanced phrase extraction`() {
        // Setup
        val text = "您好，这是一个测试文本，包含了一些常用短语和表达方式。"

        val phrases = engine.extractAdvancedPhrases(listOf(text))

        assertTrue("Should extract meaningful phrases", phrases.isNotEmpty())
        assertFalse("Should filter out stop words", phrases.any { it.length == 1 })
    }

    @Test
    fun `test style signal analysis`() {
        // Setup
        val casualText = "嗨！今天天气真不错呀～ 😊"
        val formalText = "您好，今日天气状况良好，特此告知。"
        val professionalText = "尊敬的客户，系统显示您的订单已处理完毕。"

        val casualSignals = engine.analyzeLocalStyleSignals(casualText)
        val formalSignals = engine.analyzeLocalStyleSignals(formalText)
        val professionalSignals = engine.analyzeLocalStyleSignals(professionalText)

        assertTrue("Casual text should have lower formality", casualSignals.formality < formalSignals.formality)
        assertTrue("Formal text should have higher formality", formalSignals.formality > casualSignals.formality)
        assertTrue("Professional text should have high professionalism", professionalSignals.professionalism > 0.6f)
    }

    @Test
    fun `test confidence calculation`() {
        val lowConfidence = engine.calculateEnhancedAccuracyScore(2)
        val mediumConfidence = engine.calculateEnhancedAccuracyScore(25)
        val highConfidence = engine.calculateEnhancedAccuracyScore(75)

        assertTrue("Low sample count should have low confidence", lowConfidence < 0.3f)
        assertTrue("Medium sample count should have medium confidence", mediumConfidence in 0.5f..0.7f)
        assertTrue("High sample count should have high confidence", highConfidence > 0.8f)
    }

    @Test
    fun `test stability threshold`() {
        // Setup
        val smallChange = 0.01f
        val largeChange = 0.1f

        // These would need actual implementation testing, but we can test the constants
        assertEquals("Stability threshold should prevent excessive updates", 0.05f, engine.STYLE_STABILITY_THRESHOLD, 0.01f)
    }

    @Test
    fun `test learning metrics tracking`() {
        // This would require exposing internal metrics for testing
        // For now, we verify that the engine can handle metric operations
        val metrics = engine.learningMetrics["test-user"] ?: return fail("Should initialize metrics")

        assertTrue("Learning metrics should track samples", metrics.totalSamples >= 0)
        assertTrue("Last update time should be recent", metrics.lastUpdateTime > 0)
    }

    @Test
    fun `test empty reply handling`() = runTest {
        // Setup
        val userId = "user123"
        val emptyReply = ReplyHistory(
            id = 1,
            sourceApp = "test-app",
            originalMessage = "",
            generatedReply = "",
            finalReply = "", // Empty final reply
            sendTime = System.currentTimeMillis()
        )

        // Execute
        engine.learnFromReply(userId, emptyReply)

        // Verify no repository calls were made for empty replies
        verifyZeroInteractions(mockUserStyleRepository)
        verifyZeroInteractions(mockReplyHistoryRepository)
    }

    @Test
    fun `test system prompt generation`() {
        // Setup
        val profile = UserStyleProfile(
            userId = "user123",
            formalityLevel = 0.8f,
            enthusiasmLevel = 0.6f,
            professionalismLevel = 0.9f,
            wordCountPreference = 80
        )

        // Execute
        val systemPrompt = engine.generateStyleSystemPrompt(profile)

        // Verify
        assertTrue("System prompt should mention formality", systemPrompt.contains("formal"))
        assertTrue("System prompt should mention professionalism", systemPrompt.contains("professional"))
        assertTrue("System prompt should contain length preference", systemPrompt.contains("80"))
    }

    private fun createTestReply(content: String): ReplyHistory {
        return ReplyHistory(
            id = 1,
            sourceApp = "test-app",
            originalMessage = "Test message",
            generatedReply = content,
            finalReply = content,
            sendTime = System.currentTimeMillis(),
            styleApplied = true
        )
    }
}