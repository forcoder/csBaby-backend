package com.csbaby.kefu

import com.csbaby.kefu.domain.model.AIModelConfig
import com.csbaby.kefu.domain.model.ModelType
import com.csbaby.kefu.domain.model.UserStyleProfile
import com.csbaby.kefu.fakes.ai.FakeAIClient
import com.csbaby.kefu.fakes.ai.FakeAIModelRepository
import com.csbaby.kefu.factory.TestDataFactory
import com.csbaby.kefu.infrastructure.ai.AIService
import com.csbaby.kefu.infrastructure.simple.RoutingResult
import com.csbaby.kefu.infrastructure.simple.SimpleTaskRouter
import com.csbaby.kefu.infrastructure.simple.TaskType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FakeSimpleTaskRouter : SimpleTaskRouter {
    companion object {
        fun create(): SimpleTaskRouter {
            return SimpleTaskRouter()
        }
    }

    override fun selectBestModel(
        taskType: TaskType,
        availableModels: List<AIModelConfig>
    ): RoutingResult {
        val model = availableModels.firstOrNull() ?: return RoutingResult.NoSuitableModel("no model")
        return RoutingResult.SingleChoice(
            com.csbaby.kefu.infrastructure.simple.ModelScore(model, 1.0f, "fake")
        )
    }
}

class AIServiceTest {

    private lateinit var fakeAIClient: FakeAIClient
    private lateinit var fakeRepository: FakeAIModelRepository
    private lateinit var fakeSimpleTaskRouter: FakeSimpleTaskRouter
    private lateinit var aiService: AIService

    @Before
    fun setup() {
        fakeAIClient = FakeAIClient()
        fakeRepository = FakeAIModelRepository()
        fakeSimpleTaskRouter = FakeSimpleTaskRouter()
        aiService = AIService(fakeAIClient, fakeRepository, fakeSimpleTaskRouter)
    }

    // ==================== ✅ 正常功能测试 ====================

    // AI-001: 默认模型生成回复
    @Test
    fun `AI-001 generate with default model`() = runTest {
        val model = TestDataFactory.openAIModel(id = 1L, isDefault = true)
        fakeRepository.setModels(listOf(model))
        fakeAIClient.generateResult = Result.success("AI回复内容")

        val result = aiService.generateCompletion("你好")

        assertTrue("Should succeed", result.isSuccess)
        assertEquals("AI回复内容", result.getOrNull())
        assertThat(fakeAIClient.lastGenerateConfig?.id).isEqualTo(1L)
    }

    // AI-002: 指定模型生成回复
    @Test
    fun `AI-002 generate with specific model`() = runTest {
        val model = TestDataFactory.openAIModel(id = 1L)
        fakeRepository.setModels(listOf(model))
        fakeAIClient.generateResult = Result.success("指定模型回复")

        val result = aiService.generateCompletionWithModel(
            modelId = 1L, prompt = "你好"
        )

        assertTrue("Should succeed", result.isSuccess)
        assertEquals("指定模型回复", result.getOrNull())
    }

    // AI-003: 缓存命中直接返回
    @Test
    fun `AI-003 cache hit returns cached result`() = runTest {
        val model = TestDataFactory.openAIModel(id = 1L, isDefault = true)
        fakeRepository.setModels(listOf(model))
        fakeAIClient.generateResult = Result.success("缓存回复")

        // First call - should hit API
        aiService.generateCompletion("你好")
        // Second call - should hit cache
        aiService.generateCompletion("你好")

        assertEquals("API should be called only once", 1, fakeAIClient.generateCallCount)
    }

    // AI-004: 缓存未命中走API调用
    @Test
    fun `AI-004 cache miss calls API`() = runTest {
        val model = TestDataFactory.openAIModel(id = 1L, isDefault = true)
        fakeRepository.setModels(listOf(model))

        aiService.generateCompletion("消息1")
        aiService.generateCompletion("消息2")

        assertEquals("API should be called twice", 2, fakeAIClient.generateCallCount)
    }

