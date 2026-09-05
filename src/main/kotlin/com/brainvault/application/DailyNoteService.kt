package com.brainvault.application

import com.brainvault.domain.model.Note
import com.brainvault.infrastructure.fs.VaultFileStore
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Daily-note creation/opening. The daily note lives at
 * `${dailyFolder}/${date.format(dailyPattern)}.md`, created from the daily
 * template on first open. An existing daily note is opened verbatim.
 *
 * NOTE (deviation, logged in DECISIONS.md): the template substitution writes a
 * full file (front matter AND body) so this service also takes a [VaultFileStore]
 * to honor arbitrary configured templates exactly.
 */
class DailyNoteService(
    private val noteService: NoteService,
    private val settings: SettingsService,
    private val fileStore: VaultFileStore,
) {

    fun relPathFor(date: LocalDate): String {
        val folder = settings.dailyFolder.trim('/')
        val dateStr = date.format(DateTimeFormatter.ofPattern(settings.dailyPattern))
        return if (folder.isEmpty()) "$dateStr.md" else "$folder/$dateStr.md"
    }

    /** Creates today's (or [date]'s) daily note from the template if absent. */
    fun openOrCreate(vaultRoot: Path, date: LocalDate): Note {
        val relPath = relPathFor(date)
        if (fileStore.exists(vaultRoot, relPath)) {
            return noteService.read(vaultRoot, relPath)
        }
        val dateStr = date.format(DateTimeFormatter.ofPattern(settings.dailyPattern))
        val now = LocalDateTime.now()
        val content = settings.dailyTemplate
            .replace("{{date}}", dateStr)
            .replace("{{title}}", dateStr)
            .replace("{{created}}", now.toString())
        fileStore.writeText(vaultRoot, relPath, content)
        return noteService.read(vaultRoot, relPath)
    }
}
