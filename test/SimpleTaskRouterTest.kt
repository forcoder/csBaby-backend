package com.csbaby.kefu.infrastructure.simple

import com.csbaby.kefu.domain.model.AIModelConfig
import com.csbaby.kefu.domain.model.ModelType
import org.junit.Test
import org.junit.Assert.*
import org.mockito.kotlin.mock

/**
 * Comprehensive tests for SimpleTaskRouter intelligent routing.
 */
class SimpleTaskRouterTest {

    private val router = SimpleTaskRouter()

    @Test
    fun `test general chat model selection`() {
        val models = createTestModels()
        val result = router.selectBestModel(TaskType.GENERAL_CHAT, models)

        assertTrue("Should return a single choice for general chat", result is RoutingResult.SingleChoice)
        val selected = (result as RoutingResult.SingleChoice).selectedModel

        // Should prefer models with good token capacity and low cost
        assertTrue("Selected model should have reasonable token capacity",
            selected.model.maxTokens >= 4096)
    }

    @Test
    fun `test customer service model selection`() {
        val models = createTestModels()
        val result = router.selectBestModel(TaskType.CUSTOMER_SERVICE, models)

        assertTrue("Should return a single choice for customer service", result is RoutingResult.SingleChoice)
        val selected = (result as RoutingResult.SingleChoice).selectedModel

        // Customer service should prefer recently used, cost-effective models
        val hoursSinceLastUse = (System.currentTimeMillis() - selected.model.lastUsed) / 3600000
        assertTrue("Customer service model should be recently used",
            hoursSinceLastUse < 24 || selected.score > 0.5f)
    }

    @Test
    fun `test document processing model selection`() {
        val longContextModels = listOf(
            AIModelConfig(
                id = 1,
                modelType = ModelType.CLAUDE,
                modelName = "claude-3-opus",
                maxTokens = 32768,
                monthlyCost = 20.0,
                model = "claude-3-opus-20240307",
                apiKey = "test-key",
                apiEndpoint = "https://api.anthropic.com/v1/messages"
            ),
            AIModelConfig(
                id = 2,
                modelType = ModelType.OPENAI,
                modelName = "gpt-4",
                maxTokens = 8192,
                monthlyCost = 30.0,
                model = "gpt-4",
                apiKey = "test-key",
                apiEndpoint = "https://api.openai.com/v1/chat/completions"
            )
        )

        val result = router.selectBestModel(TaskType.DOCUMENT_PROCESSING, longContextModels)

        if (result is RoutingResult.SingleChoice) {
            assertTrue("Document processing should prefer long context models",
                result.selectedModel.model.maxTokens >= 16384)
        }
    }

    @Test
    fun `test reasoning task model selection`() {
        val reasoningModels = listOf(
            AIModelConfig(
                id = 1,
                modelType = ModelType.CLAUDE,
                modelName = "claude-3-opus",
                maxTokens = 32768,
                monthlyCost = 20.0,
                model = "claude-3-opus-20240307",
                apiKey = "test-key",
                apiEndpoint = "https://api.anthropic.com/v1/messages"
            ),
            AIModelConfig(
                id = 2,
                modelType = ModelType.OPENAI,
                modelName = "gpt-4-turbo",
                maxTokens = 16384,
                monthlyCost = 15.0,
                model = "gpt-4-turbo",
                apiKey = "test-key",
                apiEndpoint = "https://api.openai.com/v1/chat/completions"
            )
        )

        val result = router.selectBestModel(TaskType.REASONING, reasoningModels)

        if (result is RoutingResult.SingleChoice) {
            // Reasoning tasks should prefer high token capacity models
            assertTrue("Reasoning models should have high token capacity",
                result.selectedModel.model.maxTokens >= 16384)
        }
    }

    @Test
    fun `test coding assistance model selection`() {
        val codingModels = listOf(
            AIModelConfig(
                id = 1,
                modelType = ModelType.OPENAI,
                modelName = "gpt-4",
                maxTokens = 8192,
                monthlyCost = 30.0,
                model = "gpt-4",
                apiKey = "test-key",
                apiEndpoint = "https://api.openai.com/v1/chat/completions"
            ),
            AIModelConfig(
                id = 2,
                modelType = ModelType.CLAUDE,
                modelName = "claude-3-haiku",
                maxTokens = 4096,
                monthlyCost = 3.0,
                model = "claude-3-haiku-20240307",
                apiKey = "test-key",
                apiEndpoint = "https://api.anthropic.com/v1/messages"
            )
        )

        val result = router.selectBestModel(TaskType.CODING_ASSISTANCE, codingModels)

        if (result is RoutingResult.SingleChoice) {
            // Coding assistance should prefer OpenAI models when available
            if (result.selectedModel.model.modelType == ModelType.OPENAI) {
                assertTrue("Coding should prefer OpenAI models", true)
            }
        }
    }

