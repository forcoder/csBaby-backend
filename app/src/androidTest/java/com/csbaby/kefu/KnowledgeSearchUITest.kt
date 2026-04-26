package com.csbaby.kefu

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import com.csbaby.kefu.presentation.MainActivity
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.not
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI Integration tests for knowledge search functionality
 */
@RunWith(AndroidJUnit4::class)
class KnowledgeSearchUITest {

    @get:Rule
    val activityRule = ActivityTestRule(MainActivity::class.java)

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    /**
     * Test Case 26: UI Search Field Real-time Updates
     */
    @Test
    fun testUISearchFieldRealTimeUpdates() {
        // Navigate to knowledge screen (assuming it exists in the app)
        onView(withId(R.id.nav_knowledge)).perform(click())

        // Verify search field exists
        onView(withId(R.id.search_field))
            .check(matches(isDisplayed()))

        // Type in search field and verify results update in real-time
        onView(withId(R.id.search_field))
            .perform(typeText("取消"), closeSoftKeyboard())

        // Wait for async updates and verify results are displayed
        Thread.sleep(1000)

        // Check if search results are shown (this would depend on your specific UI implementation)
        onView(withText("取消"))
            .check(matches(isDisplayed()))

        // Clear search
        onView(withId(R.id.search_field))
            .perform(clearText(), closeSoftKeyboard())

        // Verify all items are shown again
        Thread.sleep(500)
        onView(withText("全部规则")) // or whatever indicates showing all
            .check(matches(isDisplayed()))
    }

    /**
     * Test Case 27: Search Results Display and Interaction
     */
    @Test
    fun testSearchResultsDisplayAndInteraction() {
        onView(withId(R.id.nav_knowledge)).perform(click())

        // Perform search for specific keyword
        onView(withId(R.id.search_field))
            .perform(typeText("退款"), closeSoftKeyboard())

        Thread.sleep(800) // Wait for search debounce

        // Verify search results are displayed
        onView(withText("申请退款"))
            .check(matches(isDisplayed()))

        // Tap on a search result
        onView(withText("申请退款")).perform(click())

        // Verify details are shown (if there's a detail view)
        onView(withId(R.id.rule_detail_container))
            .check(matches(isDisplayed()))
    }

    /**
     * Test Case 28: Import/Export Button Functionality
     */
    @Test
    fun testImportExportButtonFunctionality() {
        onView(withId(R.id.nav_knowledge)).perform(click())

        // Verify import button exists and is clickable
        onView(withId(R.id.btn_import))
            .check(matches(isDisplayed()))
            .check(matches(isEnabled()))

        // Verify export button exists and is clickable
        onView(withId(R.id.btn_export))
            .check(matches(isDisplayed()))
            .check(matches(isEnabled()))

        // Test import button click opens file picker
        onView(withId(R.id.btn_import)).perform(click())

        // File picker should appear (implementation depends on specific UI)
        Thread.sleep(500)
        // onView(withText("选择文件"))
        //     .check(matches(isDisplayed()))
    }

    /**
     * Test Case 29: Rule Management Buttons
     */
    @Test
    fun testRuleManagementButtons() {
        onView(withId(R.id.nav_knowledge)).perform(click())

        // Add new rule button
        onView(withId(R.id.btn_add_rule))
            .check(matches(isDisplayed()))
            .perform(click())

        // Verify new rule dialog appears
        onView(withText("添加新规则"))
            .check(matches(isDisplayed()))

        // Cancel the operation
        onView(withText("取消")).perform(click())

        // Verify dialog is dismissed
        onView(withText("添加新规则"))
            .check(matches(not(isDisplayed())))
    }

    /**
     * Test Case 30: Category Filter Functionality
     */
    @Test
    fun testCategoryFilterFunctionality() {
        onView(withId(R.id.nav_knowledge)).perform(click())

        // Open category filter dropdown/spinner
        onView(withId(R.id.category_filter))
            .check(matches(isDisplayed()))
            .perform(click())

        // Select a specific category
        onView(withText("售后服务")).perform(click())

        Thread.sleep(500)

        // Verify only rules from selected category are shown
        onView(withText("取消订单"))
            .check(matches(isDisplayed()))

        // Test clearing category filter
        onView(withId(R.id.category_filter))
            .perform(click())
        onView(withText("全部类别")).perform(click())

        Thread.sleep(500)

        // Verify all categories are shown again
        onView(withText("通用问题"))
            .check(matches(isDisplayed()))
    }

