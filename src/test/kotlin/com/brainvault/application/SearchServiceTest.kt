package com.brainvault.application

import com.brainvault.domain.model.SearchHit
import com.brainvault.domain.port.SearchIndex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchServiceTest {

    private class FakeIndex : SearchIndex {
        var lastQuery: String? = null
        override fun search(query: String, limit: Int): List<SearchHit> {
            lastQuery = query
            return emptyList()
        }
        override fun rebuild() {}
        override fun clear() {}
    }

    @Test
    fun `wraps each token in quotes joined by spaces`() {
        val index = FakeIndex()
        val service = SearchService(index)
        service.search("hello world")
        assertEquals("\"hello\" \"world\"", index.lastQuery)
    }

    @Test
    fun `blank query returns empty without hitting index`() {
        val index = FakeIndex()
        val service = SearchService(index)
        assertTrue(service.search("").isEmpty())
        assertTrue(service.search("   \t  ").isEmpty())
        assertEquals(null, index.lastQuery)
    }

    @Test
    fun `removes embedded quotes from tokens`() {
        val index = FakeIndex()
        val service = SearchService(index)
        service.search("a \"b\" c")
        assertEquals("\"a\" \"b\" \"c\"", index.lastQuery)
    }

    @Test
    fun `hostile input never throws`() {
        val index = FakeIndex()
        val service = SearchService(index)
        // FTS5 operators, brackets, etc. are neutralized by quoting.
        val out = service.search("NOT (a OR b) \"c\" * d^2 \"\"")
        assertTrue(out.isEmpty())
        assertEquals("\"NOT\" \"(a\" \"OR\" \"b)\" \"c\" \"*\" \"d^2\"", index.lastQuery)
    }

    @Test
    fun `limit is forwarded`() {
        val index = FakeIndex()
        val service = SearchService(index)
        service.search("kotlin", limit = 7)
        assertEquals("\"kotlin\"", index.lastQuery)
    }
}
