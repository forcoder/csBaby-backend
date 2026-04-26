package com.csbaby.kefu

import android.content.Context
import com.csbaby.kefu.domain.model.ReplyContext
import com.csbaby.kefu.domain.model.ReplyResult
import com.csbaby.kefu.domain.model.ReplySource
import com.csbaby.kefu.factory.TestDataFactory
import com.csbaby.kefu.infrastructure.reply.ReplyGenerator
import com.csbaby.kefu.infrastructure.knowledge.KeywordMatcher
import com.csbaby.kefu.infrastructure.llm.LLMFeatureManager
import com.csbaby.kefu.infrastructure.llm.OptimizationEngine
import com.csbaby.kefu.infrastructure.llm.AutoRuleGenerator
import com.csbaby.kefu.fakes.knowledge.FakeKeywordRuleRepository
import com.csbaby.kefu.data.local.PreferencesManager
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

class ReplyGeneratorTest {

    @Mock private lateinit var mockContext: Context
    @Mock private lateinit var mockKnowledgeBaseManager: com.csbaby.kefu.infrastructure.knowledge.KnowledgeBaseManager
    @Mock private lateinit var mockAIService: com.csbaby.kefu.infrastructure.ai.AIService
    @Mock private lateinit var mockStyleLearningEngine: com.csbaby.kefu.infrastructure.style.StyleLearningEngine
    @Mock private lateinit var mockReplyHistoryRepository: com.csbaby.kefu.domain.repository.ReplyHistoryRepository
    @Mock private lateinit var mockUserStyleRepository: com.csbaby.kefu.domain.repository.UserStyleRepository
    @Mock private lateinit var mockPreferencesManager: com.csbaby.kefu.data.local.PreferencesManager
    @Mock private lateinit var mockLLMFeatureManager: LLMFeatureManager
    @Mock private lateinit var mockOptimizationEngine: OptimizationEngine
    @Mock private lateinit var mockAutoRuleGenerator: AutoRuleGenerator

