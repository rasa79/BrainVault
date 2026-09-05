package com.brainvault.infrastructure.persistence

import com.brainvault.domain.model.Link
import com.brainvault.domain.model.LinkKind
import com.brainvault.testutil.TestVaults
import com.brainvault.testutil.TestVaults.note
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SqliteLinkRepositoryTest {

    private lateinit var db: Database
    private lateinit var notes: SqliteNoteRepository
    private lateinit var links: SqliteLinkRepository

    @BeforeEach
    fun setUp() {
        db = Database(TestVaults.tempDbFile())
        db.initSchema()
        notes = SqliteNoteRepository(db)
        links = SqliteLinkRepository(db)
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }

    @Test
    fun `replaceLinksFrom and backlinksFor`() {
        val idA = notes.upsert(note(path = "a.md", title = "A"))
        val idB = notes.upsert(note(path = "b.md", title = "B"))

        links.replaceLinksFrom(idA, listOf(Link("a.md", "b.md", "B", LinkKind.WIKI)))

        val backlinks = links.backlinksFor(idB)
        assertEquals(1, backlinks.size)
        assertEquals("a.md", backlinks[0].sourcePath)
        assertEquals("b.md", backlinks[0].targetPath)
        assertEquals("B", backlinks[0].rawTarget)
        assertEquals(LinkKind.WIKI, backlinks[0].kind)
    }

    @Test
    fun `unresolved links are stored with null target`() {
        val idA = notes.upsert(note(path = "a.md", title = "A"))
        links.replaceLinksFrom(idA, listOf(Link("a.md", null, "Missing", LinkKind.MARKDOWN)))

        val unresolved = links.unresolvedLinks()
        assertEquals(1, unresolved.size)
        assertEquals("a.md", unresolved[0].sourcePath)
        assertTrue(unresolved[0].targetPath == null)
        assertEquals("Missing", unresolved[0].rawTarget)
        assertEquals(LinkKind.MARKDOWN, unresolved[0].kind)
    }

    @Test
    fun `unresolved link resolves after target appears and reindex`() {
        val idA = notes.upsert(note(path = "a.md", title = "A"))
        links.replaceLinksFrom(idA, listOf(Link("a.md", null, "b", LinkKind.WIKI)))
        // target appears; reindex resolves path -> id
        val idB = notes.upsert(note(path = "b.md", title = "B"))
        links.replaceLinksFrom(idA, listOf(Link("a.md", "b.md", "b", LinkKind.WIKI)))

        assertEquals(1, links.backlinksFor(idB).size)
        assertTrue(links.unresolvedLinks().isEmpty())
    }

    @Test
    fun `replaceLinksFrom replaces prior edges for source`() {
        val idA = notes.upsert(note(path = "a.md", title = "A"))
        val idB = notes.upsert(note(path = "b.md", title = "B"))
        val idC = notes.upsert(note(path = "c.md", title = "C"))

        links.replaceLinksFrom(idA, listOf(Link("a.md", "b.md", "B", LinkKind.WIKI)))
        links.replaceLinksFrom(idA, listOf(Link("a.md", "c.md", "C", LinkKind.WIKI)))

        assertTrue(links.backlinksFor(idB).isEmpty())
        assertEquals(1, links.backlinksFor(idC).size)
    }
}
