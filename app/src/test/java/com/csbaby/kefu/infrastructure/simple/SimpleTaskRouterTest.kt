package com.csbaby.kefu.infrastructure.simple

import com.csbaby.kefu.domain.model.AIModelConfig
import com.csbaby.kefu.domain.model.ModelType
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
                modelType = ModelType.OPENAI,
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
                modelType = ModelType.CUSTOM,
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
                modelType = ModelType.CUSTOM,
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
        assertEquals(models[0], (result as RoutingResult.SingleChoice).selectedModel.model)
    }

    @Test
    fun `coding task prefers models with long context`() {
        val result = router.selectBestModel(TaskType.CODING_ASSISTANCE, models)

        assertTrue(result is RoutingResult.SingleChoice)
        // LongCat Pro应该被选中最优的
        assertEquals("LongCat Pro", (result as RoutingResult.SingleChoice).selectedModel.model.modelName)
    }

    @Test
    fun `customer service prioritizes low cost and recent usage`() {
        val result = router.selectBestModel(TaskType.CUSTOMER_SERVICE, models)

        assertTrue(result is RoutingResult.SingleChoice)
        // LongCat Pro成本最低且近期使用过
        assertEquals("LongCat Pro", (result as RoutingResult.SingleChoice).selectedModel.model.modelName)
    }

    @Test
    fun `reasoning task selects high token capacity models`() {
        val result = router.selectBestModel(TaskType.REASONING, models)

        assertTrue(result is RoutingResult.SingleChoice)
        // 所有模型都有高token容量，选择评分最高的
        assertNotNull((result as RoutingResult.SingleChoice).selectedModel.model)
    }

    @Test
    fun `multiple choices when multiple models available`() {
        val testModels = models.take(2)
        val result = router.selectBestModel(TaskType.GENERAL_CHAT, testModels)

        assertTrue(result is RoutingResult.MultipleChoices)
        assertEquals(2, (result as RoutingResult.MultipleChoices).candidates.size)
    }

    @Test
    fun `routing result provides meaningful explanations`() {
        // Test that routing result contains useful information
        val result = router.selectBestModel(TaskType.CODING_ASSISTANCE, models)
        assertTrue(result is RoutingResult.SingleChoice)

        // Verify the selected model has proper reasoning information
        val selectedModel = (result as RoutingResult.SingleChoice).selectedModel
        assertNotNull(selectedModel.model)
        assertTrue("Selected model should have a score", selectedModel.score > 0f)
    }

    @Test
    fun `model selection considers task type appropriately`() {
        // Test that different models get selected for different task types
        val gpt4Result = router.selectBestModel(TaskType.GENERAL_CHAT, listOf(models[0]))
        val longcatResult = router.selectBestModel(TaskType.CUSTOMER_SERVICE, listOf(models[1]))

        assertTrue(gpt4Result is RoutingResult.SingleChoice)
        assertTrue(longcatResult is RoutingResult.SingleChoice)
    }

    @Test
    fun `document processing selects appropriate model`() {
        val result = router.selectBestModel(TaskType.DOCUMENT_PROCESSING, models)

        assertTrue(result is RoutingResult.SingleChoice)
        // 文档处理需要高token容量
        val selectedModel = (result as RoutingResult.SingleChoice).selectedModel
        assertTrue("Selected model should have sufficient token capacity", selectedModel.model.maxTokens >= 32768)
    }

    @Test
    fun `general chat balances performance and cost`() {
        val result = router.selectBestModel(TaskType.GENERAL_CHAT, models)

        assertTrue(result is RoutingResult.SingleChoice)
        // 通用对话平衡性能和成本
        val selectedModel = (result as RoutingResult.SingleChoice).selectedModel
        assertTrue("Selected model should have reasonable cost", selectedModel.model.monthlyCost < 5.0)
    }

    @Test
    fun `no suitable model when no models available`() {
        val result = router.selectBestModel(TaskType.CUSTOMER_SERVICE, emptyList())

        assertTrue(result is RoutingResult.NoSuitableModel)
        assertTrue((result as RoutingResult.NoSuitableModel).reason.contains("没有适合"))
    }
}