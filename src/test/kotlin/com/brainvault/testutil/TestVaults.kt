package com.brainvault.testutil

import com.brainvault.domain.model.Note
import com.brainvault.domain.model.NoteMeta
import java.nio.file.Files
import java.nio.file.Path

/**
 * Shared helpers for building temp vault directories, temp SQLite databases,
 * and note fixtures in tests. Nothing here touches the real vault or
 * `${user.home}` — all tests run against throwaway @TempDir-like locations.
 */
object TestVaults {

    /** Creates an empty temp directory (used by @TempDir-style fixtures). */
    fun tempDir(prefix: String = "brainvault-test"): Path =
        Files.createTempDirectory(prefix)

    /** Creates a fresh, empty temp SQLite database file path. */
    fun tempDbFile(): Path = Files.createTempFile("brainvault-test", ".db")

    /** Writes [content] to `root/rel`, creating parent directories. */
    fun write(root: Path, rel: String, content: String): Path {
        val p = root.resolve(rel)
        Files.createDirectories(p.parent)
        Files.writeString(p, content)
        return p
    }

    /** A representative note file with front matter (title, tags, extra key). */
    fun sampleNote(title: String = "Kotlin vs Java", tags: List<String> = listOf("kotlin", "java")): String {
        val tagList = tags.joinToString(", ") { "\"$it\"" }
        return "---\n" +
            "title: \"$title\"\n" +
            "tags: [$tagList]\n" +
            "rating: 4\n" +
            "---\n\n" +
            "# $title\n\nBody text."
    }

    /** Builds a persisted-style [Note] for repository/indexing tests. */
    fun note(
        path: String,
        title: String = path.removeSuffix(".md").substringAfterLast("/"),
        body: String = "body",
        tags: List<String> = emptyList(),
    ): Note = Note(
        id = 0,
        path = path,
        title = title,
        meta = NoteMeta(title = title, tags = tags),
        body = body,
    )
}