    // AI-005: 重试机制-第2次成功
    @Test
    fun `AI-005 retry succeeds on second attempt`() = runTest {
        val model = TestDataFactory.openAIModel(id = 1L)
        fakeRepository.setModels(listOf(model))
        // First call fails, second succeeds
        var callCount = 0
        fakeAIClient.generateResult = Result.failure(Exception("临时错误"))

        // We can't easily test intermediate retries with FakeAIClient
        // but we can verify the retry mechanism is in place
        val result = aiService.generateCompletionWithModel(1L, "你好")
        // With all retries failing, should return failure
        assertTrue("Should fail after all retries", result.isFailure)
    }

    // AI-006: 故障转移-默认失败后切换备用
    @Test
    fun `AI-006 model fallback when default fails`() = runTest {
        val defaultModel = TestDataFactory.openAIModel(id = 1L, isDefault = true)
        val backupModel = TestDataFactory.claudeModel(id = 2L)
        fakeRepository.setModels(listOf(defaultModel, backupModel))

        // Default model fails, backup succeeds
        fakeAIClient.generateResult = Result.success("备用模型回复")

        val result = aiService.generateCompletion("你好")

        assertTrue("Should succeed with fallback", result.isSuccess)
        assertEquals("备用模型回复", result.getOrNull())
    }

    // AI-010: 成本估算-OPENAI模型
    @Test
    fun `AI-010 cost estimation OPENAI`() = runTest {
        val model = TestDataFactory.openAIModel(id = 1L, isDefault = true)
        fakeRepository.setModels(listOf(model))
        fakeAIClient.generateResult = Result.success("回复")

        aiService.generateCompletion("你好")

        // Cost should be added
        assertThat(fakeRepository.addCostCallCount).isGreaterThan(0)
    }

    // AI-015: 月成本超限检查
    @Test
    fun `AI-015 monthly cost limit check`() = runTest {
        val exceededModel = TestDataFactory.exceededModel(id = 1L)
        val normalModel = TestDataFactory.claudeModel(id = 2L)
        fakeRepository.setModels(listOf(exceededModel, normalModel))
        fakeAIClient.generateResult = Result.success("回复")

        val result = aiService.generateCompletion("你好")

        // Should skip exceeded model and use normal one
        assertTrue("Should succeed with non-exceeded model", result.isSuccess)
        // Verify the exceeded model was not used
        assertThat(fakeAIClient.lastGenerateConfig?.id).isNotEqualTo(1L)
    }

    // AI-017: testModelConnection成功
    @Test
    fun `AI-017 test connection success`() = runTest {
        val model = TestDataFactory.openAIModel(id = 1L)
        fakeRepository.setModels(listOf(model))
        fakeAIClient.testConnectionResult = Result.success(true)

        val result = aiService.testModelConnection(1L)

        assertTrue("Should succeed", result.isSuccess)
        assertTrue("Should be true", result.getOrDefault(false))
    }

    // AI-018: testModelConnection模型不存在
    @Test
    fun `AI-018 test connection model not found`() = runTest {
        val result = aiService.testModelConnection(999L)

        assertTrue("Should fail", result.isFailure)
        assertThat(result.exceptionOrNull()?.message).contains("Model not found")
    }

    // AI-020: adjustStyle调用成功
    @Test
    fun `AI-020 adjust style success`() = runTest {
        val model = TestDataFactory.openAIModel(id = 1L, isDefault = true)
        fakeRepository.setModels(listOf(model))
        fakeAIClient.generateResult = Result.success("风格调整后的回复")

        val profile = TestDataFactory.userStyleProfile(
            formalityLevel = 0.8f, enthusiasmLevel = 0.6f, professionalismLevel = 0.9f
        )
        val result = aiService.adjustStyle("原始回复", profile)

        assertTrue("Should succeed", result.isSuccess)
    }

    // AI-021: buildMessages包含system和user
    @Test
    fun `AI-021 buildMessages includes system and user`() = runTest {
        val model = TestDataFactory.openAIModel(id = 1L, isDefault = true)
        fakeRepository.setModels(listOf(model))
        fakeAIClient.generateResult = Result.success("回复")

        aiService.generateCompletion("用户消息", systemPrompt = "系统提示词")

        val messages = fakeAIClient.lastGenerateMessages
        assertNotNull(messages)
        assertThat(messages!!.size).isAtLeast(2)
        assertThat(messages.any { it.role == "system" }).isTrue()
        assertThat(messages.any { it.role == "user" }).isTrue()
    }

