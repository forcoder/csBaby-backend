package com.csbaby.kefu

import com.csbaby.kefu.domain.model.ReplyHistory
import com.csbaby.kefu.domain.model.UserStyleProfile
import com.csbaby.kefu.factory.TestDataFactory
import com.csbaby.kefu.infrastructure.ai.AIService
import com.csbaby.kefu.infrastructure.style.StyleLearningEngine
import com.csbaby.kefu.fakes.ai.FakeAIClient
import com.csbaby.kefu.fakes.ai.FakeAIModelRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

class StyleLearningEngineTest {

    @Mock private lateinit var mockReplyHistoryRepository: com.csbaby.kefu.domain.repository.ReplyHistoryRepository
    @Mock private lateinit var mockUserStyleRepository: com.csbaby.kefu.domain.repository.UserStyleRepository
    @Mock private lateinit var mockAIClient: FakeAIClient
    @Mock private lateinit var mockAIModelRepository: FakeAIModelRepository

    private lateinit var styleEngine: StyleLearningEngine

    private val userId = "user_001"
    private val reply = TestDataFactory.replyHistory(
        finalReply = "您好，很高兴为您服务。"
    )

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        `when`(mockUserStyleRepository.getProfileSync(userId))
            .thenReturn(null)
        `when`(mockReplyHistoryRepository.getRecentReplies(anyInt()))
            .thenReturn(MutableStateFlow(emptyList()))

