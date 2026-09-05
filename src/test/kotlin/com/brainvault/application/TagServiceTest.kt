package com.brainvault.application

import com.brainvault.infrastructure.persistence.Database
import com.brainvault.infrastructure.persistence.SqliteNoteRepository
import com.brainvault.infrastructure.persistence.SqliteTagRepository
import com.brainvault.testutil.TestVaults
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TagServiceTest {

    private lateinit var db: Database
    private lateinit var notes: SqliteNoteRepository
    private lateinit var service: TagService

    @BeforeEach
    fun setUp() {
        db = Database(TestVaults.tempDbFile())
        db.initSchema()
        notes = SqliteNoteRepository(db)
        service = TagService(SqliteTagRepository(db), notes)
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }

    @Test
    fun `allTags returns counts sorted by name`() {
        val idA = notes.upsert(TestVaults.note(path = "a.md"))
        val idB = notes.upsert(TestVaults.note(path = "b.md"))
        val tags = SqliteTagRepository(db)
        tags.replaceTagsForNote(idA, listOf("beta", "alpha"))
        tags.replaceTagsForNote(idB, listOf("alpha"))

        val all = service.allTags()
        assertEquals(listOf("alpha", "beta"), all.map { it.name })
        assertEquals(2, all[0].noteCount)
        assertEquals(1, all[1].noteCount)
    }

    @Test
    fun `notesFor filters by tag in path order`() {
        val idA = notes.upsert(TestVaults.note(path = "b.md"))
        val idB = notes.upsert(TestVaults.note(path = "a.md"))
        val idC = notes.upsert(TestVaults.note(path = "c.md"))
        val tags = SqliteTagRepository(db)
        tags.replaceTagsForNote(idA, listOf("x"))
        tags.replaceTagsForNote(idB, listOf("x", "y"))
        tags.replaceTagsForNote(idC, listOf("y"))

        assertEquals(listOf("a.md", "b.md"), service.notesFor("x").map { it.path })
        assertEquals(listOf("a.md", "c.md"), service.notesFor("y").map { it.path })
    }
}
