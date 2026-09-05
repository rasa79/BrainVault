package com.brainvault.domain.port

import com.brainvault.domain.model.Tag

// see LEARN[KJV-008] (ports as interfaces)
interface TagRepository {
    fun replaceTagsForNote(noteId: Long, tags: List<String>)
    fun allTagsWithCounts(): List<Tag>                 // sorted by name
    fun notePathsForTag(tagName: String): List<String>
}