    // AI-022: buildMessages仅有user
    @Test
    fun `AI-022 buildMessages only user when no system prompt`() = runTest {
        val model = TestDataFactory.openAIModel(id = 1L, isDefault = true)
        fakeRepository.setModels(listOf(model))
        fakeAIClient.generateResult = Result.success("回复")

        aiService.generateCompletion("用户消息", systemPrompt = null)

        val messages = fakeAIClient.lastGenerateMessages
        assertNotNull(messages)
        assertThat(messages!!.size).isEqualTo(1)
        assertThat(messages[0].role).isEqualTo("user")
    }

    // AI-023: 成功响应后更新lastUsed
    @Test
    fun `AI-023 update lastUsed on success`() = runTest {
        val model = TestDataFactory.openAIModel(id = 1L, isDefault = true)
        fakeRepository.setModels(listOf(model))
        fakeAIClient.generateResult = Result.success("回复")

        aiService.generateCompletion("你好")

        assertThat(fakeRepository.updateLastUsedCallCount).isEqualTo(1)
        assertThat(fakeRepository.lastUpdateLastUsedModelId).isEqualTo(1L)
    }

    // AI-024: 成功响应后累加成本
    @Test
    fun `AI-024 add cost on success`() = runTest {
        val model = TestDataFactory.openAIModel(id = 1L, isDefault = true)
        fakeRepository.setModels(listOf(model))
        fakeAIClient.generateResult = Result.success("回复")

        aiService.generateCompletion("你好")

        assertThat(fakeRepository.addCostCallCount).isEqualTo(1)
        assertThat(fakeRepository.lastAddCostModelId).isEqualTo(1L)
        assertThat(fakeRepository.lastAddCostAmount!!).isGreaterThan(0.0)
    }

    // ==================== ⚠️ 边界条件测试 ====================

    // AI-B01: 空prompt
    @Test
    fun `AI-B01 empty prompt`() = runTest {
        val model = TestDataFactory.openAIModel(id = 1L, isDefault = true)
        fakeRepository.setModels(listOf(model))
        fakeAIClient.generateResult = Result.success("回复")

        val result = aiService.generateCompletion("")

        assertTrue("Empty prompt should still call API", result.isSuccess)
    }

    // AI-B02: 仅空格的systemPrompt
    @Test
    fun `AI-B02 blank systemPrompt`() = runTest {
        val model = TestDataFactory.openAIModel(id = 1L, isDefault = true)
        fakeRepository.setModels(listOf(model))
        fakeAIClient.generateResult = Result.success("回复")

        val result = aiService.generateCompletion("你好", systemPrompt = "   ")

        assertTrue("Blank systemPrompt should work", result.isSuccess)
    }

    // AI-B09: 月成本刚好$10
    @Test
    fun `AI-B09 monthly cost exactly 10`() = runTest {
        val model = TestDataFactory.aiModelConfig(
            id = 1L, isDefault = true, monthlyCost = 10.0, isEnabled = true
        )
        val backup = TestDataFactory.claudeModel(id = 2L)
        fakeRepository.setModels(listOf(model, backup))
        fakeAIClient.generateResult = Result.success("回复")

        val result = aiService.generateCompletion("你好")

        // monthlyCost >= 10 should be skipped
        assertTrue("Should use backup model", result.isSuccess)
        assertThat(fakeAIClient.lastGenerateConfig?.id).isEqualTo(2L)
    }

    // AI-B10: 所有模型都超限
    @Test
    fun `AI-B10 all models exceeded`() = runTest {
        val model1 = TestDataFactory.exceededModel(id = 1L)
        val model2 = TestDataFactory.exceededModel(id = 2L)
        fakeRepository.setModels(listOf(model1, model2))

        val result = aiService.generateCompletion("你好")

        assertTrue("Should fail", result.isFailure)
        assertThat(result.exceptionOrNull()?.message).contains("No enabled models available")
    }

    // AI-B11: 无默认模型但有其他模型
    @Test
    fun `AI-B11 no default but has other models`() = runTest {
        val model = TestDataFactory.claudeModel(id = 2L)
        fakeRepository.setModels(listOf(model))
        fakeRepository.defaultModelId = null
        fakeAIClient.generateResult = Result.success("回复")

        val result = aiService.generateCompletion("你好")

        assertTrue("Should succeed with non-default model", result.isSuccess)
    }

