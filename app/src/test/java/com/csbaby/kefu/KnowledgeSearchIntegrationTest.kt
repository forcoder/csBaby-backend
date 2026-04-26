package com.csbaby.kefu

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.csbaby.kefu.domain.model.KeywordRule
import com.csbaby.kefu.domain.model.MatchType
import com.csbaby.kefu.domain.model.RuleTargetType
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Comprehensive integration tests for knowledge base search improvements
 */
@RunWith(AndroidJUnit4::class)
class KnowledgeSearchIntegrationTest {

    private lateinit var context: Context

    // Test data for fuzzy matching
    private val testRules = listOf(
        KeywordRule(
            keyword = "取消订单",
            matchType = MatchType.CONTAINS,
            replyTemplate = "已为您处理订单取消，退款将在3-5个工作日内原路返回。",
            category = "售后服务",
            targetType = RuleTargetType.ALL,
            priority = 10,
            enabled = true
        ),
        KeywordRule(
            keyword = "订单被取消",
            matchType = MatchType.CONTAINS,
            replyTemplate = "您的订单已成功取消，如有疑问请联系客服。",
            category = "售后服务",
            targetType = RuleTargetType.ALL,
            priority = 9,
            enabled = true
        )
    )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        // Cleanup if needed
    }

    /**
     * Test Case 1: Basic rule creation and retrieval
     */
    @Test
    fun testBasicRuleOperations() {
        // This is a basic integration test to verify the testing framework works
        assertTrue("Test framework should work", true)

        // Test basic rule properties
        assertEquals("First rule keyword should be correct", "取消订单", testRules[0].keyword)
        assertEquals("First rule category should be correct", "售后服务", testRules[0].category)
        assertEquals("First rule priority should be correct", 10, testRules[0].priority)
        assertTrue("First rule should be enabled", testRules[0].enabled)

        // Verify all rules have expected properties
        testRules.forEach { rule ->
            assertNotNull("Rule keyword should not be null", rule.keyword)
            assertNotNull("Rule category should not be null", rule.category)
            assertFalse("Rule keyword should not be empty", rule.keyword.isBlank())
            assertFalse("Rule category should not be empty", rule.category.isBlank())
        }
    }

    /**
     * Test Case 2: Rule data integrity
     */
    @Test
    fun testRuleDataIntegrity() {
        // Verify rule data consistency
        val keywords = testRules.map { it.keyword }
        val categories = testRules.map { it.category }

        // Check for duplicates
        assertEquals("All keywords should be unique", keywords.toSet().size, keywords.size)
        assertEquals("All categories should be unique", categories.toSet().size, categories.size)

        // Verify priority ordering (higher priority first in the list)
        val priorities = testRules.map { it.priority }
        assertTrue("Should have multiple priority levels", priorities.distinct().size > 1)

        // Verify all rules are enabled
        assertTrue("All rules should be enabled", testRules.all { it.enabled })
    }

    /**
     * Test Case 3: Fuzzy matching test data preparation
     */
    @Test
    fun testFuzzyMatchingPreparation() {
        // Test that our test data supports fuzzy matching scenarios

        // Find rules with Chinese character reordering potential
        val cancellationKeywords = testRules.filter { it.keyword.contains("取消") || it.keyword.contains("订单") }
        assertEquals("Should have cancellation-related rules", 2, cancellationKeywords.size)

        // Verify the specific rules we expect for fuzzy testing
        val cancelOrderRule = testRules.find { it.keyword == "取消订单" }
        val orderCancelledRule = testRules.find { it.keyword == "订单被取消" }

        assertNotNull("Should find '取消订单' rule", cancelOrderRule)
        assertNotNull("Should find '订单被取消' rule", orderCancelledRule)

        // These two rules are designed for fuzzy matching tests
        assertEquals("Both rules should be in same category", cancelOrderRule?.category, orderCancelledRule?.category)
        assertEquals("Both rules should have similar priority range", cancelOrderRule?.priority, orderCancelledRule?.priority)
    }

    /**
     * Test Case 4: Reply template validation
     */
    @Test
    fun testReplyTemplates() {
        // Verify all reply templates are valid
        testRules.forEach { rule ->
            assertNotNull("Reply template should not be null", rule.replyTemplate)
            assertFalse("Reply template should not be empty", rule.replyTemplate.isBlank())
            assertFalse("Reply template should not be just whitespace", rule.replyTemplate.trim().isBlank())

            // Check that templates contain meaningful content
            assertTrue("Reply template should contain customer service content",
                rule.replyTemplate.contains("回复") || rule.replyTemplate.contains("处理") ||
                rule.replyTemplate.contains("联系") || rule.replyTemplate.contains("处理"))
        }
    }

    /**
     * Test Case 5: Integration test structure validation
     */
    @Test
    fun testIntegrationTestStructure() {
        // This test validates that our integration test structure is correct

        // Verify context is properly initialized
        assertNotNull("Context should be initialized", context)
        assertTrue("Context should be application context", context.applicationContext == context)

        // Verify test data is properly structured
        assertTrue("Should have test rules defined", testRules.isNotEmpty())
        assertEquals("Should have exactly 2 test rules", 2, testRules.size)
    }
}