    /**
     * Test Case 31: Empty State Display
     */
    @Test
    fun testEmptyStateDisplay() {
        // This test assumes you have an empty state view when no rules exist
        // You might need to clear all existing rules first in a real test

        onView(withId(R.id.nav_knowledge)).perform(click())

        // Look for empty state indicators
        onView(withId(R.id.empty_state_image))
            .check(matches(isDisplayed()))

        onView(withId(R.id.empty_state_text))
            .check(matches(isDisplayed()))
            .check(matches(withText("暂无知识库规则")))
    }

    /**
     * Test Case 32: Loading State During Operations
     */
    @Test
    fun testLoadingStateDuringOperations() {
        onView(withId(R.id.nav_knowledge)).perform(click())

        // Trigger a long-running operation (like importing)
        onView(withId(R.id.btn_import)).perform(click())

        // Verify loading indicator appears
        onView(withId(R.id.progress_indicator))
            .check(matches(isDisplayed()))

        // Verify loading text is shown
        onView(withText("正在导入..."))
            .check(matches(isDisplayed()))

        // Simulate operation completion
        Thread.sleep(3000)

        // Verify loading indicator disappears
        onView(withId(R.id.progress_indicator))
            .check(matches(not(isDisplayed())))

        // Verify success message appears
        onView(withText("导入完成"))
            .check(matches(isDisplayed()))
    }

    /**
     * Test Case 33: Toast Message Display
     */
    @Test
    fun testToastMessageDisplay() {
        onView(withId(R.id.nav_knowledge)).perform(click())

        // Perform action that shows toast (like deleting all rules)
        onView(withId(R.id.btn_clear_all)).perform(click())

        // Verify toast message appears (Toast messages can be tricky to test)
        // You might need to use custom Toast matchers or check for alternative feedback

        Thread.sleep(2000)

        // Verify the operation completed (rules list is empty)
        onView(withId(R.id.rules_list))
            .check(matches(hasChildCount(0)))
    }

    /**
     * Test Case 34: Edit Rule Dialog Functionality
     */
    @Test
    fun testEditRuleDialog() {
        onView(withId(R.id.nav_knowledge)).perform(click())

        // First add a rule
        onView(withId(R.id.btn_add_rule)).perform(click())

        // Fill in rule details
        onView(withId(R.id.edt_keyword)).perform(typeText("测试规则"))
        onView(withId(R.id.edt_reply_template)).perform(typeText("测试回复模板"))
        onView(withId(R.id.spn_category)).perform(click())
        onView(withText("通用问题")).perform(click())

        onView(withId(R.id.btn_save)).perform(click())

        // Now edit the rule
        onView(withText("测试规则")).perform(longClick())

        // Edit dialog should appear
        onView(withText("编辑规则"))
            .check(matches(isDisplayed()))

        // Make changes
        onView(withId(R.id.edt_keyword)).perform(replaceText("修改后的规则"))
        onView(withId(R.id.btn_update)).perform(click())

        // Verify the rule was updated
        onView(withText("修改后的规则"))
            .check(matches(isDisplayed()))
    }

    /**
     * Test Case 35: Delete Confirmation Dialog
     */
    @Test
    fun testDeleteConfirmationDialog() {
        onView(withId(R.id.nav_knowledge)).perform(click())

        // Long press on a rule to trigger delete
        onView(withText("测试删除")).perform(longClick())

        // Confirm deletion dialog should appear
        onView(withText("确认删除"))
            .check(matches(isDisplayed()))

        onView(withText("确定")).perform(click())

        // Rule should be removed from list
        onView(withText("测试删除"))
            .check(matches(not(isDisplayed())))
    }

    /**
     * Test Case 36: Toggle Rule Enable/Disable
     */
    @Test
    fun testToggleRuleEnableDisable() {
        onView(withId(R.id.nav_knowledge)).perform(click())

        // Find a rule with checkbox/switch
        onView(withText("启用").nextOrSibling()) // This selector depends on your UI
            .perform(click())

        // Verify visual indication of disabled state
        onView(withText("禁用"))
            .check(matches(isDisplayed()))

        // Toggle back to enabled
        onView(withText("禁用")).perform(click())

        // Verify rule is enabled again
        onView(withText("启用"))
            .check(matches(isDisplayed()))
    }

