package com.brainvault.application

import com.brainvault.domain.port.FileEvent
import com.brainvault.domain.port.FileEventKind
import com.brainvault.infrastructure.fs.VaultFileStore
import com.brainvault.infrastructure.markdown.FrontMatterCodec
import com.brainvault.infrastructure.markdown.MarkdownParser
import com.brainvault.infrastructure.persistence.Database
import com.brainvault.infrastructure.persistence.Fts5SearchIndex
import com.brainvault.infrastructure.persistence.SqliteFavoriteRepository
import com.brainvault.infrastructure.persistence.SqliteLinkRepository
import com.brainvault.infrastructure.persistence.SqliteNoteRepository
import com.brainvault.infrastructure.persistence.SqliteTagRepository
import com.brainvault.testutil.TestVaults
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class IndexingServiceTest {

    private lateinit var db: Database
    private lateinit var notes: SqliteNoteRepository
    private lateinit var tags: SqliteTagRepository
    private lateinit var links: SqliteLinkRepository
    private lateinit var fts: Fts5SearchIndex
    private lateinit var favorites: SqliteFavoriteRepository
    private lateinit var indexing: IndexingService

    @BeforeEach
    fun setUp() {
        db = Database(TestVaults.tempDbFile())
        db.initSchema()
        notes = SqliteNoteRepository(db)
        tags = SqliteTagRepository(db)
        links = SqliteLinkRepository(db)
        fts = Fts5SearchIndex(db)
        favorites = SqliteFavoriteRepository(db)
        indexing = IndexingService(notes, tags, links, fts, favorites, VaultFileStore(), FrontMatterCodec(), MarkdownParser())
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }

    @Test
    fun `full rebuild indexes all markdown files`() {
        val root = TestVaults.tempDir()
        TestVaults.write(root, "a.md", "# A\n\nhello world")
        TestVaults.write(root, "sub/b.md", "# B\n\nsecond file")
        TestVaults.write(root, ".brainvault/secret.md", "# hidden")

        indexing.fullRebuild(root)

        assertEquals(2, notes.count())
        assertTrue(fts.search("hello").isNotEmpty())
        assertTrue(fts.search("second").isNotEmpty())
        assertTrue(fts.search("hidden").isEmpty())
    }

    @Test
    fun `full rebuild preserves favorites for existing paths`() {
        val root = TestVaults.tempDir()
        TestVaults.write(root, "a.md", "# A")
        indexing.fullRebuild(root)
        val idA = notes.findByPath("a.md")!!.id
        favorites.add(idA)

        indexing.fullRebuild(root)

        val after = notes.findByPath("a.md")!!.id
        assertTrue(favorites.isFavorite(after))
    }

    @Test
    fun `incremental create modify delete`() {
        val root = TestVaults.tempDir()
        indexing.fullRebuild(root)

        TestVaults.write(root, "a.md", "# A\n\nuniqueAlpha")
        indexing.onFileEvents(root, listOf(FileEvent(FileEventKind.CREATED, "a.md")))
        assertTrue(fts.search("uniqueAlpha").isNotEmpty())

        TestVaults.write(root, "a.md", "# A\n\nuniqueBeta")
        indexing.onFileEvents(root, listOf(FileEvent(FileEventKind.MODIFIED, "a.md")))
        assertTrue(fts.search("uniqueBeta").isNotEmpty())
        assertTrue(fts.search("uniqueAlpha").isEmpty())

        indexing.onFileEvents(root, listOf(FileEvent(FileEventKind.DELETED, "a.md")))
        assertEquals(0, notes.count())
    }

    @Test
    fun `unresolved wiki link resolves after target appears`() {
        val root = TestVaults.tempDir()
        TestVaults.write(root, "a.md", "See [[Target Note]]")
        indexing.fullRebuild(root)

        val sourceId = notes.findByPath("a.md")!!.id
        assertTrue(links.unresolvedLinks().isNotEmpty())

        TestVaults.write(root, "Target Note.md", "# Target Note")
        indexing.onFileEvents(root, listOf(FileEvent(FileEventKind.CREATED, "Target Note.md")))
        indexing.onFileEvents(root, listOf(FileEvent(FileEventKind.MODIFIED, "a.md")))

        val targetId = notes.findByPath("Target Note.md")!!.id
        assertEquals(1, links.backlinksFor(targetId).size)
        assertTrue(links.unresolvedLinks().isEmpty())
    }

    @Test
    fun `brainvault events are ignored in onFileEvents`() {
        val root = TestVaults.tempDir()
        TestVaults.write(root, "a.md", "# A\n\nbodyzz")
        TestVaults.write(root, ".brainvault/secret.md", "# hidden\n\nsecbody")
        indexing.onFileEvents(root, listOf(FileEvent(FileEventKind.CREATED, ".brainvault/secret.md")))
        // Only the non-brainvault file should be indexed if we index it; the hidden one is dropped.
        // Verify with a full rebuild over the same vault that secret is excluded.
        assertEquals(0, notes.count())
        indexing.fullRebuild(root)
        assertEquals(1, notes.count())
    }
}
