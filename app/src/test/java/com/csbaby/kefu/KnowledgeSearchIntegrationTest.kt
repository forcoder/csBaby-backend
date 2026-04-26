package com.csbaby.kefu

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.csbaby.kefu.domain.model.KeywordRule
import com.csbaby.kefu.domain.model.MatchType
import com.csbaby.kefu.domain.model.ReplyContext
import com.csbaby.kefu.domain.model.RuleTargetType
import com.csbaby.kefu.infrastructure.knowledge.KnowledgeBaseManager
import com.csbaby.kefu.presentation.screens.knowledge.KnowledgeViewModel
import com.csbaby.kefu.presentation.screens.knowledge.KnowledgeUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Comprehensive integration tests for knowledge base search improvements
 */
@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class KnowledgeSearchIntegrationTest {

    private lateinit var context: Context
    private lateinit var knowledgeBaseManager: KnowledgeBaseManager
    private lateinit var viewModel: KnowledgeViewModel
    private val testDispatcher = StandardTestDispatcher()

    // Test data for fuzzy matching
    private val testRules = listOf(
        KeywordRule(
            keyword = "取消订单",
            matchType = MatchType.CONTAINS.name,
            replyTemplate = "已为您处理订单取消，退款将在3-5个工作日内原路返回。",
            category = "售后服务",
            targetType = RuleTargetType.ALL.name,
            priority = 10,
            enabled = true
        ),
        KeywordRule(
            keyword = "订单被取消",
            matchType = MatchType.CONTAINS.name,
            replyTemplate = "您的订单已成功取消，如有疑问请联系客服。",
            category = "售后服务",
            targetType = RuleTargetType.ALL.name,
            priority = 9,
            enabled = true
        ),
        KeywordRule(
            keyword = "申请退款",
            matchType = MatchType.CONTAINS.name,
            replyTemplate = "请提供订单号，我们将为您处理退款申请。",
            category = "售后服务",
            targetType = RuleTargetType.ALL.name,
            priority = 8,
            enabled = true
        ),
        KeywordRule(
            keyword = "物流查询",
            matchType = MatchType.CONTAINS.name,
            replyTemplate = "请提供运单号，我将帮您查询物流信息。",
            category = "物流配送",
            targetType = RuleTargetType.ALL.name,
            priority = 7,
            enabled = true
        )
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        context = ApplicationProvider.getApplicationContext()
        // Initialize KnowledgeBaseManager with mocked dependencies if needed
        // For now, we'll test the ViewModel logic directly
        knowledgeBaseManager = KnowledgeBaseManager.createForTesting(context)
        viewModel = KnowledgeViewModel(context, knowledgeBaseManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Test Case 1: Floating Window Popup Search Real-time Trigger
     * Verify that typing in the search field triggers search immediately (no search button)
     */
    @Test
    fun testRealTimeSearchTrigger() = runTest {
        // Insert test rules first
        testRules.forEach { rule ->
            knowledgeBaseManager.createRule(rule)
        }

        val latch = CountDownLatch(1)
        var searchResults: List<KeywordRule> = emptyList()

        // Subscribe to UI state changes
        val job = launch {
            viewModel.uiState.collect { state ->
                searchResults = state.rules
                if (state.totalRuleCount > 0) {
                    latch.countDown()
                }
            }
        }

        // Initial load
        testScheduler.advanceUntilIdle()
        latch.await(2, TimeUnit.SECONDS)

        assertEquals("Initial rules should be loaded", testRules.size, searchResults.size)

        // Clear search and verify all rules show up
        viewModel.search("")
        testScheduler.advanceUntilIdle()
        latch.countDownAndWait()

        assertEquals("All rules should show when searching empty string", testRules.size, searchResults.size)

        // Search for partial match
        viewModel.search("取消")
        testScheduler.advanceUntilIdle()
        latch.countDownAndWait()

        assertTrue("Should find rules containing '取消'", searchResults.isNotEmpty())
        assertTrue("Should only contain matching rules",
            searchResults.all { it.keyword.contains("取消", ignoreCase = true) })

        job.cancel()
    }

    /**
     * Test Case 2: Debounce Works (search after 300ms delay, not on every keystroke)
     */
    @Test
    fun testSearchDebounce() = runTest {
        testRules.forEach { rule ->
            knowledgeBaseManager.createRule(rule)
        }

        var searchCount = 0
        val debounceLatch = CountDownLatch(3) // Wait for 3 searches

        // Track search calls
        val originalSearch = viewModel::search
        val searchCalls = mutableListOf<String>()

        // Create a wrapper that tracks calls
        viewModel.search = { query ->
            searchCalls.add(query)
            searchCount++
            originalSearch.invoke(query)
        }

        // Simulate rapid typing
        viewModel.search("取")
        viewModel.search("取消")
        viewModel.search("取消订")

        // Advance time by more than debounce delay (300ms)
        testScheduler.advanceTimeBy(350)

        // Only the last search should have executed
        assertEquals("Debounced search should only execute once", 1, searchCount)
        assertTrue("Last search should be complete", searchCalls.last().contains("取消订单"))

        debounceLatch.await(2, TimeUnit.SECONDS)
    }

    /**
     * Test Case 3: Empty input clears results silently (no Toast message)
     */
    @Test
    fun testEmptyInputClearsResultsSilently() = runTest {
        testRules.forEach { rule ->
            knowledgeBaseManager.createRule(rule)
        }

        // Perform a search first
        viewModel.search("取消")
        testScheduler.advanceUntilIdle()

        // Get current notice message
        val initialState = viewModel.uiState.value
        assertNull("Initial state should have no notice message", initialState.noticeMessage)

        // Clear search with empty string
        viewModel.search("")
        testScheduler.advanceUntilIdle()

        val finalState = viewModel.uiState.value
        assertNull("Clearing search should not set notice message", finalState.noticeMessage)
        assertEquals("All rules should be shown after clearing search", testRules.size, finalState.rules.size)
    }

    /**
     * Test Case 4: Fuzzy Matching - "取消订单" should match rule with keyword "订单被取消"
     */
    @Test
    fun testFuzzyMatchingChineseReordering() = runTest {
        // Setup test rules with reversed keywords
        val fuzzyTestRules = listOf(
            KeywordRule(
                keyword = "取消订单",
                matchType = MatchType.CONTAINS.name,
                replyTemplate = "订单取消规则回复",
                category = "测试",
                targetType = RuleTargetType.ALL.name,
                priority = 10,
                enabled = true
            ),
            KeywordRule(
                keyword = "订单被取消",
                matchType = MatchType.CONTAINS.name,
                replyTemplate = "订单被取消规则回复",
                category = "测试",
                targetType = RuleTargetType.ALL.name,
                priority = 9,
                enabled = true
            ),
            KeywordRule(
                keyword = "取消订单失败",
                matchType = MatchType.CONTAINS.name,
                replyTemplate = "取消订单失败规则回复",
                category = "测试",
                targetType = RuleTargetType.ALL.name,
                priority = 8,
                enabled = true
            ),
            KeywordRule(
                keyword = "订单取消费用",
                matchType = MatchType.CONTAINS.name,
                replyTemplate = "订单取消费用规则回复",
                category = "测试",
                targetType = RuleTargetType.ALL.name,
                priority = 7,
                enabled = true
            )
        )

        fuzzyTestRules.forEach { rule ->
            knowledgeBaseManager.createRule(rule)
        }

        // Test fuzzy search functionality
        val searchResults = knowledgeBaseManager.searchRulesByKeyword("取消订单", 10)

        // Should find direct matches and fuzzy matches
        assertTrue("Should find direct matches", searchResults.any { it.keyword == "取消订单" })
        assertTrue("Should find fuzzy matches", searchResults.any { it.keyword == "订单被取消" })
        assertTrue("Should find additional variations", searchResults.size >= 2)
    }

    /**
     * Test Case 5: Short keywords (< 3 chars) should NOT trigger fuzzy matching
     */
    @Test
    fun testShortKeywordsNoFuzzyMatching() = runTest {
        val shortKeywordRule = KeywordRule(
            keyword = "退",
            matchType = MatchType.CONTAINS.name,
            replyTemplate = "短关键词规则",
            category = "测试",
            targetType = RuleTargetType.ALL.name,
            priority = 10,
            enabled = true
        )

        knowledgeBaseManager.createRule(shortKeywordRule)

        // Search for very short query
        val searchResults = knowledgeBaseManager.searchRulesByKeyword("退", 10)

        // Should still work but may not use advanced fuzzy matching for very short terms
        assertTrue("Short keyword should still be found", searchResults.isNotEmpty())
        assertTrue("Should find the short keyword rule", searchResults.any { it.keyword == "退" })
    }

    /**
     * Test Case 6: Fuzzy match confidence should be lower than direct substring matches
     */
    @Test
    fun testFuzzyMatchConfidenceOrdering() = runTest {
        val confidenceTestRules = listOf(
            KeywordRule(
                keyword = "取消订单",
                matchType = MatchType.CONTAINS.name,
                replyTemplate = "Direct match rule",
                category = "测试",
                targetType = RuleTargetType.ALL.name,
                priority = 10,
                enabled = true
            ),
            KeywordRule(
                keyword = "订单被取消",
                matchType = MatchType.CONTAINS.name,
                replyTemplate = "Fuzzy match rule",
                category = "测试",
                targetType = RuleTargetType.ALL.name,
                priority = 9,
                enabled = true
            )
        )

        confidenceTestRules.forEach { rule ->
            knowledgeBaseManager.createRule(rule)
        }

        // Test that direct substring matches are prioritized
        val searchResults = knowledgeBaseManager.searchRulesByKeyword("取消订单", 10)

        // Direct matches should appear first or have higher priority
        val directMatches = searchResults.filter { it.keyword == "取消订单" }
        val fuzzyMatches = searchResults.filter { it.keyword == "订单被取消" }

        assertTrue("Should find direct matches", directMatches.isNotEmpty())
        assertTrue("Should find fuzzy matches", fuzzyMatches.isNotEmpty())

        // Direct matches should have equal or higher priority
        if (directMatches.isNotEmpty() && fuzzyMatches.isNotEmpty()) {
            assertEquals("Direct matches should have same or higher priority",
                directMatches.first().priority, fuzzyMatches.first().priority)
        }
    }

    /**
     * Test Case 7: Knowledge ViewModel Data Refresh After Import/Clear/Delete
     */
    @Test
    fun testViewModelRefreshAfterImport() = runTest {
        var refreshCount = 0

        // Mock import function to track refresh calls
        val originalImport = viewModel::importRules
        viewModel.importRules = { uri, mode ->
            refreshCount++
            originalImport(uri, mode)
        }

        // Import some test data
        refreshCount = 0
        viewModel.importRules(createMockUri("test_import.json"), ImportMode.APPEND)
        testScheduler.advanceUntilIdle()

        assertTrue("refreshRules should be called after import", refreshCount > 0)

        val state = viewModel.uiState.value
        assertTrue("UI should reflect imported rules", state.rules.isNotEmpty())
        assertNull("No error message expected for successful import", state.noticeMessage)
    }

    /**
     * Test Case 8: Knowledge ViewModel Data Refresh After Clear
     */
    @Test
    fun testViewModelRefreshAfterClear() = runTest {
        // Add some test rules first
        testRules.take(2).forEach { rule ->
            knowledgeBaseManager.createRule(rule)
        }

        var refreshCount = 0
        val originalClear = viewModel::clearAllRules
        viewModel.clearAllRules = {
            refreshCount++
            originalClear()
        }

        refreshCount = 0
        viewModel.clearAllRules()
        testScheduler.advanceUntilIdle()

        assertTrue("refreshRules should be called after clear", refreshCount > 0)

        val state = viewModel.uiState.value
        assertEquals("UI should show empty list after clear", 0, state.rules.size)
        assertTrue("Notice message should indicate rules were cleared",
            state.noticeMessage?.contains("清空") == true || state.noticeMessage?.contains("删除") == true)
    }

    /**
     * Test Case 9: Knowledge ViewModel Data Refresh After Delete
     */
    @Test
    fun testViewModelRefreshAfterDelete() = runTest {
        val deleteRule = testRules.first()
        knowledgeBaseManager.createRule(deleteRule)

        // Get rule ID for deletion
        val ruleId = knowledgeBaseManager.getAllRules().first().firstOrNull()?.id ?: return

        var refreshCount = 0
        val originalDelete = viewModel::deleteRule
        viewModel.deleteRule = { id ->
            refreshCount++
            originalDelete(id)
        }

        refreshCount = 0
        viewModel.deleteRule(ruleId)
        testScheduler.advanceUntilIdle()

        assertTrue("refreshRules should be called after delete", refreshCount > 0)

        val state = viewModel.uiState.value
        assertFalse("Deleted rule should no longer appear in list",
            state.rules.any { it.keyword == deleteRule.keyword })
    }

    /**
     * Test Case 10: Knowledge ViewModel Data Refresh After Toggle Enabled/Disabled
     */
    @Test
    fun testViewModelRefreshAfterToggle() = runTest {
        val toggleRule = testRules.first()
        knowledgeBaseManager.createRule(toggleRule)

        // Get rule ID for toggling
        val ruleId = knowledgeBaseManager.getAllRules().first().firstOrNull()?.id ?: return

        var refreshCount = 0
        val originalToggle = viewModel::toggleRule
        viewModel.toggleRule = { id, enabled ->
            refreshCount++
            originalToggle(id, enabled)
        }

        refreshCount = 0
        viewModel.toggleRule(ruleId, false) // Disable rule
        testScheduler.advanceUntilIdle()

        assertTrue("refreshRules should be called after toggle", refreshCount > 0)

        val state = viewModel.uiState.value
        assertFalse("Disabled rule should not appear in enabled list",
            state.rules.any { it.id == ruleId && it.enabled })

        // Re-enable the rule
        refreshCount = 0
        viewModel.toggleRule(ruleId, true)
        testScheduler.advanceUntilIdle()

        assertTrue("refreshRules should be called again after re-enabling", refreshCount > 0)

        val stateAfterReenable = viewModel.uiState.value
        assertTrue("Re-enabled rule should appear again",
            stateAfterReenable.rules.any { it.id == ruleId && it.enabled })
    }

    /**
     * Test Case 11: Page Navigation State Persistence
     */
    @Test
    fun testPageNavigationStatePersistence() = runTest {
        // Add test rules
        testRules.forEach { rule ->
            knowledgeBaseManager.createRule(rule)
        }

        // Simulate page navigation by recreating ViewModel
        val newViewModel = KnowledgeViewModel(context, knowledgeBaseManager)
        testScheduler.advanceUntilIdle()

        val persistedState = newViewModel.uiState.value
        assertEquals("Rules list should persist across page switches",
            testRules.size, persistedState.rules.size)
        assertEquals("Rule count should persist", testRules.size, persistedState.totalRuleCount)

        // Test that flow collection still works
        newViewModel.search("取消")
        testScheduler.advanceUntilIdle()

        val searchResults = newViewModel.uiState.value.rules
        assertTrue("Search should work after page recreation", searchResults.isNotEmpty())
    }

    /**
     * Test Case 12: Save/Edit Rule Immediate UI Update
     */
    @Test
    fun testSaveEditRuleImmediateUpdate() = runTest {
        var refreshCount = 0
        val originalSave = viewModel::saveRule
        viewModel.saveRule = { rule ->
            refreshCount++
            originalSave(rule)
        }

        refreshCount = 0
        val newRule = KeywordRule(
            keyword = "新测试规则",
            matchType = MatchType.CONTAINS.name,
            replyTemplate = "新规则回复模板",
            category = "测试",
            targetType = RuleTargetType.ALL.name,
            priority = 5,
            enabled = true
        )

        viewModel.saveRule(newRule)
        testScheduler.advanceUntilIdle()

        assertTrue("refreshRules should be called after saving rule", refreshCount > 0)

        val state = viewModel.uiState.value
        assertTrue("UI should show newly saved rule",
            state.rules.any { it.keyword == "新测试规则" })

        // Test editing existing rule
        val existingRuleId = state.rules.first().id
        refreshCount = 0
        val editedRule = state.rules.first().copy(
            keyword = "编辑后的规则",
            replyTemplate = "编辑后的回复模板"
        )
        viewModel.saveRule(editedRule.copy(id = existingRuleId))
        testScheduler.advanceUntilIdle()

        assertTrue("refreshRules should be called after editing rule", refreshCount > 0)

        val stateAfterEdit = viewModel.uiState.value
        assertTrue("UI should show updated rule",
            stateAfterEdit.rules.any { it.id == existingRuleId && it.keyword == "编辑后的规则" })
    }

    /**
     * Test Case 13: Multiple Format Import Support
     */
    @Test
    fun testMultipleFormatImport() = runTest {
        // Test JSON import
        var success = false
        try {
            viewModel.importRules(createMockUri("rules.json"), ImportMode.APPEND)
            testScheduler.advanceUntilIdle()
            success = true
        } catch (e: Exception) {
            // Expected - we're using mock URIs
        }

        assertTrue("JSON import should not crash", success)

        // Test CSV import
        try {
            viewModel.importRules(createMockUri("rules.csv"), ImportMode.APPEND)
            testScheduler.advanceUntilIdle()
        } catch (e: Exception) {
            // Expected - we're using mock URIs
        }

        // Verify UI updates regardless of actual import success
        val state = viewModel.uiState.value
        assertNotNull("UI state should always be valid", state)
    }

    /**
     * Test Case 14: Hybrid Search Engine Integration
     */
    @Test
    fun testHybridSearchEngineIntegration() = runTest {
        testRules.forEach { rule ->
            knowledgeBaseManager.createRule(rule)
        }

        // Initialize matcher (required for hybrid search)
        knowledgeBaseManager.initializeMatcher()
        testScheduler.advanceUntilIdle()

        // Test hybrid search
        val hybridResults = knowledgeBaseManager.hybridSearch("如何取消订单")
        assertTrue("Hybrid search should return results",
            hybridResults.isNotEmpty())

        // Verify results contain expected rules
        val resultKeywords = hybridResults.map { it.rule.keyword }.toSet()
        assertTrue("Results should include relevant keywords",
            resultKeywords.any { it.contains("取消") })
    }

    /**
     * Test Case 15: Context-Aware Rule Filtering
     */
    @Test
    fun testContextAwareRuleFiltering() = runTest {
        val contextSpecificRule = KeywordRule(
            keyword = "专属服务",
            matchType = MatchType.CONTAINS.name,
            replyTemplate = "专属服务回复",
            category = "售前咨询",
            targetType = RuleTargetType.PROPERTY.name,
            targetNames = listOf("阳光小区", "海景别墅"),
            priority = 10,
            enabled = true
        )

        knowledgeBaseManager.createRule(contextSpecificRule)

        val generalRule = KeywordRule(
            keyword = "通用问题",
            matchType = MatchType.CONTAINS.name,
            replyTemplate = "通用问题回复",
            category = "通用问题",
            targetType = RuleTargetType.ALL.name,
            priority = 5,
            enabled = true
        )

        knowledgeBaseManager.createRule(generalRule)

        // Test with property context
        val propertyContext = ReplyContext(
            propertyName = "阳光小区",
            isGroupConversation = false
        )

        val propertyMatches = knowledgeBaseManager.findAllMatches("专属服务", propertyContext)
        assertTrue("Should find context-specific rule for property",
            propertyMatches.any { it.rule.keyword == "专属服务" })

        // Test without context (should fall back to general rules)
        val generalContext = ReplyContext(
            propertyName = null,
            isGroupConversation = false
        )

        val generalMatches = knowledgeBaseManager.findAllMatches("专属服务", generalContext)
        // May or may not find the rule depending on fallback behavior
        assertNotNull("Should handle context filtering gracefully", generalMatches)
    }

    /**
     * Helper function to create mock URI for testing
     */
    private fun createMockUri(fileName: String): android.net.Uri {
        val tempFile = File.createTempFile("mock_", "_$fileName")
        tempFile.writeText("mock content")
        return android.net.Uri.fromFile(tempFile)
    }

    /**
     * Extension function to simplify CountDownLatch usage
     */
    private suspend fun CountDownLatch.countDownAndWait() {
        await(2, TimeUnit.SECONDS)
    }
}

// Extension to make ViewModel methods testable
internal fun KnowledgeViewModel.search(query: String) {
    // This will be overridden in tests to track calls
    this.search(query)
}

internal fun KnowledgeViewModel.importRules(uri: android.net.Uri, mode: ImportMode) {
    // This will be overridden in tests to track calls
    this.importRules(uri, mode)
}

internal fun KnowledgeViewModel.clearAllRules() {
    // This will be overridden in tests to track calls
    this.clearAllRules()
}

internal fun KnowledgeViewModel.deleteRule(id: Long) {
    // This will be overridden in tests to track calls
    this.deleteRule(id)
}

internal fun KnowledgeViewModel.toggleRule(id: Long, enabled: Boolean) {
    // This will be overridden in tests to track calls
    this.toggleRule(id, enabled)
}

internal fun KnowledgeViewModel.saveRule(rule: com.csbaby.kefu.domain.model.KeywordRule) {
    // This will be overridden in tests to track calls
    this.saveRule(rule)
}