package com.brainvault.infrastructure.persistence

import com.brainvault.testutil.TestVaults
import com.brainvault.testutil.TestVaults.note
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SqliteNoteRepositoryTest {

    private lateinit var db: Database
    private lateinit var repo: SqliteNoteRepository

    @BeforeEach
    fun setUp() {
        db = Database(TestVaults.tempDbFile())
        db.initSchema()
        repo = SqliteNoteRepository(db)
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }

    @Test
    fun `upsert inserts and findByPath round-trips`() {
        val id = repo.upsert(note(path = "a.md", title = "A", body = "hello"))
        assertTrue(id > 0)
        val found = repo.findByPath("a.md")!!
        assertEquals("a.md", found.path)
        assertEquals("A", found.title)
        assertEquals("hello", found.body)
    }

    @Test
    fun `upsert updates existing row by path`() {
        repo.upsert(note(path = "a.md", title = "A", body = "v1"))
        val id2 = repo.upsert(note(path = "a.md", title = "A2", body = "v2"))
        assertTrue(id2 > 0)
        assertEquals(1, repo.count())
        assertEquals("A2", repo.findByPath("a.md")!!.title)
        assertEquals("v2", repo.findByPath("a.md")!!.body)
    }

    @Test
    fun `deleteByPath removes row or no-ops`() {
        repo.upsert(note(path = "a.md"))
        repo.deleteByPath("a.md")
        assertNull(repo.findByPath("a.md"))
        assertEquals(0, repo.count())
        repo.deleteByPath("missing.md") // no-op, no throw
    }

    @Test
    fun `findByTitle is case-insensitive on title`() {
        repo.upsert(note(path = "b.md", title = "My Title"))
        assertNotNull(repo.findByTitle("my title"))
        assertNotNull(repo.findByTitle("MY TITLE"))
    }

    @Test
    fun `findByTitle matches filename without extension`() {
        repo.upsert(note(path = "projects/idea.md", title = "Something else"))
        assertNotNull(repo.findByTitle("idea"))
    }

    @Test
    fun `allPaths returns stored paths`() {
        repo.upsert(note(path = "b.md"))
        repo.upsert(note(path = "a.md"))
        assertEquals(listOf("a.md", "b.md"), repo.allPaths())
    }
}