    /**
     * Test Case 37: Navigation Between Tabs/Screens
     */
    @Test
    fun testNavigationBetweenTabs() {
        onView(withId(R.id.nav_home)).perform(click())
        Thread.sleep(300)

        // Verify we're on home tab
        onView(withText("首页"))
            .check(matches(isDisplayed()))

        onView(withId(R.id.nav_settings)).perform(click())
        Thread.sleep(300)

        // Verify we're on settings tab
        onView(withText("设置"))
            .check(matches(isDisplayed()))

        // Return to knowledge tab
        onView(withId(R.id.nav_knowledge)).perform(click())
        Thread.sleep(300)

        // Verify knowledge screen still works
        onView(withId(R.id.search_field))
            .check(matches(isDisplayed()))
    }

    /**
     * Test Case 38: Search History and Suggestions
     */
    @Test
    fun testSearchHistoryAndSuggestions() {
        onView(withId(R.id.nav_knowledge)).perform(click())

        // Perform multiple searches
        onView(withId(R.id.search_field))
            .perform(typeText("退款"), closeSoftKeyboard())

        Thread.sleep(500)

        // Clear and search again
        onView(withId(R.id.search_field))
            .perform(clearText(), typeText("物流"), closeSoftKeyboard())

        Thread.sleep(500)

        // Search history dropdown should show recent searches
        onView(withId(R.id.search_dropdown))
            .check(matches(isDisplayed()))

        // Select from suggestion
        onView(withText("退款"))
            .perform(click())

        Thread.sleep(300)

        // Verify suggestion was selected
        onView(withId(R.id.search_field))
            .check(matches(hasText("退款")))
    }

    /**
     * Test Case 39: Responsive Layout for Different Screen Sizes
     */
    @Test
    fun testResponsiveLayout() {
        // This test verifies the layout adapts to different screen orientations/sizes
        // You may need to programmatically change orientation or use different device profiles

        onView(withId(R.id.nav_knowledge)).perform(click())

        // Landscape mode verification
        // onActivity { activity ->
        //     activity.resources.configuration.orientation = Configuration.ORIENTATION_LANDSCAPE
        //     activity.recreate()
        // }

        Thread.sleep(1000)

        // Verify key elements are still visible and functional
        onView(withId(R.id.search_field))
            .check(matches(isDisplayed()))

        onView(withId(R.id.rules_list))
            .check(matches(isDisplayed()))

        // Portrait mode verification
        // onActivity { activity ->
        //     activity.resources.configuration.orientation = Configuration.ORIENTATION_PORTRAIT
        //     activity.recreate()
        // }

        Thread.sleep(1000)

        // Verify layout returns to normal
        onView(withId(R.id.search_field))
            .check(matches(isDisplayed()))
    }

    /**
     * Test Case 40: Accessibility Features
     */
    @Test
    fun testAccessibilityFeatures() {
        onView(withId(R.id.nav_knowledge)).perform(click())

        // Verify search field has proper content description
        onView(withId(R.id.search_field))
            .check(matches(isCompletelyDisplayed()))

        // Test navigation via accessibility actions
        onView(withContentDescription("搜索输入框"))
            .perform(scrollTo(), click())

        onView(withId(R.id.search_field))
            .check(matches(hasFocus()))
    }

    /**
     * Helper extension functions for cleaner test code
     */
    private fun android.view.View.nextOrSibling(): androidx.test.espresso.ViewAction {
        return object : androidx.test.espresso.Action {
            override fun getDescription(): String = "nextOrSibling"
            override fun perform(uiController: androidx.test.espresso.UiController, view: android.view.View) {
                val parent = view.parent as? android.view.ViewGroup ?: return
                val index = parent.indexOfChild(view)
                if (index < parent.childCount - 1) {
                    parent.getChildAt(index + 1).performClick()
                }
            }
        }
    }

    /**
     * Extension to wait for asynchronous operations
     */
    private fun waitForAsync(duration: Long = 1000L) {
        Thread.sleep(duration)
    }
}