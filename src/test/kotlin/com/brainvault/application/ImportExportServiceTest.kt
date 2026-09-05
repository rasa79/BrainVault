package com.brainvault.application

import com.brainvault.infrastructure.fs.VaultFileStore
import com.brainvault.testutil.TestVaults
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.util.zip.ZipInputStream

class ImportExportServiceTest {

    private val fileStore = VaultFileStore()
    private val service = ImportExportService(fileStore)

    @Test
    fun `import copies md files with collision suffixes`() {
        val root = TestVaults.tempDir()
        val src = TestVaults.tempDir()
        TestVaults.write(src, "a.md", "# A")
        TestVaults.write(src, "sub/b.md", "# B")

        val n = service.importFolder(root, src, "imported")
        assertEquals(2, n)
        assertTrue(fileStore.exists(root, "imported/a.md"))
        assertTrue(fileStore.exists(root, "imported/sub/b.md"))

        // Re-import → collisions get -2 suffix.
        service.importFolder(root, src, "imported")
        assertTrue(fileStore.exists(root, "imported/a-2.md"))
        assertTrue(fileStore.exists(root, "imported/sub/b-2.md"))
    }

    @Test
    fun `import excludes brainvault from source`() {
        val root = TestVaults.tempDir()
        val src = TestVaults.tempDir()
        TestVaults.write(src, "ok.md", "# ok")
        TestVaults.write(src, ".brainvault/secret.md", "# secret")

        val n = service.importFolder(root, src, "imported")
        assertEquals(1, n)
        assertTrue(fileStore.exists(root, "imported/ok.md"))
        assertFalse(fileStore.exists(root, "imported/.brainvault/secret.md"))
    }

    @Test
    fun `export to folder preserves vault structure`() {
        val root = TestVaults.tempDir()
        TestVaults.write(root, "a.md", "A")
        TestVaults.write(root, "sub/b.md", "B")
        TestVaults.write(root, ".brainvault/index.db", "not a note")
        val target = TestVaults.tempDir()

        val n = service.exportToFolder(root, target)
        assertEquals(2, n)
        assertTrue(Files.exists(target.resolve("a.md")))
        assertTrue(Files.exists(target.resolve("sub/b.md")))
    }

    @Test
    fun `export to zip uses forward-slash names and excludes brainvault`() {
        val root = TestVaults.tempDir()
        TestVaults.write(root, "sub/a.md", "A")
        TestVaults.write(root, ".brainvault/secret.md", "secret")
        val zip = TestVaults.tempDir().resolve("out.zip")

        service.exportToZip(root, zip)

        val entries = mutableListOf<String>()
        ZipInputStream(Files.newInputStream(zip)).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                entries.add(e.name)
                zis.closeEntry()
                e = zis.nextEntry
            }
        }
        assertEquals(listOf("sub/a.md"), entries)
    }
}
