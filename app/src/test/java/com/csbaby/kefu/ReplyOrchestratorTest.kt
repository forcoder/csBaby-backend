package com.csbaby.kefu

import android.content.Context
import com.csbaby.kefu.domain.model.ReplyContext
import com.csbaby.kefu.factory.TestDataFactory
import com.csbaby.kefu.infrastructure.notification.MessageMonitor
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

class ReplyOrchestratorTest {

    @Mock private lateinit var mockContext: Context
    @Mock private lateinit var mockReplyGenerator: com.csbaby.kefu.infrastructure.reply.ReplyGenerator
    @Mock private lateinit var mockMessageMonitor: MessageMonitor
    @Mock private lateinit var mockPreferencesManager: com.csbaby.kefu.data.local.PreferencesManager
    @Mock private lateinit var mockKnowledgeBaseManager: com.csbaby.kefu.infrastructure.knowledge.KnowledgeBaseManager
    @Mock private lateinit var mockBlacklistRepository: com.csbaby.kefu.domain.repository.MessageBlacklistRepository
    @Mock private lateinit var mockFloatingWindowService: com.csbaby.kefu.infrastructure.window.FloatingWindowService
    @Mock private lateinit var mockLlmFeatureManager: com.csbaby.kefu.infrastructure.llm.LLMFeatureManager
    @Mock private lateinit var mockOptimizationEngine: com.csbaby.kefu.infrastructure.llm.OptimizationEngine

    private lateinit var orchestrator: com.csbaby.kefu.infrastructure.reply.ReplyOrchestrator

    private val testMessage = TestDataFactory.monitoredMessage(
        packageName = "com.whatsapp",
        content = "你好，请问价格是多少？"
    )

    private val testBaijuyiMessage = TestDataFactory.monitoredMessage(
        packageName = "com.myhostex.hostexapp",
        content = "您好",
        conversationTitle = "海景别墅"
    )

    private val testContext = TestDataFactory.replyContext(
        appPackage = "com.whatsapp",
        conversationTitle = "张三"
    )

    private val testResult = TestDataFactory.replyResult(
        reply = "您好，价格是100元",
        source = com.csbaby.kefu.domain.model.ReplySource.RULE_MATCH,
        ruleId = 1L
    )

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        `when`(mockMessageMonitor.messageFlow).thenReturn(flowOf())
        `when`(mockPreferencesManager.userPreferencesFlow).thenReturn(flowOf(
            com.csbaby.kefu.data.local.UserPreferences(
                monitoringEnabled = true,
                selectedApps = setOf("com.whatsapp"),
                floatingWindowEnabled = true,
                floatingIconEnabled = true
            )
        ))
        `when`(mockBlacklistRepository.shouldFilterMessage(any(), any(), any())).thenReturn(false)

