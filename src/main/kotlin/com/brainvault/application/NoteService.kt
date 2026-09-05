package com.brainvault.application

import com.brainvault.domain.model.Link
import com.brainvault.domain.model.Note
import com.brainvault.domain.model.NoteMeta
import com.brainvault.infrastructure.fs.VaultFileStore
import com.brainvault.infrastructure.markdown.FrontMatterCodec
import com.brainvault.infrastructure.markdown.MarkdownParser
import java.nio.file.Path
import java.time.LocalDateTime

/**
 * Note CRUD over the filesystem. Reads/writes raw Markdown files through the
 * [VaultFileStore], splitting/merging the YAML front matter via the [FrontMatterCodec].
 * This is the approved `application → infrastructure.fs` / `infrastructure.markdown`
 * exception (§5.2): these are filesystem/parsing utilities with no persistence semantics.
 */
class NoteService(
    private val fileStore: VaultFileStore,
    private val codec: FrontMatterCodec,
    private val parser: MarkdownParser,
) {
    private val illegal = setOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')

    /** Reads a note from disk, stripping the front-matter block into [Note.body]. */
    fun read(vaultRoot: Path, relPath: String): Note {
        val rel = relPath.replace('\\', '/')
        val raw = fileStore.readText(vaultRoot, rel)
        val (rawMeta, body) = codec.split(raw)
        val meta = codec.toMeta(rawMeta)
        val title = meta.effectiveTitle(rel.substringAfterLast("/").removeSuffix(".md"))
        return Note(id = 0, path = rel, title = title, meta = meta, body = body)
    }

    /** Creates a new note with a collision-safe filename; returns the new note. */
    fun create(vaultRoot: Path, folder: String, title: String): Note {
        val relPath = uniquePath(vaultRoot, folder, sanitize(title))
        val now = LocalDateTime.now()
        val meta = NoteMeta(title = title, tags = emptyList(), created = now, modified = now)
        val note = Note(id = 0, path = relPath, title = title, meta = meta, body = "")
        fileStore.writeText(vaultRoot, relPath, codec.serialize(meta, ""))
        return note
    }

    /**
     * Saves a note atomically. `created` is set once (if absent) and `modified`
     * is refreshed to now on every save. Front matter + body are written together.
     */
    fun save(vaultRoot: Path, note: Note) {
        val now = LocalDateTime.now()
        val meta = if (note.meta.created != null) note.meta.withModified(now) else note.meta.copy(created = now, modified = now)
        fileStore.writeText(vaultRoot, note.path, codec.serialize(meta, note.body))
    }

    fun delete(vaultRoot: Path, relPath: String) {
        fileStore.delete(vaultRoot, relPath.replace('\\', '/'))
    }

    /**
     * Renames a note file (and its front-matter title), returning the new
     * vault-relative path. Collisions get a `-2`, `-3`, … suffix.
     */
    fun rename(vaultRoot: Path, relPath: String, newTitle: String): String {
        val rel = relPath.replace('\\', '/')
        val folder = rel.substringBeforeLast('/', "")
        val newPath = uniquePath(vaultRoot, folder, sanitize(newTitle), exclude = rel)
        if (newPath == rel) return rel
        val note = read(vaultRoot, rel)
        val meta = note.meta.copy(title = newTitle)
        fileStore.writeText(vaultRoot, newPath, codec.serialize(meta, note.body))
        fileStore.delete(vaultRoot, rel)
        return newPath
    }

    /** Moves a note into [targetFolder] (vault-relative), returning the new path. */
    fun move(vaultRoot: Path, relPath: String, targetFolder: String): String {
        val rel = relPath.replace('\\', '/')
        val base = rel.substringAfterLast("/").removeSuffix(".md")
        val newPath = uniquePath(vaultRoot, targetFolder, base, exclude = rel)
        if (newPath == rel) return rel
        fileStore.move(vaultRoot, rel, newPath)
        return newPath
    }

    fun renderHtml(body: String): String = parser.renderHtml(body)

    /** Raw link extraction; `sourcePath`/`targetPath` are filled by the caller. */
    fun extractLinks(body: String): List<Link> = parser.extractLinks(body)

    private fun sanitize(title: String): String {
        val cleaned = title.filter { it !in illegal }.trim()
        return if (cleaned.isEmpty()) "untitled" else cleaned
    }

    private fun uniquePath(vaultRoot: Path, folder: String, base: String, exclude: String? = null): String {
        val prefix = if (folder.isEmpty()) "" else "${folder.trim('/')}/"
        var candidate = "$prefix$base.md"
        var i = 2
        while (fileStore.exists(vaultRoot, candidate) && candidate != exclude) {
            candidate = "$prefix$base-$i.md"
            i++
        }
        return candidate
    }
}
