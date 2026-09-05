package com.brainvault.application

import com.brainvault.infrastructure.persistence.Database
import com.brainvault.infrastructure.persistence.SqliteFavoriteRepository
import com.brainvault.infrastructure.persistence.SqliteNoteRepository
import com.brainvault.testutil.TestVaults
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FavoriteServiceTest {

    private lateinit var db: Database
    private lateinit var service: FavoriteService

    @BeforeEach
    fun setUp() {
        db = Database(TestVaults.tempDbFile())
        db.initSchema()
        val notes = SqliteNoteRepository(db)
        service = FavoriteService(SqliteFavoriteRepository(db), notes)
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }

    @Test
    fun `toggle adds then removes`() {
        val notes = SqliteNoteRepository(db)
        notes.upsert(TestVaults.note(path = "a.md"))
        assertFalse(service.isFavorite("a.md"))
        assertTrue(service.toggle("a.md"))
        assertTrue(service.isFavorite("a.md"))
        assertFalse(service.toggle("a.md"))
        assertFalse(service.isFavorite("a.md"))
    }

    @Test
    fun `list returns favorites in added order`() {
        val notes = SqliteNoteRepository(db)
        notes.upsert(TestVaults.note(path = "a.md"))
        notes.upsert(TestVaults.note(path = "b.md"))
        notes.upsert(TestVaults.note(path = "c.md"))
        service.toggle("a.md")
        service.toggle("c.md")
        service.toggle("b.md")
        assertEquals(listOf("a.md", "c.md", "b.md"), service.list().map { it.path })
    }

    @Test
    fun `toggle on unknown path returns false`() {
        assertFalse(service.toggle("nope.md"))
    }
}