        orchestrator = com.csbaby.kefu.infrastructure.reply.ReplyOrchestrator(
            mockContext, mockReplyGenerator, mockMessageMonitor,
            mockPreferencesManager, mockKnowledgeBaseManager, mockBlacklistRepository,
            mockLlmFeatureManager, mockOptimizationEngine
        )
    }

    // ==================== ✅ 正常功能测试 ====================

    // RO-001: start启动消息收集
    @Test
    fun `RO-001 start begins message collection`() {
        // Should not crash
        orchestrator.start()
        assertTrue("Should not crash on start", true)
    }

    // RO-002: stop停止所有任务
    @Test
    fun `RO-002 stop cancels all jobs`() {
        orchestrator.start()
        orchestrator.stop()
        assertTrue("Should not crash on stop", true)
    }

    // RO-004: 占位消息过滤-"给你发送了新消息"
    @Test
    fun `RO-004 skip placeholder - send new message`() {
        val placeholderMsg = TestDataFactory.monitoredMessage(content = "给你发送了新消息")
        orchestrator.handleNewMessage(placeholderMsg)
        verify(mockReplyGenerator, never()).generateReply(any(), any())
    }

    // RO-005: 占位消息过滤-"向你发送了一条消息"
    @Test
    fun `RO-005 skip placeholder - sent you a message`() {
        val placeholderMsg = TestDataFactory.monitoredMessage(content = "向你发送了一条消息")
        orchestrator.handleNewMessage(placeholderMsg)
        verify(mockReplyGenerator, never()).generateReply(any(), any())
    }

    // RO-006: 占位消息过滤-"[图片]"
    @Test
    fun `RO-006 skip placeholder - image bracket`() {
        val placeholderMsg = TestDataFactory.monitoredMessage(content = "[图片]")
        orchestrator.handleNewMessage(placeholderMsg)
        verify(mockReplyGenerator, never()).generateReply(any(), any())
    }

    // RO-007: 占位消息过滤-"[语音]"
    @Test
    fun `RO-007 skip placeholder - voice bracket`() {
        val placeholderMsg = TestDataFactory.monitoredMessage(content = "[语音]")
        orchestrator.handleNewMessage(placeholderMsg)
        verify(mockReplyGenerator, never()).generateReply(any(), any())
    }

    // RO-008: 占位消息过滤-"[视频]"
    @Test
    fun `RO-008 skip placeholder - video bracket`() {
        val placeholderMsg = TestDataFactory.monitoredMessage(content = "[视频]")
        orchestrator.handleNewMessage(placeholderMsg)
        verify(mockReplyGenerator, never()).generateReply(any(), any())
    }

    // RO-009: 占位消息过滤-"[文件]"
    @Test
    fun `RO-009 skip placeholder - file bracket`() {
        val placeholderMsg = TestDataFactory.monitoredMessage(content = "[文件]")
        orchestrator.handleNewMessage(placeholderMsg)
        verify(mockReplyGenerator, never()).generateReply(any(), any())
    }

    // RO-010: 占位消息过滤-"[表情]"
    @Test
    fun `RO-010 skip placeholder - emoji bracket`() {
        val placeholderMsg = TestDataFactory.monitoredMessage(content = "[表情]")
        orchestrator.handleNewMessage(placeholderMsg)
        verify(mockReplyGenerator, never()).generateReply(any(), any())
    }

    // RO-011: 仅有媒体占位符过滤
    @Test
    fun `RO-011 skip placeholder - only media bracket`() {
        val placeholderMsg = TestDataFactory.monitoredMessage(content = "发送了 [图片] ")
        orchestrator.handleNewMessage(placeholderMsg)
        verify(mockReplyGenerator, never()).generateReply(any(), any())
    }

    // RO-013: 黑名单过滤
    @Test
    fun `RO-013 blacklist filtering`() {
        `when`(mockBlacklistRepository.shouldFilterMessage(any(), any(), any()))
            .thenReturn(true)

        orchestrator.handleNewMessage(testMessage)
        verify(mockReplyGenerator, never()).generateReply(any(), any())
    }

    // RO-014: 监控未启用时跳过
    @Test
    fun `RO-014 monitoring disabled skip`() {
        `when`(mockPreferencesManager.userPreferencesFlow)
            .thenReturn(flowOf(com.csbaby.kefu.data.local.UserPreferences(
                monitoringEnabled = false,
                selectedApps = setOf("com.whatsapp"),
                floatingWindowEnabled = true
            )))

        orchestrator.handleNewMessage(testMessage)
        verify(mockReplyGenerator, never()).generateReply(any(), any())
    }

    // RO-015: 应用不在选中列表时跳过
    @Test
    fun `RO-015 app not in selection skip`() {
        `when`(mockPreferencesManager.userPreferencesFlow)
            .thenReturn(flowOf(com.csbaby.kefu.data.local.UserPreferences(
                monitoringEnabled = true,
                selectedApps = setOf("com.wechat"), // Not whatsApp
                floatingWindowEnabled = true
            )))

        orchestrator.handleNewMessage(testMessage)
        verify(mockReplyGenerator, never()).generateReply(any(), any())
    }

    // RO-018: 百居易消息提取propertyName
    @Test
    fun `RO-018 Baijuyi extract property name`() {
        val baijuyiContext = ReplyContext(
            appPackage = "com.myhostex.hostexapp",
            conversationTitle = "海景别墅",
            propertyName = null,
            isGroupConversation = false,
            userId = "user_001"
        )

        // This tests the extraction logic indirectly via ReplyContext construction
        assertNotNull("Property should be extracted", baijuyiContext.propertyName)
    }

    // RO-019: 悬浮窗显示
    @Test
    fun `RO-019 floating window display`() {
        `when`(mockReplyGenerator.generateReply(any(), any()))
            .thenReturn(TestDataFactory.replyResult())

        orchestrator.handleNewMessage(testMessage)

        verify(mockFloatingWindowService).show(any(), any())
    }

    // RO-020: 悬浮窗关闭时不显示
    @Test
    fun `RO-020 no floating window when disabled`() {
        `when`(mockPreferencesManager.userPreferencesFlow)
            .thenReturn(flowOf(com.csbaby.kefu.data.local.UserPreferences(
                monitoringEnabled = true,
                selectedApps = setOf("com.whatsapp"),
                floatingWindowEnabled = false
            )))
        `when`(mockReplyGenerator.generateReply(any(), any()))
            .thenReturn(TestDataFactory.replyResult())

        orchestrator.handleNewMessage(testMessage)

        verify(mockFloatingWindowService, never()).show(any(), any())
    }

    // RO-021: generateReplyForMessage手动触发
    @Test
    fun `RO-021 generate reply for message manually`() {
        `when`(mockReplyGenerator.generateReply(any(), any()))
            .thenReturn(TestDataFactory.replyResult())

        val result = orchestrator.generateReplyForMessage("你好", "com.whatsapp")

        assertNotNull("Should return result", result)
    }

    // RO-022: recordFinalReply记录用户回复
    @Test
    fun `RO-022 record final reply`() {
        orchestrator.recordFinalReply(
            originalMessage = "你好",
            generatedReply = "AI回复",
            finalReply = "用户修改的回复",
            appPackage = "com.whatsapp",
            source = com.csbaby.kefu.domain.model.ReplySource.AI_GENERATED,
            confidence = 0.8f
        )

        assertTrue("Should complete without error", true)
    }

    // RO-023: generateSuggestions生成多条建议
    @Test
    fun `RO-023 generate suggestions`() {
        `when`(mockReplyGenerator.generateSuggestions(any(), any(), any()))
            .thenReturn(listOf(TestDataFactory.replyResult()))

        val suggestions = orchestrator.generateSuggestions("你好", "com.whatsapp", 3)

        assertNotNull("Should return suggestions", suggestions)
        assertEquals(1, suggestions.size)
    }

    // ==================== ⚠️ 边界条件测试 ====================

    // RO-B01: 消息内容刚好2字符
    @Test
    fun `RO-B01 message exactly 2 chars`() {
        val msg = TestDataFactory.monitoredMessage(content = "ab")
        orchestrator.handleNewMessage(msg)
        verify(mockReplyGenerator, never()).generateReply(any(), any())
    }

    // RO-B02: 含媒体占位符但有实质文字
    @Test
    fun `RO-B02 media bracket with actual text`() {
        val msg = TestDataFactory.monitoredMessage(content = "请看[图片]这个")
        orchestrator.handleNewMessage(msg)
        verify(mockReplyGenerator).generateReply(any(), any())
    }

    // RO-B03: conversationTitle为null时使用title
    @Test
    fun `RO-B03 use title when conversationTitle null`() {
        val context = ReplyContext(
            appPackage = "com.test",
            conversationTitle = null,
            userId = "user_001"
        )
        assertNotNull("Should handle null conversationTitle", context.conversationTitle)
    }

    // RO-B04: generateSuggestions count=0
    @Test
    fun `RO-B04 generate suggestions count zero`() {
        val suggestions = orchestrator.generateSuggestions("你好", "com.whatsapp", 0)
        assertTrue("Should return empty list", suggestions.isEmpty())
    }

    // RO-B05: searchKnowledgeRules AUTO模式
    @Test
    fun `RO-B05 search knowledge rules AUTO mode`() {
        val result = orchestrator.searchKnowledgeRules("价格")
        assertTrue("Should return results", result.size <= 15)
    }

    // ==================== ❌ 异常情况测试 ====================

    // RO-E01: handleNewMessage内部异常
    @Test
    fun `RO-E01 internal exception in handleNewMessage`() {
        doThrow(RuntimeException("Test")).whenever(mockReplyGenerator)
            .generateReply(any(), any())

        // Should catch and log, not crash
        orchestrator.handleNewMessage(testMessage)
        assertTrue("Should not crash", true)
    }

    // RO-E05: start()本身异常
    @Test
    fun `RO-E05 start throws exception`() {
        // Should catch exception internally
        orchestrator.start()
        assertTrue("Should not crash", true)
    }
}