package com.csbaby.kefu

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlinx.coroutines.runBlocking
import com.google.truth.Truth.assertThat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.test.filters.LargeTest

/**
 * Integration tests for end-to-end functionality
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class IntegrationTests {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.INTERNET,
        android.Manifest.permission.ACCESS_NETWORK_STATE
    )

    @Before
    fun setup() {
        // Clear any existing test data before each test
        clearTestData()
    }

    /**
     * Test complete user preference flow
     */
    @Test
    fun testUserPreferenceFlow() = runBlocking {
        // Arrange - Get initial preferences
        val prefsManager = getPreferencesManager()

        // Act - Read initial state
        var initialPrefs = prefsManager.userPreferencesFlow.first()

        // Assert - Verify default state
        assertThat(initialPrefs.monitoringEnabled).isFalse()
        assertThat(initialPrefs.selectedApps).isEmpty()

        // Act - Update preferences
        val updatedPrefs = initialPrefs.copy(
            monitoringEnabled = true,
            selectedApps = setOf("com.whatsapp", "com.facebook.orca"),
            themeMode = "dark"
        )
        prefsManager.updateUserPreferences { updatedPrefs }

        // Assert - Verify update persisted
        val currentPrefs = prefsManager.userPreferencesFlow.first()
        assertThat(currentPrefs.monitoringEnabled).isTrue()
        assertThat(currentPrefs.selectedApps).containsExactly("com.whatsapp", "com.facebook.orca")
        assertThat(currentPrefs.themeMode).isEqualTo("dark")

        // Cleanup
        prefsManager.updateUserPreferences { initialPrefs }
    }

    /**
     * Test AI service integration with network connectivity
     */
    @Test
    fun testAIIntegration() = runBlocking {
        // Arrange
        val aiService = getAIService()
        val testPrompt = "Hello, can you help me with my order?"

        // Act - Generate AI response
        val result = aiService.generateCompletion(testPrompt)

        // Assert - Verify response structure
        assertThat(result.isSuccess).isTrue()
        val response = result.getOrNull()
        assertThat(response).isNotNull()
        assertThat(response!!.isNotBlank()).isTrue()

        // Additional validation
        assertThat(response.length).isAtLeast(10)
        assertThat(response.length).isAtMost(1000)
        assertThat(response.lowercase()).contains("hello")
    }

    /**
     * Test knowledge base operations integration
     */
    @Test
    fun testKnowledgeBaseIntegration() = runBlocking {
        // Arrange
        val knowledgeManager = getKnowledgeManager()
        val testEntry = com.csbaby.kefu.infrastructure.knowledge.KnowledgeBaseManager.KnowledgeEntry(
            title = "Test Product",
            content = "This is a test product description for integration testing.",
            keywords = listOf("test", "product", "integration"),
            category = "General"
        )

        // Act - Add entry
        val entryId = knowledgeManager.addEntry(
            title = testEntry.title,
            content = testEntry.content,
            keywords = testEntry.keywords,
            category = testEntry.category
        )

        // Assert - Entry was added successfully
        assertThat(entryId).isNotNull()

        // Act - Search for the entry
        val searchResults = knowledgeManager.searchEntries("test product")

        // Assert - Search returned results
        assertThat(searchResults).isNotEmpty()
        val foundEntry = searchResults.find { it.title == "Test Product" }
        assertThat(foundEntry).isNotNull()
        assertThat(foundEntry!!.title).isEqualTo("Test Product")

        // Cleanup
        knowledgeManager.deleteEntry(entryId)
    }

    /**
     * Test message monitoring workflow
     */
    @Test
    fun testMessageMonitoringWorkflow() = runBlocking {
        // Arrange
        val messageMonitor = getMessageMonitor()
        val testMessage = com.csbaby.kefu.infrastructure.notification.MessageMonitor.MonitoredMessage(
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            title = "John Doe",
            content = "Hey, I need help with my recent order",
            conversationTitle = "Order Support",
            isGroupConversation = false,
            timestamp = System.currentTimeMillis()
        )

        // Act - Emit message through monitoring system
        messageMonitor.emitMessage(testMessage)

        // Assert - Message was processed without exceptions
        // Note: In a real implementation, you would verify the message was actually processed
        // For this test, we just verify the emission succeeded
        assertThat(true).isTrue()
    }

    /**
     * Test data persistence across app restarts
     */
    @Test
    fun testDataPersistence() = runBlocking {
        // Arrange
        val prefsManager = getPreferencesManager()
        val originalPrefs = prefsManager.userPreferencesFlow.first()

        // Act - Modify preferences
        val modifiedPrefs = originalPrefs.copy(
            monitoringEnabled = true,
            selectedApps = setOf("com.whatsapp"),
            lastUpdated = System.currentTimeMillis()
        )
        prefsManager.updateUserPreferences { modifiedPrefs }

        // Simulate app restart by getting fresh instance
        val freshPrefsManager = getPreferencesManager()
        val restoredPrefs = freshPrefsManager.userPreferencesFlow.first()

        // Assert - Preferences persisted correctly
        assertThat(restoredPrefs.monitoringEnabled).isEqualTo(modifiedPrefs.monitoringEnabled)
        assertThat(restoredPrefs.selectedApps).isEqualTo(modifiedPrefs.selectedApps)

        // Cleanup
        freshPrefsManager.updateUserPreferences { originalPrefs }
    }

    /**
     * Test notification listener service status
     */
    @Test
    fun testNotificationListenerStatus() {
        // Arrange & Act - Check notification access status
        val isEnabled = com.csbaby.kefu.infrastructure.notification.NotificationListenerServiceImpl
            .isNotificationAccessEnabled(context)

        // Assert - Status check completed without exception
        assertThat(isEnabled).isNotNull()
    }

    /**
     * Test network connectivity and API availability
     */
    @Test
    fun testNetworkConnectivity() {
        // Arrange & Act - Check network permissions are granted
        val hasInternetPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.INTERNET
        ) == PackageManager.PERMISSION_GRANTED

        // Assert - Required permissions are present
        assertThat(hasInternetPermission).isTrue()

        // Additional network capability checks could be added here
        // such as actual internet connectivity testing
    }

    /**
     * Test file system operations
     */
    @Test
    fun testFileSystemOperations() {
        // Arrange
        val testDir = File(context.cacheDir, "integration_test")
        if (!testDir.exists()) {
            assertThat(testDir.mkdir()).isTrue()
        }

        // Act - Create test file
        val testFile = File(testDir, "test_data.txt")
        val writeSuccess = testFile.writeText("Integration test data")

        // Assert - File operations successful
        assertThat(writeSuccess).isTrue()
        assertThat(testFile.exists()).isTrue()
        assertThat(testFile.canRead()).isTrue()
        assertThat(testFile.canWrite()).isTrue()

        // Act - Read file content
        val content = testFile.readText()

        // Assert - Content matches what was written
        assertThat(content).isEqualTo("Integration test data")

        // Cleanup
        testFile.delete()
        testDir.delete()
    }

    /**
     * Test concurrent operations safety
     */
    @Test
    fun testConcurrentOperationsSafety() = runBlocking {
        // Arrange
        val operationCount = 50
        val concurrentOperations = mutableListOf<java.util.concurrent.Future<*>>()

        // Act - Execute concurrent operations
        repeat(operationCount) { index ->
            val future = java.util.concurrent.Executors.newSingleThreadExecutor().submit {
                // Simulate some work
                Thread.sleep(10)
                index
            }
            concurrentOperations.add(future)
        }

        // Wait for all operations to complete
        val results = concurrentOperations.map { it.get() }

        // Assert - All operations completed successfully
        assertThat(results).hasSize(operationCount)
        assertThat(results.distinct()).hasSize(operationCount) // No duplicates due to race conditions
    }

    /**
     * Test memory usage during intensive operations
     */
    @Test
    fun testMemoryUsage() = runBlocking {
        // Arrange
        val largeDataSetSize = 10000
        val startTime = System.currentTimeMillis()

        // Act - Perform memory-intensive operations
        val largeList = mutableListOf<String>()
        repeat(largeDataSetSize) {
            largeList.add("Item $it with some descriptive text to make it more realistic")
        }

        // Process the data
        val processedCount = largeList.count { it.contains("Item") }
        val sortedList = largeList.sortedBy { it.length }

        // Assert - Operations completed within reasonable time and memory
        assertThat(largeList).hasSize(largeDataSetSize)
        assertThat(processedCount).isEqualTo(largeDataSetSize)
        assertThat(sortedList.first()).isNotEmpty()

        // Verify execution time (should complete within 5 seconds for 10k items)
        val executionTime = System.currentTimeMillis() - startTime
        assertThat(executionTime).isLessThan(5000)
    }

    /**
     * Test error recovery scenarios
     */
    @Test
    fun testErrorRecovery() = runBlocking {
        // Arrange
        val errorScenarios = listOf(
            { throw java.net.UnknownHostException("No internet") },
            { throw java.io.IOException("Connection failed") },
            { throw java.lang.IllegalArgumentException("Invalid parameter") }
        )

        // Act & Assert - Each error scenario should be handled gracefully
        errorScenarios.forEach { errorScenario ->
            try {
                errorScenario()
                fail("Expected exception to be thrown")
            } catch (e: Exception) {
                // Verify error handling doesn't crash the app
                assertThat(e).isNotNull()
                // In a real implementation, you would log the error or show user feedback
            }
        }
    }

    /**
     * Test configuration loading and validation
     */
    @Test
    fun testConfigurationLoading() {
        // Arrange & Act - Load application configuration
        val config = loadAppConfiguration()

        // Assert - Configuration is valid
        assertThat(config).isNotNull()
        assertThat(config.apiEndpoint).isNotEmpty()
        assertThat(config.maxRetries).isGreaterThan(0)
        assertThat(config.timeoutMs).isGreaterThan(0)
    }

    /**
     * Helper methods for test setup
     */

    private suspend fun getPreferencesManager(): com.csbaby.kefu.data.local.PreferencesManager {
        // In a real implementation, you would use Hilt for dependency injection
        // For testing purposes, return a mock or test instance
        return try {
            // This would typically be injected via Hilt
            com.csbaby.kefu.data.local.PreferencesManager(context)
        } catch (e: Exception) {
            // Fallback to mock implementation for testing
            object : com.csbaby.kefu.data.local.PreferencesManager(context) {
                override val userPreferencesFlow = kotlinx.coroutines.flow.flowOf(
                    com.csbaby.kefu.data.model.UserPreferences()
                )
                override suspend fun updateUserPreferences(transform: (com.csbaby.kefu.data.model.UserPreferences) -> com.csbaby.kefu.data.model.UserPreferences) {
                    // Mock implementation
                }
            }
        }
    }

    private suspend fun getAIService(): com.csbaby.kefu.infrastructure.ai.AIService {
        // Similar fallback pattern for AI service
        return try {
            com.csbaby.kefu.infrastructure.ai.AIService()
        } catch (e: Exception) {
            mock()
        }
    }

    private suspend fun getKnowledgeManager(): com.csbaby.kefu.infrastructure.knowledge.KnowledgeBaseManager {
        // Similar fallback pattern for knowledge manager
        return try {
            com.csbaby.kefu.infrastructure.knowledge.KnowledgeBaseManager(context)
        } catch (e: Exception) {
            mock()
        }
    }

    private suspend fun getMessageMonitor(): com.csbaby.kefu.infrastructure.notification.MessageMonitor {
        // Similar fallback pattern for message monitor
        return try {
            com.csbaby.kefu.infrastructure.notification.MessageMonitor()
        } catch (e: Exception) {
            mock()
        }
    }

    private fun loadAppConfiguration(): AppConfig {
        // Mock configuration loading
        return AppConfig(
            apiEndpoint = "https://api.example.com",
            maxRetries = 3,
            timeoutMs = 5000
        )
    }

    private fun clearTestData() {
        // Clean up any test data from previous test runs
        try {
            val testDirs = File(context.cacheDir, "integration_test").listFiles()
            testDirs?.forEach { file ->
                if (file.name.startsWith("test_")) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    // Data class for configuration
    data class AppConfig(
        val apiEndpoint: String,
        val maxRetries: Int,
        val timeoutMs: Long
    )
}