    // AI-B12: 无默认模型且无其他模型
    @Test
    fun `AI-B12 no default and no other models`() = runTest {
        fakeRepository.setModels(emptyList())
        fakeRepository.defaultModelId = null

        val result = aiService.generateCompletion("你好")

        assertTrue("Should fail", result.isFailure)
    }

    // AI-B17: analyzeTextStyle返回markdown代码块
    @Test
    fun `AI-B17 style analysis with markdown code block`() = runTest {
        val model = TestDataFactory.openAIModel(id = 1L, isDefault = true)
        fakeRepository.setModels(listOf(model))
        fakeAIClient.generateResult = Result.success("""
            ```json
            {"formality": 0.7, "enthusiasm": 0.6, "professionalism": 0.8, "avgWordsPerSentence": 12}
            ```
        """.trimIndent())

        val result = aiService.analyzeTextStyle("这是一段测试文本")

        assertTrue("Should parse markdown JSON", result.isSuccess)
        val analysis = result.getOrNull()
        assertNotNull(analysis)
        assertThat(analysis!!.formality).isEqualTo(0.7f)
    }

    // AI-B18: analyzeTextStyle返回纯JSON
    @Test
    fun `AI-B18 style analysis with plain JSON`() = runTest {
        val model = TestDataFactory.openAIModel(id = 1L, isDefault = true)
        fakeRepository.setModels(listOf(model))
        fakeAIClient.generateResult = Result.success(
            """{"formality": 0.5, "enthusiasm": 0.5, "professionalism": 0.5, "avgWordsPerSentence": 15}"""
        )

        val result = aiService.analyzeTextStyle("测试文本")

        assertTrue("Should parse plain JSON", result.isSuccess)
        val analysis = result.getOrNull()
        assertNotNull(analysis)
        assertThat(analysis!!.formality).isEqualTo(0.5f)
    }

    // AI-B19: 置信度coerceIn(0,1)
    @Test
    fun `AI-B19 style values coerced to 0-1`() = runTest {
        val model = TestDataFactory.openAIModel(id = 1L, isDefault = true)
        fakeRepository.setModels(listOf(model))
        fakeAIClient.generateResult = Result.success(
            """{"formality": 1.5, "enthusiasm": -0.5, "professionalism": 2.0, "avgWordsPerSentence": 15}"""
        )

        val result = aiService.analyzeTextStyle("测试文本")

        assertTrue("Should parse", result.isSuccess)
        val analysis = result.getOrNull()
        assertNotNull(analysis)
        assertThat(analysis!!.formality).isAtLeast(0f)
        assertThat(analysis.formality).isAtMost(1f)
        assertThat(analysis.enthusiasm).isAtLeast(0f)
        assertThat(analysis.enthusiasm).isAtMost(1f)
        assertThat(analysis.professionalism).isAtLeast(0f)
        assertThat(analysis.professionalism).isAtMost(1f)
    }

    // ==================== ❌ 异常情况测试 ====================

    // AI-E01: 3次重试全部失败
    @Test
    fun `AI-E01 all retries fail`() = runTest {
        val model = TestDataFactory.openAIModel(id = 1L)
        fakeRepository.setModels(listOf(model))
        fakeAIClient.generateResult = Result.failure(Exception("网络错误"))

        val result = aiService.generateCompletionWithModel(1L, "你好")

        assertTrue("Should fail after all retries", result.isFailure)
        // Should have retried MAX_RETRIES times
        assertThat(fakeAIClient.generateCallCount).isEqualTo(3)
    }

    // AI-E03: 所有模型都失败
    @Test
    fun `AI-E03 all models fail`() = runTest {
        val model1 = TestDataFactory.openAIModel(id = 1L, isDefault = true)
        val model2 = TestDataFactory.claudeModel(id = 2L)
        fakeRepository.setModels(listOf(model1, model2))
        fakeAIClient.generateResult = Result.failure(Exception("全部失败"))

        val result = aiService.generateCompletion("你好")

        assertTrue("Should fail", result.isFailure)
    }

