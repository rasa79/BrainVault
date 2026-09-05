package com.brainvault.application

import com.brainvault.domain.model.Link
import com.brainvault.domain.model.LinkKind
import com.brainvault.domain.model.Note
import com.brainvault.domain.port.FavoriteRepository
import com.brainvault.domain.port.FileEvent
import com.brainvault.domain.port.FileEventKind
import com.brainvault.domain.port.LinkRepository
import com.brainvault.domain.port.NoteRepository
import com.brainvault.domain.port.SearchIndex
import com.brainvault.domain.port.TagRepository
import com.brainvault.infrastructure.fs.VaultFileStore
import com.brainvault.infrastructure.markdown.FrontMatterCodec
import com.brainvault.infrastructure.markdown.MarkdownParser
import java.nio.file.Path

/**
 * Rebuilds and incrementally updates the derived index from the Markdown files.
 *
 * NOTE (deviation, logged in DECISIONS.md): the spec's `fullRebuild` contract says
 * favorites for still-existing paths are re-created after a rebuild, which needs
 * the [FavoriteRepository]. It was therefore added to the constructor.
 */
class IndexingService(
    private val notes: NoteRepository,
    private val tags: TagRepository,
    private val links: LinkRepository,
    private val index: SearchIndex,
    private val favorites: FavoriteRepository,
    private val fileStore: VaultFileStore,
    private val codec: FrontMatterCodec,
    private val parser: MarkdownParser,
) {

    /**
     * Clears all derived tables and reindexes every `*.md` file in the vault.
     * Favorites for paths that still exist are restored after the rebuild by
     * matching path → new rowid. Progress is reported as (done, total).
     */
    fun fullRebuild(vaultRoot: Path, progress: (done: Int, total: Int) -> Unit = { _, _ -> }) {
        val favoritePaths = rememberFavoritePaths()
        val rels = fileStore.listMarkdown(vaultRoot)
        index.clear()
        var done = 0
        for (rel in rels) {
            indexOne(vaultRoot, rel)
            done++
            progress(done, rels.size)
        }
        index.rebuild()
        restoreFavorites(favoritePaths)
    }

    /** Incremental reindex from a batch of watcher events. */
    fun onFileEvents(vaultRoot: Path, events: List<FileEvent>) {
        for (event in events) {
            val path = event.vaultRelativePath
            if (path.isEmpty()) {
                // Synthetic full-rescan signal (watcher overflow).
                fullRebuild(vaultRoot)
                continue
            }
            if (path.startsWith(".brainvault/")) continue
            when (event.kind) {
                FileEventKind.CREATED, FileEventKind.MODIFIED -> {
                    if (fileStore.exists(vaultRoot, path)) indexOne(vaultRoot, path)
                }
                FileEventKind.DELETED -> notes.deleteByPath(path)
            }
        }
    }

    /** Reads, parses and indexes a single note file (front matter + body). */
    fun indexOne(vaultRoot: Path, relPath: String) {
        val rel = relPath.replace('\\', '/')
        val raw = fileStore.readText(vaultRoot, rel)
        val (rawMeta, body) = codec.split(raw)
        val meta = codec.toMeta(rawMeta)
        val title = meta.effectiveTitle(rel.substringAfterLast("/").removeSuffix(".md"))
        val note = Note(id = 0, path = rel, title = title, meta = meta, body = body)

        val sourceId = notes.upsert(note)
        tags.replaceTagsForNote(sourceId, meta.tags)
        val resolved = resolveLinks(rel, body)
        links.replaceLinksFrom(sourceId, resolved)
    }

    /**
     * Resolves raw links from the body into concrete [Link]s. Wiki links resolve
     * by title/filename; Markdown links resolve relative to the source note's
     * folder, decoding `%20`. Unresolved targets are kept with a null target.
     */
    private fun resolveLinks(sourceRel: String, body: String): List<Link> {
        val sourceFolder = sourceRel.substringBeforeLast('/', "")
        return parser.extractLinks(body).map { link ->
            // ============================================================
            // LEARN[KJV-022] Sealed classes/interfaces & exhaustive `when`
            // Kotlin:
            //   `link.kind` is a LinkKind enum. Here `when (link.kind)` has a
            //   branch for WIKI and MARKDOWN and NO `else`. The compiler checks
            //   exhaustiveness: because the subject is a closed set (an enum, or
            //   a sealed class/interface hierarchy), a `when` covering all cases
            //   is exhaustive and needs no default. If a new enum constant were
            //   added, this would fail to compile — a safety Java's switch lacks.
            // Java 25 equivalent:
            //   switch (link.kind) { ... } still requires an explicit default (or
            //   a throw) even when all cases are listed, because enum constants
            //   are not guaranteed closed outside the switch's own source file.
            // Differences:
            //   - Kotlin `when` over a sealed hierarchy is checked exhaustively;
            //     Java switch is not checked (needs a `default` branch).
            // ============================================================
            when (link.kind) {
                LinkKind.WIKI -> {
                    val target = notes.findByTitle(link.rawTarget)
                    link.copy(sourcePath = sourceRel, targetPath = target?.path)
                }
                LinkKind.MARKDOWN -> {
                    val decoded = link.rawTarget.replace("%20", " ")
                    val candidate = if (sourceFolder.isEmpty()) decoded else "$sourceFolder/$decoded"
                    val target = notes.findByPath(candidate)
                    link.copy(sourcePath = sourceRel, targetPath = target?.path)
                }
            }
        }
    }

    private fun rememberFavoritePaths(): Set<String> {
        val paths = mutableSetOf<String>()
        for (path in notes.allPaths()) {
            val id = notes.findByPath(path)?.id ?: continue
            if (favorites.isFavorite(id)) paths.add(path)
        }
        return paths
    }

    private fun restoreFavorites(paths: Set<String>) {
        for (path in paths) {
            val id = notes.findByPath(path)?.id ?: continue
            favorites.add(id)
        }
    }
}
