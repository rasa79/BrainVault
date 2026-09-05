package com.brainvault.domain.port

// see LEARN[KJV-008] (ports as interfaces)
interface FavoriteRepository {
    fun add(noteId: Long)
    fun remove(noteId: Long)
    fun isFavorite(noteId: Long): Boolean
    fun favoriteNoteIds(): List<Long>    // ordered by added_at
}