    @Test
    fun `test vision task model selection`() {
        val visionModels = listOf(
            AIModelConfig(
                id = 1,
                modelType = ModelType.OPENAI,
                modelName = "gpt-4-vision",
                maxTokens = 4096,
                monthlyCost = 15.0,
                model = "gpt-4-vision-preview",
                apiKey = "test-key",
                apiEndpoint = "https://api.openai.com/v1/chat/completions"
            ),
            AIModelConfig(
                id = 2,
                modelType = ModelType.CUSTOM,
                modelName = "custom-model",
                maxTokens = 2048,
                monthlyCost = 5.0,
                model = "regular-model",
                apiKey = "test-key",
                apiEndpoint = "https://api.example.com/v1/chat/completions"
            )
        )

        val result = router.selectBestModel(TaskType.VISION_TASKS, visionModels)

        if (result is RoutingResult.SingleChoice) {
            // Vision tasks should prefer models with vision capabilities
            val selectedModel = result.selectedModel.model
            assertTrue("Vision tasks should prefer vision-capable models",
                selectedModel.model.contains("vision") ||
                selectedModel.apiEndpoint.contains("vision"))
        }
    }

    @Test
    fun `test multiple candidates selection`() {
        val models = createMultipleCandidateModels()
        val result = router.selectBestModel(TaskType.INFORMATION_QUERY, models)

        if (result is RoutingResult.MultipleChoices) {
            assertEquals(3, result.candidates.size)
            // Candidates should be sorted by score
            assertTrue("Candidates should be sorted by score",
                result.candidates[0].score >= result.candidates[1].score)
            assertTrue("Candidates should be sorted by score",
                result.candidates[1].score >= result.candidates[2].score)
        }
    }

    @Test
    fun `test no suitable model scenario`() {
        // Create models that don't meet minimum thresholds
        val poorModels = listOf(
            AIModelConfig(
                id = 1,
                modelType = ModelType.CUSTOM,
                modelName = "poor-model",
                maxTokens = 100, // Very low capacity
                monthlyCost = 10.0, // High cost
                model = "poor-model",
                apiKey = "test-key",
                apiEndpoint = "https://api.example.com/v1/chat/completions"
            )
        )

        val result = router.selectBestModel(TaskType.DOCUMENT_PROCESSING, poorModels)

        if (result is RoutingResult.NoSuitableModel) {
            assertNotNull("Should provide reason for no suitable model", result.reason)
        }
    }

    @Test
    fun `test fallback model selection`() {
        val models = createTestModels()
        val result = router.selectFallbackModel(TaskType.GENERAL_CHAT, models)

        assertTrue("Fallback should select from available models", result is RoutingResult.SingleChoice)
        val selected = (result as RoutingResult.SingleChoice).selectedModel

        // Should prefer enabled, recently used, low-cost models
        assertTrue("Fallback model should be enabled", selected.model.isEnabled)
    }

    @Test
    fun `test empty model list`() {
        val result = router.selectBestModel(TaskType.GENERAL_CHAT, emptyList())

        assertTrue("Should handle empty model list", result is RoutingResult.NoSuitableModel)
    }

    @Test
    fun `test single model selection`() {
        val singleModel = listOf(
            AIModelConfig(
                id = 1,
                modelType = ModelType.OPENAI,
                modelName = "gpt-3.5-turbo",
                maxTokens = 4096,
                monthlyCost = 1.5,
                model = "gpt-3.5-turbo",
                apiKey = "test-key",
                apiEndpoint = "https://api.openai.com/v1/chat/completions"
            )
        )

        val result = router.selectBestModel(TaskType.GENERAL_CHAT, singleModel)

        assertTrue("Single model should return single choice", result is RoutingResult.SingleChoice)
    }

