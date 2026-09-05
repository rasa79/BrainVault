package com.brainvault.application

import com.brainvault.infrastructure.fs.VaultFileStore
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Import/export of Markdown vault content.
 *
 * - Import: copies `*.md` recursively from a source folder into
 *   `vaultRoot/targetSubfolder`, preserving relative subpaths, never overwriting
 *   (collisions get `-2`, `-3`, …). `.brainvault/` in the source is skipped.
 * - Export folder: copies every vault `.md` (excluding `.brainvault/`) to a
 *   target folder, overwriting only within that explicitly-chosen target.
 * - Export zip: one entry per vault `.md`, forward-slash vault-relative name.
 */
class ImportExportService(private val fileStore: VaultFileStore) {

    /** @return the number of files copied. */
    fun importFolder(
        vaultRoot: Path,
        source: Path,
        targetSubfolder: String,
        progress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Int {
        val src = source.toAbsolutePath().normalize()
        val files = Files.walk(src).use { stream ->
            stream.filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().lowercase().endsWith(".md") }
                .filter { !isUnderBrainvault(src, it) }
                .sorted()
                .toList()
        }
        var done = 0
        for (file in files) {
            val rel = src.relativize(file).toString().replace('\\', '/')
            val destRel = (if (targetSubfolder.trim('/').isEmpty()) "" else "${targetSubfolder.trim('/')}/") + rel
            val dest = uniqueImportPath(vaultRoot, destRel)
            fileStore.writeText(vaultRoot, dest, Files.readString(file))
            done++
            progress(done, files.size)
        }
        return done
    }

    /** @return the number of files copied. */
    fun exportToFolder(vaultRoot: Path, target: Path): Int {
        val rels = fileStore.listMarkdown(vaultRoot)
        for (rel in rels) {
            val dest = target.resolve(rel)
            Files.createDirectories(dest.parent)
            Files.writeString(dest, fileStore.readText(vaultRoot, rel))
        }
        return rels.size
    }

    fun exportToZip(vaultRoot: Path, zipFile: Path) {
        val rels = fileStore.listMarkdown(vaultRoot)
        ZipOutputStream(Files.newOutputStream(zipFile)).use { zos ->
            for (rel in rels) {
                zos.putNextEntry(ZipEntry(rel))
                zos.write(fileStore.readText(vaultRoot, rel).toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
    }

    private fun uniqueImportPath(vaultRoot: Path, rel: String): String {
        var candidate = rel
        var i = 2
        while (fileStore.exists(vaultRoot, candidate)) {
            candidate = "${rel.removeSuffix(".md")}-$i.md"
            i++
        }
        return candidate
    }

    private fun isUnderBrainvault(src: Path, file: Path): Boolean {
        val rel = src.relativize(file).toString().replace('\\', '/')
        return rel.startsWith(".brainvault/") || rel == ".brainvault"
    }
}
