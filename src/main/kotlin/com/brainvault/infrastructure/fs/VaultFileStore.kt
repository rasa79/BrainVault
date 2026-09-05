package com.brainvault.infrastructure.fs

import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Filesystem access for a vault. All methods take a vault-relative path with
 * forward slashes and return vault-relative paths with forward slashes, on
 * every OS (`toRel` handles the direction conversion). The `.brainvault/`
 * directory is never listed or traversed.
 */
class VaultFileStore {

    fun readText(vaultRoot: Path, relPath: String): String =
        Files.readString(resolve(root(vaultRoot), relPath))

    /** Atomic write: write to a temp file in the same directory, then move. */
    fun writeText(vaultRoot: Path, relPath: String, content: String) {
        val target = resolve(root(vaultRoot), relPath)
        Files.createDirectories(target.parent)
        val tmp = Files.createTempFile(target.parent, ".bv-", ".tmp")
        try {
            Files.writeString(tmp, content)
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(tmp)
        }
    }

    fun delete(vaultRoot: Path, relPath: String) {
        Files.deleteIfExists(resolve(root(vaultRoot), relPath))
    }

    /** Moves [fromRel] to [toRel], creating target directories. */
    fun move(vaultRoot: Path, fromRel: String, toRel: String) {
        val from = resolve(root(vaultRoot), fromRel)
        val to = resolve(root(vaultRoot), toRel)
        Files.createDirectories(to.parent)
        Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
    }

    // ============================================================
    // LEARN[KJV-017] Java Streams → Kotlin pipelines (Files.walk)
    // Kotlin:
    //   `Files.walk(root).use { stream -> stream.filter{...}.map{...}.sorted()
    //   .toList() }` turns a Java `java.util.stream.Stream` into a Kotlin list
    //   via `.toList()`. The `.use { }` closes the Stream (it is AutoCloseable),
    //   which Java's Stream API requires you to remember to close when backed by
    //   a file handle.
    // Java 25 equivalent:
    //   Files.walk(root).filter(...).map(...).sorted().toList(); (Stream)
    // Differences:
    //   - Kotlin borrows the Java file APIs as-is but adapts them with function
    //     types; the pipeline reads left-to-right like the Kotlin collection ops.
    //   - In Kotlin you still must close the Stream (via .use) — .toList()
    //     materializes without closing the file-backed source.
    // ============================================================
    fun listMarkdown(vaultRoot: Path): List<String> {
        val root = root(vaultRoot)
        val bv = root.resolve(".brainvault")
        return Files.walk(root, FileVisitOption.FOLLOW_LINKS).use { stream ->
            stream.filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().lowercase().endsWith(".md") }
                .filter { !it.isUnderDir(bv) }
                .map { toRel(root, it) }
                .sorted()
                .toList()
        }
    }

    fun listFolders(vaultRoot: Path): List<String> {
        val root = root(vaultRoot)
        val bv = root.resolve(".brainvault")
        return Files.walk(root, FileVisitOption.FOLLOW_LINKS).use { stream ->
            stream.filter { Files.isDirectory(it) }
                .filter { it.normalize() != root && !it.isUnderDir(bv) }
                .map { toRel(root, it) }
                .sorted()
                .toList()
        }
    }

    // ============================================================
    // LEARN[KJV-021] Extension functions vs Java static helpers
    // Kotlin:
    //   `private fun Path.isUnderDir(dir: Path)` adds a *new* method to the
    //   existing `java.nio.file.Path` type without touching its source. Inside,
    //   `this` is the receiver Path, so it reads like a member call:
    //   `it.isUnderDir(bv)`. The function is private to this file.
    // Java 25 equivalent:
    //   A static utility method `PathUtil.isUnder(Path p, Path dir)` — the path is
    //   an explicit first argument, so the call site is longer and the type cannot
    //   be "extended" without subclassing or decorators.
    // Differences:
    //   - Extensions let you add behavior to third-party types with member syntax.
    //   - They are NOT dispatched virtually (the receiver is a static parameter),
    //     so a Java static helper is semantically closest.
    // ============================================================
    private fun Path.isUnderDir(dir: Path): Boolean = normalize().startsWith(dir.normalize())

    fun createFolder(vaultRoot: Path, relFolder: String) {
        Files.createDirectories(resolve(root(vaultRoot), relFolder))
    }

    /** Converts an absolute path to a vault-relative, forward-slash path. */
    fun toRel(vaultRoot: Path, absolute: Path): String {
        val root = root(vaultRoot)
        val rel = root.relativize(absolute.normalize())
        return rel.toString().replace('\\', '/')
    }

    fun exists(vaultRoot: Path, relPath: String): Boolean =
        Files.exists(resolve(root(vaultRoot), relPath))

    private fun root(vaultRoot: Path): Path = vaultRoot.toAbsolutePath().normalize()

    private fun resolve(root: Path, rel: String): Path {
        val target = root.resolve(rel).normalize()
        if (!target.startsWith(root)) {
            throw IllegalArgumentException("path escapes vault: $rel")
        }
        return target
    }
}
