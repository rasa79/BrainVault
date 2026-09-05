package com.brainvault.domain.port

import com.brainvault.domain.model.Link

// see LEARN[KJV-008] (ports as interfaces)
interface LinkRepository {
    fun replaceLinksFrom(sourceId: Long, links: List<Link>)   // delete-then-insert for one note
    fun backlinksFor(targetId: Long): List<Link>              // resolved inbound links
    fun unresolvedLinks(): List<Link>
}
