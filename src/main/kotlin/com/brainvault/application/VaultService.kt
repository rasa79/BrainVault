package com.brainvault.application

import com.brainvault.domain.model.FolderNode
import com.brainvault.infrastructure.fs.VaultFileStore
import java.nio.file.Files
import java.nio.file.Path

/**
 * Scans a vault folder and presents it as a domain tree. No SQL and no
 * persistence imports — it only reads the filesystem via the injected
 * [VaultFileStore].
 */
class VaultService(private val fileStore: VaultFileStore) {

    /** Recursive scan, `.brainvault` excluded, folders and notes sorted. */
    fun tree(vaultRoot: Path): FolderNode {
        val root = vaultRoot.toAbsolutePath().normalize()
        return buildNode(root, rel = "", vaultRoot = root)
    }

    /**
     * All `*.md` files as absolute paths, ordered by vault-relative path. Use
     * [VaultFileStore.toRel] (relativize) to get a forward-slash vault-relative
     * string — do not hand-roll path prefix-stripping.
     */
    fun markdownFiles(vaultRoot: Path): List<Path> {
        val root = vaultRoot.toAbsolutePath().normalize()
        return fileStore.listMarkdown(root).map { root.resolve(it) }
    }

    private fun buildNode(dir: Path, rel: String, vaultRoot: Path): FolderNode {
        val folders = mutableListOf<FolderNode>()
        val notes = mutableListOf<String>()
        Files.newDirectoryStream(dir).use { stream ->
            for (p in stream) {
                val name = p.fileName.toString()
                if (name == ".brainvault") continue
                if (Files.isDirectory(p)) {
                    val childRel = if (rel.isEmpty()) name else "$rel/$name"
                    folders += buildNode(p, childRel, vaultRoot)
                } else if (name.lowercase().endsWith(".md")) {
                    val noteRel = if (rel.isEmpty()) name else "$rel/$name"
                    notes += noteRel
                }
            }
        }
        val nodeName = if (rel.isEmpty()) "" else dir.fileName.toString()
        return FolderNode(
            name = nodeName,
            path = rel,
            folders = folders.sortedBy { it.name.lowercase() },
            notePaths = notes.sorted(),
        )
    }
}
