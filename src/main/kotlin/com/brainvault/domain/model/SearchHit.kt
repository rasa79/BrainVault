package com.brainvault.domain.model

// see LEARN[KJV-001] (data class)
data class SearchHit(
    val path: String,
    val title: String,
    val snippet: String,   // contains <b>...</b> highlight markers from FTS5 snippet()
    val rank: Double,      // bm25 score, lower = better
)
