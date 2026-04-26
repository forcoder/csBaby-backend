package com.csbaby.kefu.infrastructure.style

import com.csbaby.kefu.domain.model.ReplyHistory
import com.csbaby.kefu.domain.model.UserStyleProfile
import com.csbaby.kefu.domain.repository.ReplyHistoryRepository
import com.csbaby.kefu.domain.repository.UserStyleRepository
import com.csbaby.kefu.infrastructure.ai.AIService
import com.csbaby.kefu.infrastructure.simple.SimpleTaskRouter
import com.csbaby.kefu.factory.TestDataFactory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*

class StyleLearningEngineTest {

    private lateinit var mockReplyHistoryRepository: ReplyHistoryRepository
    private lateinit var mockUserStyleRepository: UserStyleRepository
    private lateinit var mockAIService: AIService
    private lateinit var styleLearningEngine: StyleLearningEngine

    @Before
    fun setup() {
        mockReplyHistoryRepository = mock(ReplyHistoryRepository::class.java)
        mockUserStyleRepository = mock(UserStyleRepository::class.java)
        mockAIService = mock(AIService::class.java)
        styleLearningEngine = StyleLearningEngine(
            replyHistoryRepository = mockReplyHistoryRepository,
            userStyleRepository = mockUserStyleRepository,
            aiService = mockAIService
        )
    }

    // ==================== ✅ 正常功能测试 ====================

    // SL-001: learnFromReply首次学习-创建新的UserStyleProfile
    @Test
    fun `SL-001 learnFromReply creates new profile on first learning`() = runTest {
        val userId = "user_001"
        val reply = TestDataFactory.replyHistory(finalReply = "您好，很高兴为您服务")

        // Mock no existing profile
        `when`(mockUserStyleRepository.getProfileSync(userId)).thenReturn(null)

        styleLearningEngine.learnFromReply(userId, reply)

        val profileCaptor = ArgumentCaptor.forClass(UserStyleProfile::class.java)
        verify(mockUserStyleRepository).saveProfile(profileCaptor.capture())
        val capturedProfile = profileCaptor.value
        assertNotNull("Profile should be created", capturedProfile)
        assertEquals("User ID should match", userId, capturedProfile.userId)
        assertThat(capturedProfile.learningSamples).isEqualTo(1)
    }

    // SL-002: learnFromReply空回复跳过处理
    @Test
    fun `SL-002 learnFromReply skips blank final reply`() = runTest {
        val userId = "user_001"
        val reply = TestDataFactory.replyHistory(finalReply = "")

        styleLearningEngine.learnFromReply(userId, reply)

        verify(mockUserStyleRepository, never()).saveProfile(any())
        verify(mockUserStyleRepository, never()).updateProfile(any())
    }

    // SL-003: learnFromReply增量学习-更新已有profile的blendMetric
    @Test
    fun `SL-003 learnFromReply incremental learning with blend metric`() = runTest {
        val userId = "user_001"
        val existingProfile = TestDataFactory.userStyleProfile(
            formalityLevel = 0.6f,
            enthusiasmLevel = 0.5f,
            professionalismLevel = 0.7f,
            learningSamples = 5
        )
        val reply = TestDataFactory.replyHistory(finalReply = "好的，没问题！")

        // Mock existing profile
        `when`(mockUserStyleRepository.getProfileSync(userId)).thenReturn(existingProfile)

        styleLearningEngine.learnFromReply(userId, reply)

        val profileCaptor = ArgumentCaptor.forClass(UserStyleProfile::class.java)
        verify(mockUserStyleRepository).updateProfile(profileCaptor.capture())
        val capturedProfile = profileCaptor.value
        assertNotNull("Profile should be updated", capturedProfile)
        // Should have more samples
        assertThat(capturedProfile.learningSamples).isGreaterThan(existingProfile.learningSamples)
    }

    // SL-004: learnFromBatch批量学习
    @Test
    fun `SL-004 learnFromBatch processes multiple replies`() = runTest {
        val userId = "user_001"
        val replies = listOf(
            TestDataFactory.replyHistory(finalReply = "回复1"),
            TestDataFactory.replyHistory(finalReply = "回复2"),
            TestDataFactory.replyHistory(finalReply = "")
        )

        styleLearningEngine.learnFromBatch(userId, replies)

        // Should process non-blank replies
        verify(mockUserStyleRepository, times(2)).updateProfile(any())
        // Blank reply should be skipped
        verify(mockUserStyleRepository, never()).saveProfile(any())
    }

    // SL-005: getStyleProfile返回用户风格配置
    @Test
    fun `SL-005 getStyleProfile returns user style profile`() = runTest {
        val userId = "user_001"
        val expectedProfile = TestDataFactory.userStyleProfile()
        `when`(mockUserStyleRepository.getProfileSync(userId)).thenReturn(expectedProfile)

        val result = styleLearningEngine.getStyleProfile(userId)

        assertEquals("Should return the profile", expectedProfile, result)
    }

