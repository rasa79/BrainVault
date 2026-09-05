package com.brainvault.infrastructure.fs

import com.brainvault.testutil.TestVaults
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VaultFileStoreTest {

    private val store = VaultFileStore()

    @Test
    fun `write and read text round trip`() {
        val root = TestVaults.tempDir()
        store.writeText(root, "sub/a.md", "hello")
        assertTrue(store.exists(root, "sub/a.md"))
        assertEquals("hello", store.readText(root, "sub/a.md"))
    }

    @Test
    fun `writeText is atomic via move`() {
        val root = TestVaults.tempDir()
        store.writeText(root, "a.md", "x")
        store.writeText(root, "a.md", "y")
        assertEquals("y", store.readText(root, "a.md"))
    }

    @Test
    fun `move creates target directories`() {
        val root = TestVaults.tempDir()
        store.writeText(root, "a.md", "x")
        store.move(root, "a.md", "sub/deep/b.md")
        assertFalse(store.exists(root, "a.md"))
        assertTrue(store.exists(root, "sub/deep/b.md"))
        assertEquals("x", store.readText(root, "sub/deep/b.md"))
    }

    @Test
    fun `delete removes the file`() {
        val root = TestVaults.tempDir()
        store.writeText(root, "a.md", "x")
        store.delete(root, "a.md")
        assertFalse(store.exists(root, "a.md"))
    }

    @Test
    fun `listMarkdown excludes brainvault`() {
        val root = TestVaults.tempDir()
        store.writeText(root, "a.md", "1")
        store.writeText(root, "sub/b.md", "2")
        store.writeText(root, ".brainvault/secret.md", "hidden")
        store.writeText(root, ".brainvault/index.db", "not a note")
        assertEquals(listOf("a.md", "sub/b.md"), store.listMarkdown(root))
    }

    @Test
    fun `listFolders excludes brainvault and root`() {
        val root = TestVaults.tempDir()
        store.createFolder(root, "sub/deep")
        store.createFolder(root, ".brainvault")
        assertEquals(listOf("sub", "sub/deep"), store.listFolders(root))
    }

    @Test
    fun `toRel uses forward slashes`() {
        val root = TestVaults.tempDir()
        val abs = root.resolve("sub").resolve("a.md")
        assertEquals("sub/a.md", store.toRel(root, abs))
    }

    @Test
    fun `path escaping is rejected`() {
        val root = TestVaults.tempDir()
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            store.readText(root, "../outside.md")
        }
    }
}
