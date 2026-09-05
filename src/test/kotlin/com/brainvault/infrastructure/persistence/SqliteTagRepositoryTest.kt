package com.brainvault.infrastructure.persistence

import com.brainvault.testutil.TestVaults
import com.brainvault.testutil.TestVaults.note
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SqliteTagRepositoryTest {

    private lateinit var db: Database
    private lateinit var notes: SqliteNoteRepository
    private lateinit var tags: SqliteTagRepository

    @BeforeEach
    fun setUp() {
        db = Database(TestVaults.tempDbFile())
        db.initSchema()
        notes = SqliteNoteRepository(db)
        tags = SqliteTagRepository(db)
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }

    @Test
    fun `replaceTagsForNote and counts`() {
        val idA = notes.upsert(note(path = "a.md"))
        val idB = notes.upsert(note(path = "b.md"))
        tags.replaceTagsForNote(idA, listOf("kotlin", "java"))
        tags.replaceTagsForNote(idB, listOf("kotlin"))

        val all = tags.allTagsWithCounts()
        assertEquals(2, all.size)
        val kotlin = all.first { it.name == "kotlin" }
        val java = all.first { it.name == "java" }
        assertEquals(2, kotlin.noteCount)
        assertEquals(1, java.noteCount)
    }

    @Test
    fun `notePathsForTag returns paths`() {
        val idA = notes.upsert(note(path = "a.md"))
        val idB = notes.upsert(note(path = "sub/b.md"))
        tags.replaceTagsForNote(idA, listOf("x"))
        tags.replaceTagsForNote(idB, listOf("x", "y"))
        assertEquals(listOf("a.md", "sub/b.md"), tags.notePathsForTag("x"))
        assertEquals(listOf("sub/b.md"), tags.notePathsForTag("y"))
    }

    @Test
    fun `replace of a note drops its old tag edges`() {
        val idA = notes.upsert(note(path = "a.md"))
        tags.replaceTagsForNote(idA, listOf("old", "keep"))
        tags.replaceTagsForNote(idA, listOf("keep"))
        val names = tags.allTagsWithCounts().map { it.name }
        assertTrue("keep" in names)
        assertEquals(false, "old" in names, "orphan tag 'old' should be removed: $names")
    }
}