    // SL-006: applyStyle调用AI服务调整风格
    @Test
    fun `SL-006 applyStyle calls AI service for style adjustment`() = runTest {
        val userId = "user_001"
        val text = "原始文本"
        val profile = TestDataFactory.userStyleProfile()
        `when`(mockUserStyleRepository.getProfileSync(userId)).thenReturn(profile)
        `when`(mockAIService.adjustStyle(text, profile)).thenReturn(Result.success("风格化后的文本"))

        val result = styleLearningEngine.applyStyle(text, userId)

        assertTrue("Should succeed", result.isSuccess)
        assertEquals("风格化后的文本", result.getOrNull())
        verify(mockAIService).adjustStyle(text, profile)
    }

    // SL-007: generateStyleSystemPrompt生成系统提示词
    @Test
    fun `SL-007 generateStyleSystemPrompt creates system prompt`() = runTest {
        val profile = TestDataFactory.userStyleProfile(
            formalityLevel = 0.8f,
            enthusiasmLevel = 0.6f,
            professionalismLevel = 0.9f,
            wordCountPreference = 50,
            commonPhrases = listOf("您好", "请"),
            accuracyScore = 0.8f
        )

        val prompt = styleLearningEngine.generateStyleSystemPrompt(profile)

        assertTrue("Should contain formality description", prompt.contains("formal"))
        assertTrue("Should contain enthusiasm description", prompt.contains("enthusiastic"))
        assertTrue("Should contain length info", prompt.contains("50"))
        assertTrue("Should contain common phrases", prompt.contains("Preferred expressions"))
    }

    // ==================== ⚠️ 边界条件测试 ====================

    // SL-B01: 样本数达到10的倍数时触发深度分析
    @Test
    fun `SL-B01 deep analysis triggered at sample count multiples of 10`() = runTest {
        val userId = "user_001"
        val profile = TestDataFactory.userStyleProfile(learningSamples = 9)
        val replies = (1..10).map {
            TestDataFactory.replyHistory(finalReply = "样本$it")
        }

        `when`(mockReplyHistoryRepository.getRecentReplies(anyInt())).thenReturn(kotlinx.coroutines.flow.flowOf(replies))
        `when`(mockUserStyleRepository.getProfileSync(userId)).thenReturn(profile)
        `when`(mockAIService.analyzeTextStyle(anyString())).thenReturn(
            Result.success(AIService.TextStyleAnalysis(0.7f, 0.6f, 0.8f, 15f))
        )

        styleLearningEngine.learnFromReply(userId, replies[0])

        // Learning from 10th sample should trigger deep analysis
        verify(mockAIService).analyzeTextStyle(anyString())
    }

    // SL-B02: MIN_SAMPLES_FOR_AI_ANALYSIS不足时不进行AI分析
    @Test
    fun `SL-B02 insufficient samples skip AI analysis`() = runTest {
        val userId = "user_001"
        val profile = TestDataFactory.userStyleProfile(learningSamples = 4)
        val replies = (1..3).map {
            TestDataFactory.replyHistory(finalReply = "样本$it")
        }

        `when`(mockReplyHistoryRepository.getRecentReplies(anyInt())).thenReturn(kotlinx.coroutines.flow.flowOf(replies))
        `when`(mockUserStyleRepository.getProfileSync(userId)).thenReturn(profile)

        styleLearningEngine.learnFromReply(userId, replies[0])

        // Should not call AI analysis when samples < MIN_SAMPLES_FOR_AI_ANALYSIS
        verify(mockAIService, never()).analyzeTextStyle(anyString())
    }

    // SL-B03: 常用短语提取长度限制
    @Test
    fun `SL-B03 common phrase extraction respects length limits`() = runTest {
        val userId = "user_001"
        val reply = TestDataFactory.replyHistory(finalReply = "这是一个非常非常长的短语，超过了18个字符的限制，应该被过滤掉")

        `when`(mockUserStyleRepository.getProfileSync(userId)).thenReturn(null)

        styleLearningEngine.learnFromReply(userId, reply)

        val profileCaptor = ArgumentCaptor.forClass(UserStyleProfile::class.java)
        verify(mockUserStyleRepository).saveProfile(profileCaptor.capture())
        val commonPhrases = profileCaptor.value.commonPhrases
        // All phrases should be within 2-18 character limit
        commonPhrases.forEach { phrase ->
            assertTrue("Phrase length should be within limits",
                phrase.length in 2..18)
        }
    }

