package com.csbaby.kefu.fakes.knowledge

import com.csbaby.kefu.domain.model.KeywordRule
import com.csbaby.kefu.infrastructure.search.HybridSearchEngine
import com.csbaby.kefu.infrastructure.search.SearchType

class FakeHybridSearchEngine {

    var searchCallCount = 0
    var initializeCallCount = 0

    var lastQuery: String? = null
    var lastContext: com.csbaby.kefu.domain.model.ReplyContext? = null
    var lastMode: HybridSearchEngine.SearchMode? = null

    var shouldThrowException = false
    var exceptionToThrow = RuntimeException("Search failed")

    fun reset() {
        searchCallCount = 0
        initializeCallCount = 0
        lastQuery = null
        lastContext = null
        lastMode = null
        shouldThrowException = false
    }

    suspend fun initialize(): Boolean {
        initializeCallCount++
        return true
    }

    suspend fun search(
        query: String,
        context: com.csbaby.kefu.domain.model.ReplyContext?,
        mode: HybridSearchEngine.SearchMode,
        keywordWeight: Float = 0.6f,
        semanticWeight: Float = 0.4f,
        maxResults: Int = 10
    ): List<HybridSearchEngine.HybridSearchResult> {
        searchCallCount++
        lastQuery = query
        lastContext = context
        lastMode = mode

        if (shouldThrowException) {
            throw exceptionToThrow
        }

        // Return mock results based on query
        val mockRule = com.csbaby.kefu.factory.TestDataFactory.keywordRule(
            keyword = query,
            replyTemplate = "Mock reply for $query"
        )

        return listOf(
            HybridSearchEngine.HybridSearchResult(
                rule = mockRule,
                keywordScore = 0.8f,
                semanticScore = 0.6f,
                combinedScore = 0.7f,
                matchedText = query,
                matchType = SearchType.HYBRID
            )
        )
    }
}
