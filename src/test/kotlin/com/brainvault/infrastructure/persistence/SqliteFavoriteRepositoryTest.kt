package com.brainvault.infrastructure.persistence

import com.brainvault.testutil.TestVaults
import com.brainvault.testutil.TestVaults.note
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SqliteFavoriteRepositoryTest {

    private lateinit var db: Database
    private lateinit var notes: SqliteNoteRepository
    private lateinit var favorites: SqliteFavoriteRepository

    @BeforeEach
    fun setUp() {
        db = Database(TestVaults.tempDbFile())
        db.initSchema()
        notes = SqliteNoteRepository(db)
        favorites = SqliteFavoriteRepository(db)
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }

    @Test
    fun `add and isFavorite`() {
        val id = notes.upsert(note(path = "a.md"))
        assertFalse(favorites.isFavorite(id))
        favorites.add(id)
        assertTrue(favorites.isFavorite(id))
        favorites.remove(id)
        assertFalse(favorites.isFavorite(id))
    }

    @Test
    fun `add is idempotent`() {
        val id = notes.upsert(note(path = "a.md"))
        favorites.add(id)
        favorites.add(id) // INSERT OR IGNORE
        assertEquals(listOf(id), favorites.favoriteNoteIds())
    }

    @Test
    fun `favoriteNoteIds returns added ids`() {
        val id1 = notes.upsert(note(path = "a.md"))
        val id2 = notes.upsert(note(path = "b.md"))
        val id3 = notes.upsert(note(path = "c.md"))
        favorites.add(id1)
        favorites.add(id3)
        favorites.add(id2)

        val ids = favorites.favoriteNoteIds()
        assertEquals(3, ids.size)
        assertEquals(true, id1 in ids)
        assertEquals(true, id2 in ids)
        assertEquals(true, id3 in ids)
    }
}