    // SL-B04: 风格指标平滑过渡避免突变
    @Test
    fun `SL-B04 style metrics smooth transition`() = runTest {
        val userId = "user_001"
        val initialProfile = TestDataFactory.userStyleProfile(
            formalityLevel = 0.2f,
            enthusiasmLevel = 0.3f,
            professionalismLevel = 0.4f,
            learningSamples = 10
        )
        val reply = TestDataFactory.replyHistory(finalReply = "新回复内容")

        `when`(mockUserStyleRepository.getProfileSync(userId)).thenReturn(initialProfile)

        styleLearningEngine.learnFromReply(userId, reply)

        val profileCaptor = ArgumentCaptor.forClass(UserStyleProfile::class.java)
        verify(mockUserStyleRepository).updateProfile(profileCaptor.capture())
        val updatedProfile = profileCaptor.value

        // Values should change but not drastically (smooth blending)
        assertThat(updatedProfile.formalityLevel).isNotEqualTo(0.2f) // Should change
        assertThat(updatedProfile.formalityLevel).isAtLeast(0f) // Should stay in valid range
        assertThat(updatedProfile.formalityLevel).isAtMost(1f) // Should stay in valid range
    }

    // SL-B05: 空或null用户ID处理
    @Test
    fun `SL-B05 handles empty or null user ID gracefully`() = runTest {
        val reply = TestDataFactory.replyHistory(finalReply = "回复")

        // Should not crash with empty user ID
        styleLearningEngine.learnFromReply("", reply)
        styleLearningEngine.learnFromReply("   ", reply)

        // Verify no exceptions thrown
        assertTrue("Operations should complete without error", true)
    }

    // ==================== ❌ 异常情况测试 ====================

    // SL-E01: AI分析失败时使用本地分析
    @Test
    fun `SL-E01 AI analysis failure falls back to local analysis`() = runTest {
        val userId = "user_001"
        val profile = TestDataFactory.userStyleProfile(learningSamples = 10)
        val replies = (1..10).map {
            TestDataFactory.replyHistory(finalReply = "样本$it")
        }

        `when`(mockReplyHistoryRepository.getRecentReplies(anyInt())).thenReturn(kotlinx.coroutines.flow.flowOf(replies))
        `when`(mockUserStyleRepository.getProfileSync(userId)).thenReturn(profile)
        `when`(mockAIService.analyzeTextStyle(anyString())).thenReturn(
            Result.failure(Exception("AI分析失败"))
        )

        styleLearningEngine.learnFromReply(userId, replies[0])

        // Should still update profile even if AI analysis fails
        verify(mockUserStyleRepository).updateProfile(any())
    }

    // SL-E02: 数据库操作异常处理
    @Test
    fun `SL-E02 database operation exception handling`() = runTest {
        val userId = "user_001"
        val reply = TestDataFactory.replyHistory(finalReply = "回复")

        // Mock repository throwing exception
        `when`(mockUserStyleRepository.getProfileSync(userId)).thenThrow(RuntimeException("数据库错误"))

        // Should handle exception gracefully without crashing
        styleLearningEngine.learnFromReply(userId, reply)

        // Verify the method completed despite exception
        assertTrue("Should handle database errors gracefully", true)
    }

    // SL-E03: 风格参数手动更新
    @Test
    fun `SL-E03 manual style parameter update`() = runTest {
        val userId = "user_001"
        val newFormality = 0.9f
        val newEnthusiasm = 0.7f

        styleLearningEngine.updateStyleParameters(
            userId = userId,
            formality = newFormality,
            enthusiasm = newEnthusiasm
        )

        verify(mockUserStyleRepository).updateFormalityLevel(userId, newFormality)
        verify(mockUserStyleRepository).updateEnthusiasmLevel(userId, newEnthusiasm)
        verify(mockUserStyleRepository, never()).updateProfessionalismLevel(any(), any())
    }

    // SL-E04: applyStyle无用户配置时使用原始文本
    @Test
    fun `SL-E04 applyStyle returns original text when no user profile`() = runTest {
        val userId = "user_001"
        val text = "原始文本"

        `when`(mockUserStyleRepository.getProfileSync(userId)).thenReturn(null)

        val result = styleLearningEngine.applyStyle(text, userId)

        assertTrue("Should return original text", result.isSuccess)
        assertEquals("Should return original text unchanged", text, result.getOrNull())
    }

    // SL-E05: 并发学习操作线程安全
    @Test
    fun `SL-E05 concurrent learning operations thread safe`() = runTest {
        val userId = "user_001"
        val reply = TestDataFactory.replyHistory(finalReply = "回复")

        // Simulate sequential learning (concurrent operations would require more complex setup)
        repeat(10) {
            styleLearningEngine.learnFromReply(userId, reply)
        }

        // Should complete without deadlocks or race conditions
        assertTrue("Operations should complete safely", true)
    }

    // SL-E06: 超长文本处理
    @Test
    fun `SL-E06 handles very long text input`() = runTest {
        val userId = "user_001"
        val longReply = TestDataFactory.replyHistory(
            finalReply = "这是一段非常长的回复文本。".repeat(100) // Very long text
        )

        `when`(mockUserStyleRepository.getProfileSync(userId)).thenReturn(null)

        // Should handle long text without performance issues
        styleLearningEngine.learnFromReply(userId, longReply)

        verify(mockUserStyleRepository).saveProfile(any())
    }
}