package com.csbaby.kefu.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.csbaby.kefu.data.local.AppDatabase
import com.csbaby.kefu.data.local.entity.AIModelConfigEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Comprehensive Room database tests for AIModelConfigDao.
 */
@RunWith(AndroidJUnit4::class)
class AIModelConfigDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: AIModelConfigDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.aiModelConfigDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun `insert and retrieve single model`() = runTest {
        // Given
        val model = AIModelConfigEntity(
            id = 1L,
            modelType = "OPENAI",
            modelName = "gpt-3.5-turbo",
            model = "gpt-3.5-turbo",
            apiKey = "test-key",
            apiEndpoint = "https://api.openai.com/v1/chat/completions"
        )

        // When
        val insertedId = dao.insertModel(model)
        val retrievedModel = dao.getModelById(insertedId)

        // Then
        assertEquals(1L, insertedId)
        assertNotNull(retrievedModel)
        assertEquals("OPENAI", retrievedModel!!.modelType)
        assertEquals("gpt-3.5-turbo", retrievedModel.modelName)
    }

    @Test
    fun `insert duplicate model with conflict strategy`() = runTest {
        // Given
        val model1 = AIModelConfigEntity(
            id = 1L,
            modelType = "OPENAI",
            modelName = "gpt-3.5-turbo",
            model = "gpt-3.5-turbo",
            apiKey = "key1",
            apiEndpoint = "https://api.openai.com/v1/chat/completions"
        )

        val model2 = AIModelConfigEntity(
            id = 1L, // Same ID
            modelType = "OPENAI",
            modelName = "gpt-3.5-turbo-updated",
            model = "gpt-3.5-turbo-updated",
            apiKey = "key2",
            apiEndpoint = "https://api.openai.com/v1/chat/completions"
        )

        // When
        val id1 = dao.insertModel(model1)
        val id2 = dao.insertModel(model2) // Should replace

        val finalModel = dao.getModelById(id1)

        // Then
        assertEquals(id1, id2) // Same ID returned
        assertNotNull(finalModel)
        assertEquals("gpt-3.5-turbo-updated", finalModel!!.modelName)
        assertEquals("key2", finalModel.apiKey)
    }

    @Test
    fun `get all models flow emits correctly`() = runTest {
        // Given
        val model1 = createOpenAIModel("gpt-3.5-turbo", false)
        val model2 = createClaudeModel("claude-3-haiku", true) // Default
        val model3 = createZhipuModel("glm-4", false)

        dao.insertModel(model1)
        dao.insertModel(model2)
        dao.insertModel(model3)

        // When
        val modelsFlow = dao.getAllModels()
        val models = modelsFlow.first()

        // Then
        assertEquals(3, models.size)
        // Should be ordered by isDefault DESC, lastUsed DESC
        assertTrue(models[0].isDefault) // Default model first
        assertEquals("claude-3-haiku", models[0].modelName)
    }

    @Test
    fun `get enabled models only`() = runTest {
        // Given
        val enabledModel = createOpenAIModel("gpt-3.5-turbo", false, true)
        val disabledModel = createClaudeModel("claude-3-opus", false, false)

        dao.insertModel(enabledModel)
        dao.insertModel(disabledModel)

        // When
        val enabledModelsFlow = dao.getEnabledModels()
        val enabledModels = enabledModelsFlow.first()

        // Then
        assertEquals(1, enabledModels.size)
        assertEquals("gpt-3.5-turbo", enabledModels[0].modelName)
    }

    @Test
    fun `set and clear default model`() = runTest {
        // Given
        val model1 = createOpenAIModel("gpt-3.5-turbo", false)
        val model2 = createClaudeModel("claude-3-haiku", false)
        val model3 = createZhipuModel("glm-4", false)

        dao.insertModel(model1)
        dao.insertModel(model2)
        dao.insertModel(model3)

        // When
        dao.setDefaultModel(model2.id)
        val defaultModel = dao.getDefaultModel()

        // Then
        assertNotNull(defaultModel)
        assertEquals("claude-3-haiku", defaultModel!!.modelName)
        assertTrue(defaultModel.isDefault)

        // When - Clear default
        dao.clearDefaultModel()
        val noDefaultModel = dao.getDefaultModel()

        // Then
        assertNull(noDefaultModel)
    }

    @Test
    fun `update model last used timestamp`() = runTest {
        // Given
        val model = createOpenAIModel("gpt-3.5-turbo", false)
        val originalTime = System.currentTimeMillis()
        Thread.sleep(10) // Ensure time difference

        dao.insertModel(model)
        val newTimestamp = System.currentTimeMillis()

        // When
        dao.updateLastUsed(model.id, newTimestamp)
        val updatedModel = dao.getModelById(model.id)

        // Then
        assertNotNull(updatedModel)
        assertEquals(newTimestamp, updatedModel!!.lastUsed)
    }

    @Test
    fun `track monthly cost correctly`() = runTest {
        // Given
        val model = createOpenAIModel("gpt-3.5-turbo", false)
        dao.insertModel(model)

        // When
        dao.addCost(model.id, 15.5)
        val updatedModel = dao.getModelById(model.id)

        // Then
        assertNotNull(updatedModel)
        assertEquals(15.5, updatedModel!!.monthlyCost, 0.01)
    }

    @Test
    fun `delete model by id`() = runTest {
        // Given
        val model = createOpenAIModel("gpt-3.5-turbo", false)
        dao.insertModel(model)

        // When
        dao.deleteById(model.id)
        val deletedModel = dao.getModelById(model.id)

        // Then
        assertNull(deletedModel)
    }

    @Test
    fun `getAllModelsList returns complete list`() = runTest {
        // Given
        val model1 = createOpenAIModel("gpt-3.5-turbo", false)
        val model2 = createClaudeModel("claude-3-haiku", true)
        val model3 = createZhipuModel("glm-4", false)

        dao.insertModel(model1)
        dao.insertModel(model2)
        dao.insertModel(model3)

        // When
        val modelsList = dao.getAllModelsList()

        // Then
        assertEquals(3, modelsList.size)
        // Should be ordered by isDefault DESC, lastUsed DESC
        assertEquals("claude-3-haiku", modelsList[0].modelName)
        assertTrue(modelsList[0].isDefault)
    }

    @Test
    fun `flow updates when data changes`() = runTest {
        // Given
        val modelsFlow = dao.getAllModels()
        val initialModels = modelsFlow.first()
        assertEquals(0, initialModels.size)

        val model = createOpenAIModel("gpt-3.5-turbo", false)
        dao.insertModel(model)

        // When
        val updatedModels = modelsFlow.first()

        // Then
        assertEquals(1, updatedModels.size)
        assertEquals("gpt-3.5-turbo", updatedModels[0].modelName)
    }

    // Helper functions
    private fun createOpenAIModel(
        modelName: String,
        isDefault: Boolean,
        isEnabled: Boolean = true
    ): AIModelConfigEntity {
        return AIModelConfigEntity(
            modelType = "OPENAI",
            modelName = modelName,
            model = modelName,
            apiKey = "openai-key-$modelName",
            apiEndpoint = "https://api.openai.com/v1/chat/completions",
            isDefault = isDefault,
            isEnabled = isEnabled
        )
    }

    private fun createClaudeModel(
        modelName: String,
        isDefault: Boolean,
        isEnabled: Boolean = true
    ): AIModelConfigEntity {
        return AIModelConfigEntity(
            modelType = "CLAUDE",
            modelName = modelName,
            model = modelName,
            apiKey = "claude-key-$modelName",
            apiEndpoint = "https://api.anthropic.com/v1/messages",
            isDefault = isDefault,
            isEnabled = isEnabled
        )
    }

    private fun createZhipuModel(
        modelName: String,
        isDefault: Boolean,
        isEnabled: Boolean = true
    ): AIModelConfigEntity {
        return AIModelConfigEntity(
            modelType = "ZHIPU",
            modelName = modelName,
            model = modelName,
            apiKey = "zhipu-key-$modelName",
            apiEndpoint = "https://open.bigmodel.cn/api/paas/v4/chat/completions",
            isDefault = isDefault,
            isEnabled = isEnabled
        )
    }
}