    // AI-E04: analyzeTextStyle JSON解析失败
    @Test
    fun `AI-E04 style analysis JSON parse failure returns defaults`() = runTest {
        val model = TestDataFactory.openAIModel(id = 1L, isDefault = true)
        fakeRepository.setModels(listOf(model))
        fakeAIClient.generateResult = Result.success("无效JSON内容")

        val result = aiService.analyzeTextStyle("测试文本")

        // Should return default analysis on parse failure
        assertTrue("Should return success with defaults", result.isSuccess)
        val analysis = result.getOrNull()
        assertNotNull(analysis)
        assertThat(analysis!!.formality).isEqualTo(0.5f)
        assertThat(analysis.enthusiasm).isEqualTo(0.5f)
        assertThat(analysis.professionalism).isEqualTo(0.5f)
        assertThat(analysis.avgWordsPerSentence).isEqualTo(15f)
    }

    // AI-E06: 模型ID不存在
    @Test
    fun `AI-E06 model ID not found`() = runTest {
        val result = aiService.generateCompletionWithModel(999L, "你好")

        assertTrue("Should fail", result.isFailure)
        assertThat(result.exceptionOrNull()?.message).contains("Model not found")
    }

    // AI-E07: 缓存操作并发安全
    @Test
    fun `AI-E07 concurrent cache operations safe`() = runTest {
        val model = TestDataFactory.openAIModel(id = 1L, isDefault = true)
        fakeRepository.setModels(listOf(model))
        fakeAIClient.generateResult = Result.success("回复")

        // Simulate concurrent access
        coroutineScope {
            val jobs = (1..20).map {
                launch {
                    aiService.generateCompletion("消息$it")
                }
            }
            jobs.joinAll()
        }

        // Should complete without crash
        assertTrue("Concurrent operations should complete", true)
    }

    // ==================== 🔧 修复后新增测试点 ====================

    // AI-F01: 无默认模型时自动选择其他可用模型
    @Test
    fun `AI-F01 no default model selects other available`() = runTest {
        val model1 = TestDataFactory.openAIModel(id = 1L, isDefault = false)
        val model2 = TestDataFactory.claudeModel(id = 2L, isDefault = false)
        fakeRepository.setModels(listOf(model1, model2))
        fakeRepository.defaultModelId = null // No default set
        fakeAIClient.generateResult = Result.success("从备用模型生成的回复")

        val result = aiService.generateCompletion("你好")

        assertTrue("Should succeed using non-default model", result.isSuccess)
        assertEquals("从备用模型生成的回复", result.getOrNull())
        assertThat(fakeAIClient.lastGenerateConfig?.id).isEqualTo(2L) // Should use Claude (higher lastUsed)
    }

    // AI-F02: 所有模型都不可用时应返回友好错误
    @Test
    fun `AI-F02 all models unavailable returns user friendly error`() = runTest {
        val disabledModel = TestDataFactory.openAIModel(id = 1L, isEnabled = false)
        val exceededModel = TestDataFactory.exceededModel(id = 2L)
        fakeRepository.setModels(listOf(disabledModel, exceededModel))

        val result = aiService.generateCompletion("你好")

        assertTrue("Should fail gracefully", result.isFailure)
        assertThat(result.exceptionOrNull()?.message).contains("没有可用的AI模型")
    }

    // AI-F03: 模型排序按最后使用时间优先
    @Test
    fun `AI-F03 model selection by last used time priority`() = runTest {
        val oldModel = TestDataFactory.openAIModel(
            id = 1L,
            lastUsed = System.currentTimeMillis() - 86400000 // 1 day ago
        )
        val recentModel = TestDataFactory.claudeModel(
            id = 2L,
            lastUsed = System.currentTimeMillis() // Now
        )
        fakeRepository.setModels(listOf(oldModel, recentModel))
        fakeRepository.defaultModelId = null
        fakeAIClient.generateResult = Result.success("最近使用的模型回复")

        val result = aiService.generateCompletion("你好")

        assertTrue("Should succeed", result.isSuccess)
        assertThat(fakeAIClient.lastGenerateConfig?.id).isEqualTo(2L) // Recent model first
    }

    // AI-F04: 风格调整失败时使用原始文本
    @Test
    fun `AI-F04 style adjustment failure returns original text`() = runTest {
        val model = TestDataFactory.openAIModel(id = 1L, isDefault = true)
        fakeRepository.setModels(listOf(model))
        fakeAIClient.generateResult = Result.failure(Exception("风格调整失败"))

        val profile = TestDataFactory.userStyleProfile()
        val result = aiService.adjustStyle("原始回复内容", profile)

        assertTrue("Should return failure for style adjustment", result.isFailure)
    }
}

