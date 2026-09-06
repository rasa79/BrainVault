package com.brainvault.application

import com.brainvault.infrastructure.fs.VaultFileStore
import com.brainvault.testutil.TestVaults
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VaultServiceTest {

    private val fileStore = VaultFileStore()
    private val service = VaultService(fileStore)

    @Test
    fun `tree builds nested folders and notes`() {
        val root = TestVaults.tempDir()
        TestVaults.write(root, "a.md", "# A")
        TestVaults.write(root, "sub/b.md", "# B")
        TestVaults.write(root, "sub/deep/c.md", "# C")
        TestVaults.write(root, ".brainvault/x.md", "# hidden")

        val tree = service.tree(root)
        assertEquals("", tree.name)
        assertEquals("", tree.path)
        assertEquals(listOf("a.md"), tree.notePaths)
        assertEquals(1, tree.folders.size)

        val sub = tree.folders[0]
        assertEquals("sub", sub.name)
        assertEquals("sub", sub.path)
        assertEquals(listOf("sub/b.md"), sub.notePaths)
        assertEquals(1, sub.folders.size)

        val deep = sub.folders[0]
        assertEquals("deep", deep.name)
        assertEquals("sub/deep", deep.path)
        assertEquals(listOf("sub/deep/c.md"), deep.notePaths)
    }

    @Test
    fun `markdownFiles sorts and excludes brainvault`() {
        val root = TestVaults.tempDir()
        TestVaults.write(root, "b.md", "# B")
        TestVaults.write(root, "a.md", "# A")
        TestVaults.write(root, "sub/c.md", "# C")
        TestVaults.write(root, ".brainvault/secret.md", "# x")

        // Convert the returned absolute Paths to vault-relative forward-slash
        // strings via toRel (relativize) — this is OS-independent, unlike
        // hand-rolled prefix-stripping with a hardcoded '/'.
        val rels = service.markdownFiles(root).map { fileStore.toRel(root, it) }
        assertEquals(listOf("a.md", "b.md", "sub/c.md"), rels)
    }
}
