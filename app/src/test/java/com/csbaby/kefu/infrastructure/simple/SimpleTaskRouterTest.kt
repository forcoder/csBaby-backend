package com.csbaby.kefu.infrastructure.simple

import com.csbaby.kefu.domain.model.AIModelConfig
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SimpleTaskRouterTest {

    private lateinit var router: SimpleTaskRouter
    private lateinit var models: List<AIModelConfig>

    @Before
    fun setup() {
        router = SimpleTaskRouter()

        // 创建测试模型
        models = listOf(
            AIModelConfig(
                model = "gpt-4",
                modelName = "GPT-4",
                apiKey = "key1",
                apiEndpoint = "https://api.openai.com/v1/chat/completions",
                maxTokens = 32768,
                monthlyCost = 5.0,
                lastUsed = System.currentTimeMillis(),
                isEnabled = true
            ),
            AIModelConfig(
                model = "longcat-pro",
                modelName = "LongCat Pro",
                apiKey = "key2",
                apiEndpoint = "https://api.longcat.ai/v1/chat/completions",
                maxTokens = 65536,
                monthlyCost = 2.0,
                lastUsed = System.currentTimeMillis() - 1800000, // 30分钟前
                isEnabled = true
            ),
            AIModelConfig(
                model = "deepseek-reasoner",
                modelName = "DeepSeek Reasoner",
                apiKey = "key3",
                apiEndpoint = "https://api.deepseek.com/v1/chat/completions",
                maxTokens = 64000,
                monthlyCost = 3.0,
                lastUsed = System.currentTimeMillis() - 3600000, // 1小时前
                isEnabled = true
            )
        )
    }

    @Test
    fun `selectBestModel returns single choice when one model available`() {
        val result = router.selectBestModel(TaskType.GENERAL_CHAT, models.take(1))

        assertTrue(result is RoutingResult.SingleChoice)
        assertEquals(models[0], result.selectedModel.model)
    }

    @Test
    fun `coding task prefers models with long context`() {
        val result = router.selectBestModel(TaskType.CODING_ASSISTANCE, models)

        assertTrue(result is RoutingResult.SingleChoice)
        // LongCat Pro应该被选中最优的
        assertEquals("LongCat Pro", result.selectedModel.model.modelName)
    }

    @Test
    fun `customer service prioritizes low cost and recent usage`() {
        val result = router.selectBestModel(TaskType.CUSTOMER_SERVICE, models)

        assertTrue(result is RoutingResult.SingleChoice)
        // LongCat Pro成本最低且近期使用过
        assertEquals("LongCat Pro", result.selectedModel.model.modelName)
    }

    @Test
    fun `reasoning task selects high token capacity models`() {
        val result = router.selectBestModel(TaskType.REASONING, models)

        assertTrue(result is RoutingResult.SingleChoice)
        // 所有模型都有高token容量，选择评分最高的
        assertNotNull(result.selectedModel.model)
    }

    @Test
    fun `multiple choices when multiple models available`() {
        val testModels = models.take(2)
        val result = router.selectBestModel(TaskType.GENERAL_CHAT, testModels)

        assertTrue(result is RoutingResult.MultipleChoices)
        assertEquals(2, result.candidates.size)
    }

    @Test
    fun `generateRoutingReasoning provides meaningful explanations`() {
        val model = models[0]
        val reasoning = router.generateRoutingReasoning(0.8f, model, TaskType.CODING_ASSISTANCE)

        assertTrue("Should contain reasoning", reasoning.isNotEmpty())
        assertTrue("Should mention coding capability", reasoning.contains("代码"))
    }

    @Test
    fun `calculateModelScore gives higher score for suitable tasks`() {
        val gpt4Model = models[0]
        val longcatModel = models[1]

        val gpt4Score = router.calculateModelScore(gpt4Model, TaskType.CODING_ASSISTANCE)
        val longcatScore = router.calculateModelScore(longcatModel, TaskType.CODING_ASSISTANCE)

        assertTrue("GPT-4 should have good coding score", gpt4Score > 0.3f)
        assertTrue("LongCat should excel at coding", longcatScore > gpt4Score)
    }

    @Test
    fun `document processing prefers high token capacity models`() {
        val result = router.selectBestModel(TaskType.DOCUMENT_PROCESSING, models)

        assertTrue(result is RoutingResult.SingleChoice)
        // 文档处理需要高token容量
        assertTrue(result.selectedModel.model.maxTokens >= 32768)
    }

    @Test
    fun `general chat balances performance and cost`() {
        val result = router.selectBestModel(TaskType.GENERAL_CHAT, models)

        assertTrue(result is RoutingResult.SingleChoice)
        // 通用对话平衡性能和成本
        assertTrue(result.selectedModel.model.monthlyCost < 5.0)
    }

    @Test
    fun `no suitable model when no models available`() {
        val result = router.selectBestModel(TaskType.CUSTOMER_SERVICE, emptyList())

        assertTrue(result is RoutingResult.NoSuitableModel)
        assertTrue(result.reason.contains("没有适合"))
    }
}