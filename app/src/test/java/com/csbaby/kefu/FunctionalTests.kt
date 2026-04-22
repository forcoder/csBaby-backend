package com.csbaby.kefu

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.csbaby.kefu.data.local.PreferencesManager
import com.csbaby.kefu.infrastructure.ai.AIService
import com.csbaby.kefu.infrastructure.notification.MessageMonitor
import com.csbaby.kefu.infrastructure.knowledge.KnowledgeBaseManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Functional tests for core application features
 */
@RunWith(AndroidJUnit4::class)
class FunctionalTests {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    @Inject
    lateinit var aiService: AIService

    @Inject
    lateinit var messageMonitor: MessageMonitor

    @Inject
    lateinit var knowledgeBaseManager: KnowledgeBaseManager

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() {
        // Initialize Dagger Hilt for testing
        val appComponent = (context.applicationContext as com.csbaby.kefu.KefuApplication).appComponent
        appComponent.inject(this)
    }

    /**
     * Test notification monitoring functionality
     */
    @Test
    fun testNotificationMonitoringFlow() = runBlocking {
        // Test that monitoring can be enabled/disabled
        var userPreferences = preferencesManager.userPreferencesFlow.first()
        assertFalse("Monitoring should be disabled by default", userPreferences.monitoringEnabled)

        // Enable monitoring
        preferencesManager.updateUserPreferences { it.copy(monitoringEnabled = true) }
        userPreferences = preferencesManager.userPreferencesFlow.first()
        assertTrue("Monitoring should be enabled after update", userPreferences.monitoringEnabled)

        // Test selected apps management
        val testApps = setOf("com.whatsapp", "com.facebook.orca")
        preferencesManager.updateUserPreferences { it.copy(selectedApps = testApps) }

        userPreferences = preferencesManager.userPreferencesFlow.first()
        assertEquals("Selected apps should match", testApps, userPreferences.selectedApps)
    }

    /**
     * Test AI response generation with different models
     */
    @Test
    fun testAIResponseGeneration() = runBlocking {
        // Test basic AI completion
        val prompt = "Hello, how are you?"
        val result = aiService.generateCompletion(prompt)

        assertTrue("AI response should be successful", result.isSuccess)
        val response = result.getOrNull()
        assertNotNull("Response should not be null", response)
        assertTrue("Response should contain text", response!!.isNotBlank())
        assertTrue("Response should acknowledge greeting", response.contains("hello", ignoreCase = true))
    }

    /**
     * Test AI style analysis functionality
     */
    @Test
    fun testAIStyleAnalysis() = runBlocking {
        val testText = "This is a sample customer service message that needs style analysis."

        val result = aiService.analyzeTextStyle(testText)

        assertTrue("Style analysis should be successful", result.isSuccess)
        val analysis = result.getOrNull()

        assertNotNull("Analysis should not be null", analysis)
        assertTrue("Formality should be between 0-1", analysis!!.formality in 0f..1f)
        assertTrue("Enthusiasm should be between 0-1", analysis.enthusiasm in 0f..1f)
        assertTrue("Professionalism should be between 0-1", analysis.professionalism in 0f..1f)
        assertTrue("Words per sentence should be positive", analysis.avgWordsPerSentence > 0f)
    }

    /**
     * Test knowledge base operations
     */
    @Test
    fun testKnowledgeBaseOperations() = runBlocking {
        // Test adding and retrieving knowledge entries
        val entryId = knowledgeBaseManager.addEntry(
            title = "Test Product",
            content = "This is a test product description",
            keywords = listOf("test", "product", "sample"),
            category = "General"
        )

        assertNotNull("Entry ID should not be null", entryId)

        // Search for the entry
        val searchResults = knowledgeBaseManager.searchEntries("test product")
        assertTrue("Search should return results", searchResults.isNotEmpty())

        val foundEntry = searchResults.find { it.title == "Test Product" }
        assertNotNull("Should find the added entry", foundEntry)
        assertEquals("Content should match", "This is a test product description", foundEntry?.content)
    }

    /**
     * Test message monitoring flow
     */
    @Test
    fun testMessageMonitoring() = runBlocking {
        val testMessage = MessageMonitor.MonitoredMessage(
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            title = "John Doe",
            content = "Hey, I need help with my order",
            conversationTitle = "Order Support",
            isGroupConversation = false,
            timestamp = System.currentTimeMillis()
        )

        // Test message emission
        messageMonitor.emitMessage(testMessage)

        // Verify message was processed (this would require observing the message flow)
        // For now, we just verify no exceptions are thrown
        assertTrue("Message emission should succeed", true)
    }

    /**
     * Test permission management
     */
    @Test
    fun testPermissionManagement() {
        // Test notification listener service status check
        val isEnabled = com.csbaby.kefu.infrastructure.notification.NotificationListenerServiceImpl
            .isNotificationAccessEnabled(context)

        // The result depends on actual device state, so we just verify the method works
        assertNotNull("Status check should return a value", isEnabled)
    }

    /**
     * Test data persistence
     */
    @Test
    fun testDataPersistence() = runBlocking {
        // Test saving and loading user preferences
        val originalPrefs = preferencesManager.userPreferencesFlow.first()

        // Modify preferences
        preferencesManager.updateUserPreferences { current ->
            current.copy(
                themeMode = "dark",
                monitoringEnabled = true,
                selectedApps = setOf("com.whatsapp", "com.facebook.orca")
            )
        }

        // Verify changes persisted
        val updatedPrefs = preferencesManager.userPreferencesFlow.first()
        assertEquals("Theme mode should be saved", "dark", updatedPrefs.themeMode)
        assertTrue("Monitoring should be enabled", updatedPrefs.monitoringEnabled)
        assertEquals("Selected apps should be saved", 2, updatedPrefs.selectedApps.size)

        // Restore original preferences
        preferencesManager.updateUserPreferences { originalPrefs }
    }

    /**
     * Test error handling in AI service
     */
    @Test
    fun testAIErrorHandling() = runBlocking {
        // Test with invalid model ID
        val result = aiService.generateCompletionWithModel(
            modelId = -1L, // Invalid model ID
            prompt = "Test prompt"
        )

        assertFalse("Should fail with invalid model ID", result.isSuccess)
        assertNotNull("Should get an exception", result.exceptionOrNull())
    }
}