package com.brainvault.application

import com.brainvault.domain.model.Note
import com.brainvault.domain.model.Tag
import com.brainvault.domain.port.NoteRepository
import com.brainvault.domain.port.TagRepository

/**
 * Tag listing/filtering over the normalized tag tables.
 */
class TagService(
    private val tags: TagRepository,
    private val notes: NoteRepository,
) {
    /** All tags with counts, sorted by name. */
    fun allTags(): List<Tag> = tags.allTagsWithCounts()

    /** Notes carrying [tagName], in path order. */
    fun notesFor(tagName: String): List<Note> =
        tags.notePathsForTag(tagName).mapNotNull { notes.findByPath(it) }
}
