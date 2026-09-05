package com.brainvault.application

import com.brainvault.infrastructure.config.PropertiesSettingsStore
import com.brainvault.infrastructure.fs.VaultFileStore
import com.brainvault.infrastructure.markdown.FrontMatterCodec
import com.brainvault.infrastructure.markdown.MarkdownParser
import com.brainvault.testutil.TestVaults
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DailyNoteServiceTest {

    private val fileStore = VaultFileStore()
    private val codec = FrontMatterCodec()
    private val parser = MarkdownParser()
    private val noteService = NoteService(fileStore, codec, parser)

    private fun settings(): SettingsService {
        val store = PropertiesSettingsStore(TestVaults.tempDir().resolve("config.properties"))
        return SettingsService(store)
    }

    private fun service(s: SettingsService) = DailyNoteService(noteService, s, fileStore)

    @Test
    fun `relPathFor formats according to pattern`() {
        val s = settings()
        s.dailyFolder = "daily"
        s.dailyPattern = "yyyy-MM-dd"
        assertEquals("daily/2026-09-05.md", service(s).relPathFor(LocalDate.of(2026, 9, 5)))
    }

    @Test
    fun `openOrCreate creates a daily note from the template`() {
        val root = TestVaults.tempDir()
        val s = settings()
        s.dailyFolder = "daily"
        val note = service(s).openOrCreate(root, LocalDate.of(2026, 9, 5))

        assertEquals("daily/2026-09-05.md", note.path)
        assertTrue(fileStore.exists(root, "daily/2026-09-05.md"))
        assertEquals(listOf("daily"), note.meta.tags)
        assertTrue(note.body.contains("2026-09-05"), "body: ${note.body}")
    }

    @Test
    fun `opening an existing daily note is idempotent`() {
        val root = TestVaults.tempDir()
        val s = settings()
        s.dailyFolder = "daily"
        val svc = service(s)
        val first = svc.openOrCreate(root, LocalDate.of(2026, 9, 5))
        val firstContent = fileStore.readText(root, first.path)

        val second = svc.openOrCreate(root, LocalDate.of(2026, 9, 5))
        assertEquals(first.path, second.path)
        assertEquals(firstContent, fileStore.readText(root, second.path))
    }
}
