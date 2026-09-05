package com.brainvault.infrastructure.persistence

import com.brainvault.testutil.TestVaults
import com.brainvault.testutil.TestVaults.note
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class Fts5SearchIndexTest {

    private lateinit var db: Database
    private lateinit var notes: SqliteNoteRepository
    private lateinit var fts: Fts5SearchIndex

    @BeforeEach
    fun setUp() {
        db = Database(TestVaults.tempDbFile())
        db.initSchema()
        notes = SqliteNoteRepository(db)
        fts = Fts5SearchIndex(db)
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }

    @Test
    fun `search matches body across notes`() {
        notes.upsert(note(path = "a.md", title = "Kotlin", body = "java is a language"))
        notes.upsert(note(path = "b.md", title = "Java", body = "kotlin is also jvm"))
        val hits = fts.search("java")
        assertEquals(2, hits.size)
        assertEquals(listOf("a.md", "b.md"), hits.map { it.path }.sorted())
    }

    @Test
    fun `snippet contains highlight markers`() {
        notes.upsert(note(path = "a.md", title = "Kotlin", body = "a generous snippet of kotlin"))
        val hits = fts.search("snippet")
        assertTrue(hits.isNotEmpty())
        assertTrue(hits[0].snippet.contains("<b>"), "got: ${hits[0].snippet}")
    }

    @Test
    fun `delete removes row from fts index`() {
        notes.upsert(note(path = "a.md", title = "Kotlin", body = "uniquezzle"))
        assertTrue(fts.search("uniquezzle").isNotEmpty())
        notes.deleteByPath("a.md")
        assertTrue(fts.search("uniquezzle").isEmpty())
    }

    @Test
    fun `update is reflected in index`() {
        notes.upsert(note(path = "a.md", title = "Kotlin", body = "oldword"))
        assertTrue(fts.search("oldword").isNotEmpty())
        notes.upsert(note(path = "a.md", title = "Kotlin", body = "newword"))
        assertTrue(fts.search("newword").isNotEmpty())
        assertTrue(fts.search("oldword").isEmpty())
    }

    @Test
    fun `clear deletes all notes`() {
        notes.upsert(note(path = "a.md", title = "A", body = "somebody"))
        fts.clear()
        assertEquals(0, notes.count())
        assertTrue(fts.search("somebody").isEmpty())
    }

    @Test
    fun `rebuild repopulates index`() {
        notes.upsert(note(path = "a.md", title = "Kotlin", body = "rebuilt word"))
        fts.rebuild()
        assertTrue(fts.search("rebuilt").isNotEmpty())
    }

    @Test
    fun `bm25 ranks the more relevant note first`() {
        // a.md matches in the title column AND has many body occurrences, so it is
        // strictly more relevant than b.md (single body occurrence, no title match).
        notes.upsert(note(path = "a.md", title = "Kotlin", body = "kotlin kotlin kotlin"))
        notes.upsert(note(path = "b.md", title = "Random", body = "kotlin"))

        val hits = fts.search("kotlin")
        assertEquals(2, hits.size)
        assertEquals("a.md", hits[0].path, "more relevant note must rank first: ${hits.map { it.path }}")
        assertTrue(hits[0].rank < hits[1].rank, "bm25 score should be lower=better")
    }
}
