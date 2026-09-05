package com.brainvault.application

import com.brainvault.domain.model.Note
import com.brainvault.domain.port.FavoriteRepository
import com.brainvault.domain.port.NoteRepository

/**
 * Favorites toggling and listing. Favorites are identified by note rowid; the
 * repository's `favoriteNoteIds()` returns ids ordered by `added_at`, which this
 * service resolves back to full notes in that order.
 */
class FavoriteService(
    private val favorites: FavoriteRepository,
    private val notes: NoteRepository,
) {
    /** Toggles the favorite state for a note path and returns the new state. */
    fun toggle(notePath: String): Boolean {
        val id = notes.findByPath(notePath)?.id ?: return false
        return if (favorites.isFavorite(id)) {
            favorites.remove(id)
            false
        } else {
            favorites.add(id)
            true
        }
    }

    fun isFavorite(notePath: String): Boolean {
        val id = notes.findByPath(notePath)?.id ?: return false
        return favorites.isFavorite(id)
    }

    /** Resolves all favorited notes, ordered by `added_at`. */
    fun list(): List<Note> {
        val byId = HashMap<Long, Note>()
        for (path in notes.allPaths()) {
            notes.findByPath(path)?.let { byId[it.id] = it }
        }
        return favorites.favoriteNoteIds().mapNotNull { byId[it] }
    }
}