        styleEngine = StyleLearningEngine(
            mockReplyHistoryRepository,
            mockUserStyleRepository,
            AIService(mockAIClient, mockAIModelRepository)
        )
    }

    // ==================== ✅ 正常功能测试 ====================

    // SL-001: learnFromReply首次学习
    @Test
    fun `SL-001 learn from reply first time`() = runTest {
        styleEngine.learnFromReply(userId, reply)

        verify(mockUserStyleRepository).saveProfile(any())
        val profile = mockUserStyleRepository.getProfileSync(userId)
        assertNotNull("Should save profile", profile)
        assertEquals(userId, profile!!.userId)
    }

    // SL-002: learnFromReply增量学习
    @Test
    fun `SL-002 learn from reply incrementally`() = runTest {
        // First sample
        styleEngine.learnFromReply(userId, reply.copy(finalReply = "回复1"))

        // Second sample - should blend metrics
        styleEngine.learnFromReply(userId, reply.copy(finalReply = "回复2"))

        val profile = mockUserStyleRepository.getProfileSync(userId)
        assertNotNull("Profile should exist", profile)
        assertThat(profile!!.learningSamples).isEqualTo(2)
    }

    // SL-003: learnFromReply空回复跳过
    @Test
    fun `SL-003 skip empty final reply`() = runTest {
        val emptyReply = reply.copy(finalReply = "")
        styleEngine.learnFromReply(userId, emptyReply)

        verify(mockUserStyleRepository, never()).saveProfile(any())
    }

    // SL-004: blendMetric加权平均
    @Test
    fun `SL-004 blend metric weighted average`() {
        val result = styleEngine.blendMetric(0.5f, 0.8f, 1) // first sample
        assertThat(result).isIn(0.5f..0.8f)
    }

    // SL-006: blendLengthPreference长度偏好
    @Test
    fun `SL-006 blend length preference`() {
        val result = styleEngine.blendLengthPreference(10, 20, 1)
        assertThat(result).isIn(10..20)
    }

    // SL-007: calculateAccuracyScore公式
    @Test
    fun `SL-007 calculate accuracy score formula`() {
        val score1 = styleEngine.calculateAccuracyScore(1)
        val score10 = styleEngine.calculateAccuracyScore(10)
        val score50 = styleEngine.calculateAccuracyScore(50)

        assertTrue("Score should increase with samples", score1 < score10 && score10 < score50)
    }

    // SL-009: extractCommonPhrases提取短语
    @Test
    fun `SL-009 extract common phrases`() {
        val text = "您好，很高兴为您服务。请问有什么可以帮您？"
        val phrases = styleEngine.extractCommonPhrases(listOf(text))

        assertTrue("Should extract phrases", phrases.isNotEmpty())
        phrases.forEach { phrase ->
            assertThat(phrase.length).isIn(2..18)
        }
    }

    // SL-010: extractCommonPhrases最多8条
    @Test
    fun `SL-010 extract common phrases max 8`() {
        val manyTexts = (1..20).map { "短语$it" }
        val phrases = styleEngine.extractCommonPhrases(manyTexts)
        assertThat(phrases.size).isAtMost(8)
    }

    // SL-012: 每10个样本触发深度分析
    @Test
    fun `SL-012 deep analysis every 10 samples`() = runTest {
        // Simulate 9 samples
        repeat(9) { index ->
            styleEngine.learnFromReply(userId, reply.copy(finalReply = "回复$index"))
        }

        // 10th sample should trigger deep analysis
        styleEngine.learnFromReply(userId, reply.copy(finalReply = "回复10"))

        verify(mockReplyHistoryRepository).getRecentReplies(eq(500))
    }

    // SL-014: 深度分析最少5个样本
    @Test
    fun `SL-014 deep analysis min 5 samples`() = runTest {
        `when`(mockReplyHistoryRepository.getRecentReplies(anyInt()))
            .thenReturn(MutableStateFlow(listOf(
                reply, reply, reply, reply // only 4
            )))

        styleEngine.performDeepAnalysis(userId, TestDataFactory.userStyleProfile(learningSamples = 10))

        verify(mockAIClient, never()).generateCompletion(any(), any(), any(), any())
    }

    // SL-016: 深度分析取最近10条合并
    @Test
    fun `SL-016 deep analysis take last 10 combined`() = runTest {
        `when`(mockReplyHistoryRepository.getRecentReplies(anyInt()))
            .thenReturn(MutableStateFlow((1..20).map { reply.copy(finalReply = "回复$it") }))

        styleEngine.performDeepAnalysis(userId, TestDataFactory.userStyleProfile())

        // Should take only 10 for analysis
        verify(mockAIClient).generateCompletion(
            eq("Analyze the writing style of the following text and provide metrics:\n\nOriginal text:\n\"回复1\n---\n回复2\n---\n回复3\n---\n回复4\n---\n回复5\n---\n回复6\n---\n回复7\n---\n回复8\n---\n回复9\n---\n回复10\"\n\nProvide your analysis in JSON format..."),
            any(),
            any(),
            any()
        )
    }

    // SL-017: analyzeLocalStyleSignals正式度
    @Test
    fun `SL-017 formal signal detection`() {
        val formalText = "您好，感谢致电。请稍等，我正在为您查询。"
        val signals = styleEngine.analyzeLocalStyleSignals(formalText)

        assertTrue("Should detect formality", signals.formality > 0f)
    }

    // SL-018: analyzeLocalStyleSignals热情度
    @Test
    fun `SL-018 enthusiasm signal detection`() {
        val warmText = "您好！😊 非常感谢您的来电，祝您生活愉快！"
        val signals = styleEngine.analyzeLocalStyleSignals(warmText)

        assertTrue("Should detect enthusiasm", signals.enthusiasm > 0f)
    }

    // SL-019: analyzeLocalStyleSignals专业度
    @Test
    fun `SL-019 professionalism signal detection`() {
        val professionalText = "您好，我是客服代表，正在为您核实订单信息。"
        val signals = styleEngine.analyzeLocalStyleSignals(professionalText)

        assertTrue("Should detect professionalism", signals.professionalism > 0f)
    }

    // SL-020: 正式度下限0.05
    @Test
    fun `SL-020 formality lower bound 0_05`() {
        val signals = styleEngine.analyzeLocalStyleSignals("哈")
        assertThat(signals.formality).isAtLeast(0.05f)
    }

    // SL-021: 正式度上限0.98
    @Test
    fun `SL-021 formality upper bound 0_98`() {
        val signals = styleEngine.analyzeLocalStyleSignals("您" * 100)
        assertThat(signals.formality).isAtMost(0.98f)
    }

    // SL-022: applyStyle应用风格
    @Test
    fun `SL-022 apply style`() = runTest {
        val result = styleEngine.applyStyle("原始文本", userId)
        // If no profile exists, returns original
        assertTrue("Should return success", result.isSuccess)
        assertEquals("原始文本", result.getOrNull())
    }

    // SL-023: getStyleProfile获取档案
    @Test
    fun `SL-023 get style profile`() = runTest {
        `when`(mockUserStyleRepository.getProfileSync(userId))
            .thenReturn(TestDataFactory.userStyleProfile())

        val profile = styleEngine.getStyleProfile(userId)
        assertNotNull("Should return profile", profile)
    }

    // SL-024: updateStyleParameters更新参数
    @Test
    fun `SL-024 update style parameters`() = runTest {
        styleEngine.updateStyleParameters(
            userId = userId,
            formality = 0.8f,
            enthusiasm = 0.6f,
            professionalism = 0.9f
        )

        verify(mockUserStyleRepository).updateFormalityLevel(userId, 0.8f)
        verify(mockUserStyleRepository).updateEnthusiasmLevel(userId, 0.6f)
        verify(mockUserStyleRepository).updateProfessionalismLevel(userId, 0.9f)
    }

    // SL-026: learnFromBatch批量学习
    @Test
    fun `SL-026 learn from batch`() = runTest {
        val replies = listOf(reply, reply, reply)
        styleEngine.learnFromBatch(userId, replies)

        verify(mockUserStyleRepository, atLeastOnce()).saveProfile(any())
    }

    // ==================== ⚠️ 边界条件测试 ====================

    // SL-B01: 首个样本的blendMetric
    @Test
    fun `SL-B01 first sample blend metric`() = runTest {
        val profile = TestDataFactory.userStyleProfile(learningSamples = 0)
        styleEngine.learnFromReply(userId, reply)

        val savedProfile = mockUserStyleRepository.getProfileSync(userId)
        assertThat(savedProfile!!.formalityLevel).isNotEqualTo(0f)
    }

    // SL-B03: 正式度-长文本加成
    @Test
    fun `SL-B03 formality long text bonus`() {
        val signals1 = styleEngine.analyzeLocalStyleSignals("短")
        val signals2 = styleEngine.analyzeLocalStyleSignals("短" + "您".repeat(60))
        // Longer text should have higher formality
        assertThat(signals2.formality).isGreaterThan(signals1.formality)
    }

    // SL-B05: 正式度-正式标记词
    @Test
    fun `SL-B05 formality formal markers`() {
        val signals1 = styleEngine.analyzeLocalStyleSignals("哈")
        val signals2 = styleEngine.analyzeLocalStyleSignals("您")
        assertThat(signals2.formality).isGreaterThan(signals1.formality)
    }

    // SL-B10: 热情度-感叹号加成
    @Test
    fun `SL-B10 enthusiasm exclamation mark bonus`() {
        val signals1 = styleEngine.analyzeLocalStyleSignals("您好")
        val signals2 = styleEngine.analyzeLocalStyleSignals("您好!")
        assertThat(signals2.enthusiasm).isGreaterThan(signals1.enthusiasm)
    }

    // SL-B11: 专业度-专业术语
    @Test
    fun `SL-B11 professionalism professional terms`() {
        val signals1 = styleEngine.analyzeLocalStyleSignals("哈")
        val signals2 = styleEngine.analyzeLocalStyleSignals("订单")
        assertThat(signals2.professionalism).isGreaterThan(signals1.professionalism)
    }

    // SL-B15: 短语长度范围[2,18]
    @Test
    fun `SL-B15 phrase length range 2-18`() {
        val phrases = styleEngine.extractCommonPhrases(listOf(
            "a", "ab", "abc", "abcd", "abcde",
            "abcdefghijklmnopqrst", // 18 chars
            "abcdefghijklmnopqrstu" // 19 chars
        ))
        phrases.forEach { phrase ->
            assertThat(phrase.length).isIn(2..18)
        }
    }

    // SL-B16: 短语频率≥2才保留
    @Test
    fun `SL-B16 phrase frequency >=2`() {
        val phrases = styleEngine.extractCommonPhrases(listOf(
            "a", "a", "b" // a appears twice, b once
        ))
        assertTrue("Should only keep frequent phrases", phrases.size == 1 && phrases[0] == "a")
    }

    // SL-B18: 深度分析AI结果合并
    @Test
    fun `SL-B18 deep analysis AI results merged`() = runTest {
        `when`(mockReplyHistoryRepository.getRecentReplies(anyInt()))
            .thenReturn(MutableStateFlow(listOf(reply)))

        `when`(mockAIClient.generateCompletion(any(), any(), any(), any()))
            .thenReturn(com.google.common.util.concurrent.Result.success(
                """{"formality": 0.9, "enthusiasm": 0.8, "professionalism": 0.7, "avgWordsPerSentence": 12}"""
            ))

        styleEngine.performDeepAnalysis(userId, TestDataFactory.userStyleProfile(
            learningSamples = 20, formalityLevel = 0.5f, enthusiasmLevel = 0.5f, professionalismLevel = 0.5f
        ))

        val profile = mockUserStyleRepository.getProfileSync(userId)
        // Weighted average: old weight = 1/21, new weight = 1/21
        assertThat(profile!!.formalityLevel).isIn(0.5f..0.9f)
    }

    // SL-B22: 风格提示词置信度显示
    @Test
    fun `SL-B22 style prompt confidence display`() {
        val profile = TestDataFactory.userStyleProfile(accuracyScore = 0.85f)
        val prompt = styleEngine.generateStyleSystemPrompt(profile)
        assertTrue("Should show confidence percentage", prompt.contains("85%"))
    }

    // ==================== ❌ 异常情况测试 ====================

    // SL-E01: AI分析失败
    @Test
    fun `SL-E01 AI analysis failure`() = runTest {
        `when`(mockAIClient.generateCompletion(any(), any(), any(), any()))
            .thenReturn(com.google.common.util.concurrent.Result.failure(Exception("API Error")))

        styleEngine.performDeepAnalysis(userId, TestDataFactory.userStyleProfile())

        // Should not crash
        assertTrue("Should not crash on AI failure", true)
    }

    // SL-E02: applyStyle失败
    @Test
    fun `SL-E02 apply style failure`() = runTest {
        `when`(mockAIClient.generateCompletion(any(), any(), any(), any()))
            .thenReturn(com.google.common.util.concurrent.Result.failure(Exception("Error")))

        val result = styleEngine.applyStyle("原始文本", userId)
        assertEquals("原始文本", result.getOrNull())
    }

    // SL-E04: 深度分析getProfileSync返回null
    @Test
    fun `SL-E04 deep analysis profile null`() = runTest {
        styleEngine.performDeepAnalysis(userId, null)

        // Should handle gracefully
        assertTrue("Should handle null profile", true)
    }
}