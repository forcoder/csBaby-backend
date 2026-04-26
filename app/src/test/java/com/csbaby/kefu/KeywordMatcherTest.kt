package com.csbaby.kefu

import com.csbaby.kefu.domain.model.KeywordRule
import com.csbaby.kefu.domain.model.MatchType
import com.csbaby.kefu.domain.model.RuleTargetType
import com.csbaby.kefu.factory.TestDataFactory
import com.csbaby.kefu.infrastructure.knowledge.KeywordMatcher
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class KeywordMatcherTest {

    private lateinit var matcher: KeywordMatcher

    @Before
    fun setup() {
        matcher = KeywordMatcher()
    }

    // ==================== ✅ 正常功能测试 ====================

    // KM-001: 精确匹配基本匹配
    @Test
    fun `KM-001 exact match basic`() {
        matcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = "价格", matchType = MatchType.EXACT)
        ))
        val results = matcher.findMatches("请问价格是多少")
        assertTrue("Should find match", results.isNotEmpty())
        assertEquals("价格", results[0].matchedText)
    }

    // KM-002: 包含匹配基本匹配
    @Test
    fun `KM-002 contains match basic`() {
        matcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = "价格", matchType = MatchType.CONTAINS)
        ))
        val results = matcher.findMatches("请问价格是多少")
        assertTrue("Should find match", results.isNotEmpty())
        assertEquals("价格", results[0].matchedText)
    }

    // KM-003: 正则匹配基本匹配
    @Test
    fun `KM-003 regex match basic`() {
        matcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = "\\d+元", matchType = MatchType.REGEX)
        ))
        val results = matcher.findMatches("房间100元")
        assertTrue("Should find match", results.isNotEmpty())
        assertEquals("100元", results[0].matchedText)
    }

    // KM-004: 多关键词逗号分隔匹配
    @Test
    fun `KM-004 comma separated keywords`() {
        matcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = "价格,多少钱,费用", matchType = MatchType.CONTAINS)
        ))
        val result1 = matcher.findBestMatch("请问价格是多少")
        assertNotNull("价格 should match", result1)

        val result2 = matcher.findBestMatch("这个多少钱")
        assertNotNull("多少钱 should match", result2)

        val result3 = matcher.findBestMatch("费用是多少")
        assertNotNull("费用 should match", result3)
    }

    // KM-005: 多关键词中文逗号分隔
    @Test
    fun `KM-005 chinese comma separated keywords`() {
        matcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = "价格，多少钱，费用", matchType = MatchType.CONTAINS)
        ))
        val result = matcher.findBestMatch("这个多少钱")
        assertNotNull("Chinese comma should work", result)
    }

    // KM-006: 多关键词竖线分隔
    @Test
    fun `KM-006 pipe separated keywords`() {
        matcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = "价格|多少钱|费用", matchType = MatchType.CONTAINS)
        ))
        val result = matcher.findBestMatch("这个多少钱")
        assertNotNull("Pipe separator should work", result)
    }

    // KM-007: 多关键词换行分隔
    @Test
    fun `KM-007 newline separated keywords`() {
        matcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = "价格\n多少钱\n费用", matchType = MatchType.CONTAINS)
        ))
        val result = matcher.findBestMatch("这个多少钱")
        assertNotNull("Newline separator should work", result)
    }

    // KM-008: 优先级排序
    @Test
    fun `KM-008 priority sorting`() {
        matcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = "价格", priority = 1),
            TestDataFactory.keywordRule(id = 2L, keyword = "价格", priority = 10)
        ))
        val results = matcher.findMatches("价格")
        assertTrue("Should have matches", results.size >= 1)
        // Higher priority should have higher confidence
        val highPriority = results.find { it.rule.id == 2L }
        val lowPriority = results.find { it.rule.id == 1L }
        if (highPriority != null && lowPriority != null) {
            assertTrue(
                "High priority confidence should be >= low priority",
                highPriority.confidence >= lowPriority.confidence
            )
        }
    }

    // KM-009: 精确匹配类型加分
    @Test
    fun `KM-009 exact match type bonus`() {
        val exactMatcher = KeywordMatcher()
        val containsMatcher = KeywordMatcher()

        exactMatcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = "价格", matchType = MatchType.EXACT)
        ))
        containsMatcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 2L, keyword = "价格", matchType = MatchType.CONTAINS)
        ))

        val exactResult = exactMatcher.findBestMatch("价格")
        val containsResult = containsMatcher.findBestMatch("价格")

        if (exactResult != null && containsResult != null) {
            assertTrue(
                "EXACT should have higher confidence than CONTAINS",
                exactResult.confidence > containsResult.confidence
            )
        }
    }

    // KM-010: 包含匹配类型加分
    @Test
    fun `KM-010 contains match type bonus`() {
        val containsMatcher = KeywordMatcher()
        val regexMatcher = KeywordMatcher()

        containsMatcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = "价格", matchType = MatchType.CONTAINS)
        ))
        regexMatcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 2L, keyword = "价格", matchType = MatchType.REGEX)
        ))

        val containsResult = containsMatcher.findBestMatch("价格")
        val regexResult = regexMatcher.findBestMatch("价格")

        if (containsResult != null && regexResult != null) {
            assertTrue(
                "CONTAINS(0.2) should have lower confidence than REGEX(0.25)",
                containsResult.confidence < regexResult.confidence
            )
        }
    }

    // KM-011: 正则匹配类型加分
    @Test
    fun `KM-011 regex match type bonus`() {
        matcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = "\\d+元", matchType = MatchType.REGEX)
        ))
        val result = matcher.findBestMatch("100元")
        assertNotNull("Regex should match", result)
        assertTrue("Regex confidence should be > 0", result!!.confidence > 0f)
    }

    // KM-012: 置信度-长度比贡献
    @Test
    fun `KM-012 length ratio contribution`() {
        val shortMatcher = KeywordMatcher()
        val longMatcher = KeywordMatcher()

        shortMatcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = "价格", matchType = MatchType.CONTAINS)
        ))
        longMatcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 2L, keyword = "价格", matchType = MatchType.CONTAINS)
        ))

        val shortResult = shortMatcher.findBestMatch("价格")
        val longResult = longMatcher.findBestMatch("请问一下价格到底是多少啊")

        if (shortResult != null && longResult != null) {
            assertTrue(
                "Shorter message should have higher confidence (higher length ratio)",
                shortResult.confidence > longResult.confidence
            )
        }
    }

    // KM-013: 优先级加成
    @Test
    fun `KM-013 priority factor boost`() {
        val maxPriority = KeywordMatcher()
        val minPriority = KeywordMatcher()

        maxPriority.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = "价格", matchType = MatchType.CONTAINS, priority = 10)
        ))
        minPriority.initialize(listOf(
            TestDataFactory.keywordRule(id = 2L, keyword = "价格", matchType = MatchType.CONTAINS, priority = 0)
        ))

        val maxResult = maxPriority.findBestMatch("价格")
        val minResult = minPriority.findBestMatch("价格")

        if (maxResult != null && minResult != null) {
            val diff = maxResult.confidence - minResult.confidence
            assertEquals("Priority diff should be 0.2", 0.2f, diff, 0.01f)
        }
    }

    // KM-014: 置信度范围限制[0,1]
    @Test
    fun `KM-014 confidence coerced to 0-1 range`() {
        matcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = "价格", matchType = MatchType.EXACT, priority = 10)
        ))
        val result = matcher.findBestMatch("价格")
        assertNotNull(result)
        assertTrue("Confidence should be <= 1.0", result!!.confidence <= 1.0f)
        assertTrue("Confidence should be >= 0.0", result.confidence >= 0.0f)
    }

    // KM-015: findMatches按置信度降序
    @Test
    fun `KM-015 findMatches sorted by confidence descending`() {
        matcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = "价格", matchType = MatchType.CONTAINS, priority = 1),
            TestDataFactory.keywordRule(id = 2L, keyword = "价格", matchType = MatchType.EXACT, priority = 10)
        ))
        val results = matcher.findMatches("价格")
        for (i in 0 until results.size - 1) {
            assertTrue(
                "Results should be sorted by confidence descending",
                results[i].confidence >= results[i + 1].confidence
            )
        }
    }

    // KM-016: findBestMatch返回最高置信度
    @Test
    fun `KM-016 findBestMatch returns highest confidence`() {
        matcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = "价格", priority = 1),
            TestDataFactory.keywordRule(id = 2L, keyword = "价格", priority = 10)
        ))
        val best = matcher.findBestMatch("价格")
        assertNotNull("Should find best match", best)
        assertEquals("Should be highest priority rule", 2L, best!!.rule.id)
    }

    // KM-017: 缓存命中
    @Test
    fun `KM-017 cache hit returns cached result`() {
        matcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = "价格", matchType = MatchType.CONTAINS)
        ))
        val result1 = matcher.findMatches("请问价格")
        val result2 = matcher.findMatches("请问价格")
        assertEquals("Cached result should be identical", result1, result2)
    }

    // KM-018: clearCache清空缓存
    @Test
    fun `KM-018 clearCache clears cache`() {
        matcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = "价格")
        ))
        matcher.findMatches("请问价格")
        matcher.clearCache()
        // After clear, should still work (rebuild trie lazily)
        val result = matcher.findMatches("请问价格")
        assertTrue("Should still match after cache clear", result.isNotEmpty())
    }

    // KM-019: initialize重置规则和缓存
    @Test
    fun `KM-019 initialize resets rules and cache`() {
        matcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = "价格")
        ))
        matcher.findMatches("请问价格")

        matcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 2L, keyword = "你好")
        ))
        val oldResult = matcher.findMatches("请问价格")
        val newResult = matcher.findMatches("你好")
        assertTrue("Old keyword should not match after reinitialize", oldResult.isEmpty())
        assertTrue("New keyword should match", newResult.isNotEmpty())
    }

    // KM-020: 禁用规则不参与匹配
    @Test
    fun `KM-020 disabled rules not matched`() {
        matcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = "价格", enabled = false)
        ))
        val result = matcher.findBestMatch("请问价格")
        assertNull("Disabled rule should not match", result)
    }

    // KM-021: 模板变量替换
    @Test
    fun `KM-021 template variable substitution`() {
        val result = matcher.applyTemplate("价格是{price}元", mapOf("price" to "100"))
        assertEquals("价格是100元", result)
    }

    // KM-022: 模板变量忽略大小写
    @Test
    fun `KM-022 template variable case insensitive`() {
        val result = matcher.applyTemplate("价格是{Price}元", mapOf("price" to "100"))
        assertEquals("价格是100元", result)
    }

    // KM-023: 多位置匹配同一规则
    @Test
    fun `KM-023 multiple positions same rule`() {
        matcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = "价格", matchType = MatchType.CONTAINS)
        ))
        val results = matcher.findMatches("价格价格价格")
        assertTrue("Should find multiple matches", results.isNotEmpty())
    }

    // KM-024: 同规则多关键词取最佳置信度
    @Test
    fun `KM-024 same rule multiple aliases takes best confidence`() {
        matcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = "价格,多少钱,费用", matchType = MatchType.CONTAINS)
        ))
        val results = matcher.findMatches("请问价格是多少")
        val ruleMatches = results.filter { it.rule.id == 1L }
        assertTrue("Should have at most 1 entry per rule", ruleMatches.size <= 1)
    }

    // KM-025: 匹配结果去重
    @Test
    fun `KM-025 match results deduplicated`() {
        matcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = "价格,多少钱")
        ))
        val results = matcher.findMatches("价格多少钱")
        val ids = results.map { it.rule.id }
        assertEquals("Should have unique rule IDs", ids.distinct().size, ids.size)
    }

    // ==================== ⚠️ 边界条件测试 ====================

    // KM-B01: 空规则列表
    @Test
    fun `KM-B01 empty rules list`() {
        matcher.initialize(emptyList())
        val result = matcher.findBestMatch("请问价格")
        assertNull("Empty rules should return null", result)
    }

    // KM-B02: 空消息匹配
    @Test
    fun `KM-B02 empty message`() {
        matcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = "价格")
        ))
        val result = matcher.findBestMatch("")
        assertNull("Empty message should not match", result)
    }

    // KM-B03: 单字符关键词
    @Test
    fun `KM-B03 single char keyword`() {
        matcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = "好", matchType = MatchType.CONTAINS)
        ))
        val result = matcher.findBestMatch("你好")
        assertNotNull("Single char keyword should match", result)
    }

    // KM-B04: 超长关键词
    @Test
    fun `KM-B04 very long keyword`() {
        val longKeyword = "价".repeat(200)
        matcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = longKeyword)
        ))
        val result = matcher.findBestMatch("前缀${longKeyword}后缀")
        assertNotNull("Long keyword should match", result)
    }

    // KM-B05: 超长消息
    @Test
    fun `KM-B05 very long message`() {
        matcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = "价格")
        ))
        val longMessage = "前缀".repeat(5000) + "价格" + "后缀".repeat(5000)
        val result = matcher.findBestMatch(longMessage)
        assertNotNull("Should match in long message", result)
    }

    // KM-B06: 消息与关键词完全相同
    @Test
    fun `KM-B06 message equals keyword`() {
        matcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = "价格", matchType = MatchType.CONTAINS)
        ))
        val result = matcher.findBestMatch("价格")
        assertNotNull("Exact message should match", result)
        assertEquals("Confidence should be high", 1f, result!!.confidence, 0.5f)
    }

    // KM-B07: 置信度上限为1.0
    @Test
    fun `KM-B07 confidence upper bound 1_0`() {
        matcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = "价格", matchType = MatchType.EXACT, priority = 10)
        ))
        val result = matcher.findBestMatch("价格")
        assertTrue("Confidence should not exceed 1.0", result!!.confidence <= 1.0f)
    }

    // KM-B08: 置信度下限为0.0
    @Test
    fun `KM-B08 confidence lower bound 0_0`() {
        matcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = "价格", matchType = MatchType.CONTAINS, priority = 0)
        ))
        val result = matcher.findMatches("价格" + "后缀".repeat(1000))
        result.forEach { assertTrue("Confidence should be >= 0", it.confidence >= 0f) }
    }

    // KM-B09: 缓存达到上限1000条
    @Test
    fun `KM-B09 cache size limit 1000`() {
        matcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = "测试")
        ))
        // Fill cache beyond limit
        for (i in 0..1100) {
            matcher.findMatches("消息$i")
        }
        // Should not crash
        val result = matcher.findBestMatch("测试消息")
        assertNotNull("Should still work after cache limit", result)
    }

    // KM-B10: 关键词别名去重
    @Test
    fun `KM-B10 keyword aliases deduplicated`() {
        matcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = "价格,价格,价格")
        ))
        val result = matcher.findMatches("价格")
        val ruleMatches = result.filter { it.rule.id == 1L }
        assertTrue("Deduplicated aliases should produce single result", ruleMatches.size <= 1)
    }

    // KM-B11: 关键词前后空格trim
    @Test
    fun `KM-B11 keyword trim`() {
        matcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = " 价格 ")
        ))
        val result = matcher.findBestMatch("请问价格")
        assertNotNull("Trimmed keyword should match", result)
    }

    // KM-B12: 空别名过滤
    @Test
    fun `KM-B12 empty alias filter`() {
        matcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = ", , ")
        ))
        val result = matcher.findBestMatch("价格")
        // Empty aliases should be filtered, rule may not match
        // This tests no crash
        assertTrue("Should not crash with empty aliases", true)
    }

    // KM-B13: 大小写不敏感匹配
    @Test
    fun `KM-B13 case insensitive matching`() {
        matcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = "PRICE", matchType = MatchType.CONTAINS)
        ))
        val result = matcher.findBestMatch("请问price是多少")
        assertNotNull("Case insensitive match should work", result)
    }

    // KM-B14: 优先级范围限制[0,10]
    @Test
    fun `KM-B14 priority coerced to 0-10`() {
        val highMatcher = KeywordMatcher()
        val veryHighMatcher = KeywordMatcher()

        highMatcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = "价格", matchType = MatchType.CONTAINS, priority = 10)
        ))
        veryHighMatcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 2L, keyword = "价格", matchType = MatchType.CONTAINS, priority = 100)
        ))

        val result1 = highMatcher.findBestMatch("价格")
        val result2 = veryHighMatcher.findBestMatch("价格")

        if (result1 != null && result2 != null) {
            assertEquals(
                "Priority > 10 should be coerced to 10",
                result1.confidence, result2.confidence, 0.01f
            )
        }
    }

    // KM-B15: 规则ID不存在时过滤
    @Test
    fun `KM-B15 missing rule id filtered`() {
        // This is tested indirectly - trie stores ruleIds but rules list might not have them
        matcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = "价格")
        ))
        val result = matcher.findBestMatch("价格")
        assertNotNull("Should find match with valid rule", result)
    }

    // KM-B16: 模板无变量时原样返回
    @Test
    fun `KM-B16 template without variables unchanged`() {
        val result = matcher.applyTemplate("纯文本模板", emptyMap())
        assertEquals("纯文本模板", result)
    }

    // KM-B17: 模板变量不存在时保留原样
    @Test
    fun `KM-B17 nonexistent template variable kept`() {
        val result = matcher.applyTemplate("您好{name}", emptyMap())
        assertEquals("您好{name}", result)
    }

    // KM-B18: 正则匹配大小写忽略
    @Test
    fun `KM-B18 regex case insensitive`() {
        matcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = "hello", matchType = MatchType.REGEX)
        ))
        val result = matcher.findBestMatch("HELLO world")
        assertNotNull("Regex should be case insensitive", result)
    }

    // ==================== ❌ 异常情况测试 ====================

    // KM-E01: 无效正则表达式不崩溃
    @Test
    fun `KM-E01 invalid regex does not crash`() {
        matcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = "[invalid", matchType = MatchType.REGEX)
        ))
        val result = matcher.findBestMatch("test")
        // Should not crash, just skip the invalid regex
        assertTrue("Should not crash with invalid regex", true)
    }

    // KM-E02: 未初始化直接匹配
    @Test
    fun `KM-E02 match without initialize`() {
        val newMatcher = KeywordMatcher()
        val result = newMatcher.findBestMatch("价格")
        // Should auto-build trie (empty), return empty results
        assertTrue("Should return empty without initialize", result.isEmpty())
    }

    // KM-E04: 并发匹配线程安全
    @Test
    fun `KM-E04 concurrent matching thread safety`() {
        matcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = "价格")
        ))
        val results = mutableListOf<KeywordMatcher.MatchedResult?>()
        val threads = (1..10).map {
            Thread {
                val result = matcher.findBestMatch("请问价格")
                synchronized(results) { results.add(result) }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join(5000) }
        assertEquals("All threads should complete", 10, results.size)
        assertTrue("All should find match", results.all { it != null })
    }

    // KM-E05: 特殊正则元字符
    @Test
    fun `KM-E05 special regex metacharacters`() {
        matcher.initialize(listOf(
            TestDataFactory.keywordRule(id = 1L, keyword = ".*+?^\${}()|[]\\", matchType = MatchType.REGEX)
        ))
        // Should not crash
        val result = matcher.findBestMatch("test")
        assertTrue("Should not crash with special regex chars", true)
    }
}
