package com.brainvault.domain.port

import com.brainvault.domain.model.SearchHit

// see LEARN[KJV-008] (ports as interfaces)
interface SearchIndex {
    fun search(query: String, limit: Int = 100): List<SearchHit>
    fun rebuild()        // runs FTS 'rebuild' command after bulk note reload
    fun clear()          // delete all rows (used by full reindex before reload)
}