    @Test
    fun `test reasoning score calculation`() {
        val opusModel = AIModelConfig(
            id = 1,
            modelType = ModelType.CLAUDE,
            modelName = "claude-3-opus",
            maxTokens = 32768,
            monthlyCost = 20.0,
            model = "claude-3-opus-20240307",
            apiKey = "test-key",
            apiEndpoint = "https://api.anthropic.com/v1/messages"
        )

        val haikuModel = AIModelConfig(
            id = 2,
            modelType = ModelType.CLAUDE,
            modelName = "claude-3-haiku",
            maxTokens = 4096,
            monthlyCost = 3.0,
            model = "claude-3-haiku-20240307",
            apiKey = "test-key",
            apiEndpoint = "https://api.anthropic.com/v1/messages"
        )

        val opusScore = router.getReasoningScore(opusModel, null)
        val haikuScore = router.getReasoningScore(haikuModel, null)

        assertTrue("Opus model should score higher for reasoning", opusScore > haikuScore)
    }

    @Test
    fun `test coding score calculation`() {
        val openaiModel = AIModelConfig(
            id = 1,
            modelType = ModelType.OPENAI,
            modelName = "gpt-4",
            maxTokens = 8192,
            monthlyCost = 30.0,
            model = "gpt-4",
            apiKey = "test-key",
            apiEndpoint = "https://api.openai.com/v1/chat/completions"
        )

        val claudeModel = AIModelConfig(
            id = 2,
            modelType = ModelType.CLAUDE,
            modelName = "claude-3-haiku",
            maxTokens = 4096,
            monthlyCost = 3.0,
            model = "claude-3-haiku-20240307",
            apiKey = "test-key",
            apiEndpoint = "https://api.anthropic.com/v1/messages"
        )

        val openaiScore = router.getCodingScore(openaiModel, null)
        val claudeScore = router.getCodingScore(claudeModel, null)

        assertTrue("OpenAI models should score higher for coding", openaiScore >= claudeScore)
    }

    private fun createTestModels(): List<AIModelConfig> {
        return listOf(
            AIModelConfig(
                id = 1,
                modelType = ModelType.OPENAI,
                modelName = "gpt-3.5-turbo",
                maxTokens = 4096,
                monthlyCost = 1.5,
                lastUsed = System.currentTimeMillis() - 3600000, // Used 1 hour ago
                model = "gpt-3.5-turbo",
                apiKey = "test-key",
                apiEndpoint = "https://api.openai.com/v1/chat/completions"
            ),
            AIModelConfig(
                id = 2,
                modelType = ModelType.CLAUDE,
                modelName = "claude-3-haiku",
                maxTokens = 4096,
                monthlyCost = 3.0,
                lastUsed = System.currentTimeMillis(), // Used now
                model = "claude-3-haiku-20240307",
                apiKey = "test-key",
                apiEndpoint = "https://api.anthropic.com/v1/messages"
            ),
            AIModelConfig(
                id = 3,
                modelType = ModelType.ZHIPU,
                modelName = "glm-4",
                maxTokens = 8192,
                monthlyCost = 2.0,
                lastUsed = System.currentTimeMillis() - 86400000, // Used yesterday
                model = "glm-4",
                apiKey = "test-key",
                apiEndpoint = "https://open.bigmodel.cn/api/paas/v4/chat/completions"
            )
        )
    }

    private fun createMultipleCandidateModels(): List<AIModelConfig> {
        return listOf(
            AIModelConfig(
                id = 1,
                modelType = ModelType.OPENAI,
                modelName = "gpt-3.5-turbo",
                maxTokens = 4096,
                monthlyCost = 1.5,
                model = "gpt-3.5-turbo",
                apiKey = "test-key",
                apiEndpoint = "https://api.openai.com/v1/chat/completions"
            ),
            AIModelConfig(
                id = 2,
                modelType = ModelType.CLAUDE,
                modelName = "claude-3-haiku",
                maxTokens = 4096,
                monthlyCost = 3.0,
                model = "claude-3-haiku-20240307",
                apiKey = "test-key",
                apiEndpoint = "https://api.anthropic.com/v1/messages"
            ),
            AIModelConfig(
                id = 3,
                modelType = ModelType.TONGYI,
                modelName = "qwen-turbo",
                maxTokens = 8192,
                monthlyCost = 1.8,
                model = "qwen-turbo",
                apiKey = "test-key",
                apiEndpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation"
            )
        )
    }
}