    private lateinit var replyGenerator: ReplyGenerator
    private val testMessage = "请问价格是多少"
    private val testContext = TestDataFactory.replyContext()

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)

        // Mock preferences
        `when`(mockPreferencesManager.userPreferencesFlow).thenReturn(flowOf(
            PreferencesManager.UserPreferences(
                monitoringEnabled = true,
                selectedApps = setOf("com.whatsapp"),
                styleLearningEnabled = true
            )
        ))

        replyGenerator = ReplyGenerator(
            mockKnowledgeBaseManager,
            mockAIService,
            mockStyleLearningEngine,
            mockReplyHistoryRepository,
            mockUserStyleRepository,
            mockPreferencesManager,
            mockLLMFeatureManager,
            mockOptimizationEngine,
            mockAutoRuleGenerator
        )
    }

    // ========== ✅ 正常功能测试 ==========

    // RG-001: 知识库优先匹配
    @Test
    fun `RG-001 knowledge base priority match`() = runTest {
        val matchedRule = com.csbaby.kefu.factory.TestDataFactory.containsRule()
        `when`(mockKnowledgeBaseManager.findBestMatch(testMessage, testContext))
            .thenReturn(com.csbaby.kefu.infrastructure.knowledge.KeywordMatcher.MatchedResult(
                rule = matchedRule,
                matchedText = "价格",
                matchStart = 2,
                matchEnd = 3,
                confidence = 0.8f
            ))
        `when`(mockKnowledgeBaseManager.generateReplyFromRule(any()))
            .thenReturn("规则回复")

        val result = replyGenerator.generateReply(testMessage, testContext)

        assertEquals("规则回复", result.reply)
        assertEquals(ReplySource.RULE_MATCH, result.source)
        assertEquals(matchedRule.id, result.ruleId)
        verify(mockAIService, never()).generateCompletion(any(), any(), any(), any())
    }

    // RG-002: 知识库未匹配转AI生成
    @Test
    fun `RG-002 no knowledge base match falls back to AI`() = runTest {
        `when`(mockKnowledgeBaseManager.findBestMatch(testMessage, testContext))
            .thenReturn(null)
        `when`(mockAIService.generateCompletion(any(), any(), any(), any()))
            .thenReturn(Result.success("AI回复"))

        val result = replyGenerator.generateReply(testMessage, testContext)

        assertEquals("AI回复", result.reply)
        assertEquals(ReplySource.AI_GENERATED, result.source)
    }

    // RG-003: AI生成成功返回
    @Test
    fun `RG-003 AI generation success returns result`() = runTest {
        `when`(mockKnowledgeBaseManager.findBestMatch(testMessage, testContext))
            .thenReturn(null)
        `when`(mockAIService.generateCompletion(any(), any(), any(), any()))
            .thenReturn(Result.success("AI回复"))
        `when`(mockUserStyleRepository.getProfileSync(testContext.userId))
            .thenReturn(TestDataFactory.userStyleProfile())

        val result = replyGenerator.generateReply(testMessage, testContext)

        assertTrue("Should have model ID", result.modelId != null)
        assertTrue("Style should be applied", result.source == ReplySource.AI_GENERATED)
    }

    // RG-004: AI失败返回兜底回复
    @Test
    fun `RG-004 AI failure returns fallback reply`() = runTest {
        `when`(mockKnowledgeBaseManager.findBestMatch(testMessage, testContext))
            .thenReturn(null)
        `when`(mockAIService.generateCompletion(any(), any(), any(), any()))
            .thenReturn(Result.failure(Exception("AI错误")))

        val result = replyGenerator.generateReply(testMessage, testContext)

        assertEquals("感谢您的留言，我们会尽快处理您的问题。", result.reply)
        assertEquals(0.1f, result.confidence, 0.01f)
    }

    // RG-005: 风格学习启用时应用风格
    @Test
    fun `RG-005 style learning enabled applies style`() = runTest {
        `when`(mockKnowledgeBaseManager.findBestMatch(testMessage, testContext))
            .thenReturn(null)
        `when`(mockAIService.generateCompletion(any(), any(), any(), any()))
            .thenReturn(Result.success("原始回复"))
        `when`(mockUserStyleRepository.getProfileSync(testContext.userId))
            .thenReturn(TestDataFactory.userStyleProfile())
        `when`(mockStyleLearningEngine.applyStyle("原始回复", testContext.userId))
            .thenReturn(Result.success("风格化回复"))

        val result = replyGenerator.generateReply(testMessage, testContext)

        assertEquals("风格化回复", result.reply)
        verify(mockStyleLearningEngine).applyStyle("原始回复", testContext.userId)
    }

    // RG-006: 风格学习未启用时不调整
    @Test
    fun `RG-006 style learning disabled does not adjust`() = runTest {
        `when`(mockPreferencesManager.userPreferencesFlow).thenReturn(flowOf(
            PreferencesManager.UserPreferences(
                monitoringEnabled = true,
                selectedApps = setOf("com.whatsapp"),
                styleLearningEnabled = false
            )
        ))
        `when`(mockKnowledgeBaseManager.findBestMatch(testMessage, testContext))
            .thenReturn(null)
        `when`(mockAIService.generateCompletion(any(), any(), any(), any()))
            .thenReturn(Result.success("原始回复"))

        val result = replyGenerator.generateReply(testMessage, testContext)

        assertEquals("原始回复", result.reply)
        verify(mockStyleLearningEngine, never()).applyStyle(any(), any())
    }

    // RG-008: recordUserReply记录历史
    @Test
    fun `RG-008 record user reply saves history`() = runTest {
        val context = TestDataFactory.replyContext()
        val result = TestDataFactory.replyResult(source = ReplySource.AI_GENERATED)

        replyGenerator.recordUserReply(
            originalMessage = "你好",
            generatedReply = "AI回复",
            finalReply = "用户修改的回复",
            context = context,
            result = result
        )

        verify(mockReplyHistoryRepository).insertReply(any())
        verify(mockStyleLearningEngine).learnFromReply(context.userId, any())
    }

    // RG-010: generateSuggestions合并知识库+AI
    @Test
    fun `RG-010 generate suggestions combines KB and AI`() = runTest {
        val ruleMatches = listOf(
            com.csbaby.kefu.infrastructure.knowledge.KeywordMatcher.MatchedResult(
                rule = TestDataFactory.keywordRule(id = 1L),
                matchedText = "价格",
                matchStart = 0,
                matchEnd = 1,
                confidence = 0.8f
            ),
            com.csbaby.kefu.infrastructure.knowledge.KeywordMatcher.MatchedResult(
                rule = TestDataFactory.keywordRule(id = 2L),
                matchedText = "费用",
                matchStart = 0,
                matchEnd = 1,
                confidence = 0.7f
            )
        )
        `when`(mockKnowledgeBaseManager.findAllMatches(testMessage, testContext))
            .thenReturn(ruleMatches)
        `when`(mockKnowledgeBaseManager.generateReplyFromRule(any()))
            .thenReturn("规则回复")
        `when`(mockAIService.generateCompletion(any(), any(), any(), any()))
            .thenReturn(Result.success("AI建议"))

        val suggestions = replyGenerator.generateSuggestions(testMessage, testContext, 3)

        assertTrue("Should return at least rule matches", suggestions.size >= 2)
    }

    // RG-011: buildSystemPrompt包含基础提示词
    @Test
    fun `RG-011 build system prompt includes base guidelines`() {
        val prompt = replyGenerator.buildSystemPrompt(testContext, null)
        assertTrue("Should contain professional customer service", prompt.contains("professional customer service assistant"))
        assertTrue("Should contain helpful guidelines", prompt.contains("Be helpful and solution-oriented"))
    }

    // RG-012: buildSystemPrompt追加风格提示
    @Test
    fun `RG-012 build system prompt appends style when available`() {
        val profile = TestDataFactory.userStyleProfile()
        val prompt = replyGenerator.buildSystemPrompt(testContext, profile)
        assertTrue("Should contain style customization", prompt.contains("mirroring the owner's reply style"))
    }

    // ========== ⚠️ 边界条件测试 ==========

    // RG-B01: 知识库匹配但置信度低于阈值
    @Test
    fun `RG-B01 low confidence rule still returned`() = runTest {
        val matchedRule = com.csbaby.kefu.factory.TestDataFactory.containsRule(priority = 1)
        `when`(mockKnowledgeBaseManager.findBestMatch(testMessage, testContext))
            .thenReturn(com.csbaby.kefu.infrastructure.knowledge.KeywordMatcher.MatchedResult(
                rule = matchedRule,
                matchedText = "价格",
                matchStart = 2,
                matchEnd = 3,
                confidence = 0.3f // < 0.5 threshold
            ))
        `when`(mockKnowledgeBaseManager.generateReplyFromRule(any()))
            .thenReturn("规则回复")

        val result = replyGenerator.generateReply(testMessage, testContext)

        assertEquals("规则回复", result.reply)
        // Code does not filter by confidence threshold
    }

    // RG-B03: scenarioId为空
    @Test
    fun `RG-B03 empty scenarioId`() = runTest {
        val contextWithNullScenario = TestDataFactory.replyContext(scenarioId = null)
        `when`(mockAIService.generateCompletion(any(), any(), any(), any()))
            .thenReturn(Result.success("回复"))

        val result = replyGenerator.generateReply("你好", contextWithNullScenario)

        assertNotNull("Should handle null scenario", result)
    }

    // RG-B04: finalReply为空时不学习
    @Test
    fun `RG-B04 empty final reply does not learn`() = runTest {
        val emptyFinalReply = TestDataFactory.replyHistory(finalReply = "")

        replyGenerator.recordUserReply(
            originalMessage = "你好",
            generatedReply = "AI回复",
            finalReply = "", // Empty
            context = testContext,
            result = TestDataFactory.replyResult()
        )

        verify(mockStyleLearningEngine, never()).learnFromReply(any(), any())
    }

    // ========== ❌ 异常情况测试 ==========

    // RG-E01: AI生成抛出异常
    @Test
    fun `RG-E01 AI generation throws exception`() = runTest {
        `when`(mockKnowledgeBaseManager.findBestMatch(testMessage, testContext))
            .thenReturn(null)
        `when`(mockAIService.generateCompletion(any(), any(), any(), any()))
            .thenReturn(Result.failure(RuntimeException("Network error")))

        val result = replyGenerator.generateReply(testMessage, testContext)

        assertEquals("感谢您的留言，我们会尽快处理您的问题。", result.reply)
    }

    // RG-E02: 知识库匹配异常
    @Test
    fun `RG-E02 knowledge base match exception`() = runTest {
        doThrow(RuntimeException("Database error")).`when`(mockKnowledgeBaseManager)
            .findBestMatch(testMessage, testContext)

        val result = replyGenerator.generateReply(testMessage, testContext)

        // Should fall back to AI or default
        assertNotNull("Should return some result", result)
    }

    // RG-E03: 风格调整失败
    @Test
    fun `RG-E03 style adjustment failure`() = runTest {
        `when`(mockKnowledgeBaseManager.findBestMatch(testMessage, testContext))
            .thenReturn(null)
        `when`(mockAIService.generateCompletion(any(), any(), any(), any()))
            .thenReturn(Result.success("原始回复"))
        `when`(mockUserStyleRepository.getProfileSync(testContext.userId))
            .thenReturn(TestDataFactory.userStyleProfile())
        `when`(mockStyleLearningEngine.applyStyle("原始回复", testContext.userId))
            .thenReturn(Result.failure(Exception("Style error")))

        val result = replyGenerator.generateReply(testMessage, testContext)

        assertEquals("原始回复", result.reply) // Fallback to original
    }
}
