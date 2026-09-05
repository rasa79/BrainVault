package com.brainvault.application

import com.brainvault.domain.model.SearchHit
import com.brainvault.domain.port.SearchIndex

/**
 * Escapes a user query into a safe FTS5 MATCH string and delegates to the index.
 * Every non-blank whitespace-separated token is wrapped in double quotes so the
 * user's arbitrary input can never inject FTS5 operators or throw; blank → empty.
 */
class SearchService(private val index: SearchIndex) {

    fun search(rawQuery: String, limit: Int = 100): List<SearchHit> {
        val tokens = rawQuery.split(Regex("\\s+"))
            .map { it.trim() }
            .map { it.replace("\"", "") }
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        val match = tokens.joinToString(" ") { "\"$it\"" }
        return index.search(match, limit)
    }
}
