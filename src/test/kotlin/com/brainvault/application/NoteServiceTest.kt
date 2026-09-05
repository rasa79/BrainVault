package com.brainvault.application

import com.brainvault.infrastructure.fs.VaultFileStore
import com.brainvault.infrastructure.markdown.FrontMatterCodec
import com.brainvault.infrastructure.markdown.MarkdownParser
import com.brainvault.testutil.TestVaults
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NoteServiceTest {

    private val fileStore = VaultFileStore()
    private val codec = FrontMatterCodec()
    private val parser = MarkdownParser()
    private val service = NoteService(fileStore, codec, parser)

    @Test
    fun `create writes a file with front matter`() {
        val root = TestVaults.tempDir()
        val note = service.create(root, "", "My Note")
        assertEquals("My Note.md", note.path)
        assertTrue(fileStore.exists(root, "My Note.md"))
        val back = service.read(root, "My Note.md")
        assertEquals("My Note", back.title)
        assertNotNull(back.meta.created)
        assertNotNull(back.meta.modified)
    }

    @Test
    fun `create appends a collision suffix`() {
        val root = TestVaults.tempDir()
        service.create(root, "", "My Note")
        val second = service.create(root, "", "My Note")
        assertEquals("My Note-2.md", second.path)
    }

    @Test
    fun `save writes body and refreshes modified`() {
        val root = TestVaults.tempDir()
        val note = service.create(root, "", "A")
        val withBody = note.copy(body = "Hello world")
        service.save(root, withBody)
        val back = service.read(root, "A.md")
        assertEquals("Hello world", back.body)
        assertNotNull(back.meta.created)
        assertNotNull(back.meta.modified)
    }

    @Test
    fun `front matter tags survive a save round trip`() {
        val root = TestVaults.tempDir()
        val note = service.create(root, "", "A")
        val edited = note.copy(meta = note.meta.copy(tags = listOf("x", "y")), body = "body")
        service.save(root, edited)
        val back = service.read(root, "A.md")
        assertEquals(listOf("x", "y"), back.meta.tags)
        assertEquals("body", back.body)
    }

    @Test
    fun `rename renames file and title`() {
        val root = TestVaults.tempDir()
        service.create(root, "", "Old")
        val newPath = service.rename(root, "Old.md", "New")
        assertEquals("New.md", newPath)
        assertFalse(fileStore.exists(root, "Old.md"))
        assertTrue(fileStore.exists(root, "New.md"))
        val read = service.read(root, "New.md")
        assertEquals("New", read.title)
        assertEquals("New", read.meta.title)
    }

    @Test
    fun `move relocates a note into a folder`() {
        val root = TestVaults.tempDir()
        service.create(root, "", "A")
        val newPath = service.move(root, "A.md", "sub")
        assertEquals("sub/A.md", newPath)
        assertFalse(fileStore.exists(root, "A.md"))
        assertTrue(fileStore.exists(root, "sub/A.md"))
        assertEquals("A", service.read(root, "sub/A.md").title)
    }

    @Test
    fun `delete removes the file`() {
        val root = TestVaults.tempDir()
        service.create(root, "", "A")
        service.delete(root, "A.md")
        assertFalse(fileStore.exists(root, "A.md"))
    }

    @Test
    fun `sanitize strips illegal filename characters`() {
        val root = TestVaults.tempDir()
        val note = service.create(root, "", "a/b:c*d")
        assertEquals("abcd.md", note.path)
    }
}
