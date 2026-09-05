package com.brainvault.infrastructure.persistence

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

/**
 * Owns the single shared SQLite connection used by all repositories. Applies
 * the three PRAGMAs on every connection and can bring up the schema idempotently.
 *
 * v1 threading rule: one writer thread via `Dispatchers.IO.limitedParallelism(1)`
 * in the UI layer serializes all access to the single shared connection. WAL
 * mode still allows concurrent readers while the writer holds the lock.
 */
// ============================================================
// LEARN[KJV-016] AutoCloseable vs Java try-with-resources / Closeable
// Kotlin:
//   `class Database(...) : AutoCloseable` implements Java's AutoCloseable. It is
//   closed with `use { }` (the stdlib extension) inside the composition root:
//   `Database(...).use { db -> ... }`. Kotlin's `use` guarantees `close()` runs
//   even on exception, like Java's try-with-resources.
// Java 25 equivalent:
//   try (Database db = new Database(file)) { ... } — the interface is the same.
// Differences:
//   - Kotlin `use` is an extension function on AutoCloseable/Closeable/InputStream,
//     so any compatible object gets RAII-style scoping without a language keyword.
// ============================================================
class Database(dbFile: Path) : AutoCloseable {

    val connection: Connection

    init {
        val abs = dbFile.toAbsolutePath()
        abs.parent?.let { Files.createDirectories(it) }
        connection = DriverManager.getConnection("jdbc:sqlite:$abs")
        connection.createStatement().use { st ->
            st.execute("PRAGMA journal_mode = WAL")
            st.execute("PRAGMA foreign_keys = ON")
            st.execute("PRAGMA busy_timeout = 5000")
        }
    }

    /**
     * Executes the §4.2 DDL. All statements are idempotent (`IF NOT EXISTS` /
     * `INSERT OR IGNORE`), so it is safe to call on every startup.
     */
    fun initSchema() {
        connection.createStatement().use { st ->
            SCHEMA_DDL.forEach { ddl -> st.execute(ddl) }
        }
    }

    /**
     * Runs [block] inside a transaction (begin/commit, rollback on throw) on the
     * shared connection. Because the connection is single-threaded in v1, this
     * is a plain begin/commit with no nested-transaction handling.
     */
    fun <T> tx(block: (Connection) -> T): T {
        val previousAutoCommit = connection.autoCommit
        connection.autoCommit = false
        try {
            val result = block(connection)
            connection.commit()
            return result
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = previousAutoCommit
        }
    }

    override fun close() {
        connection.close()
    }

    private companion object {
        // DDL from §4.2, one statement per element (trigger bodies contain `;`,
        // so a naive `;`-split would break them).
        val SCHEMA_DDL: List<String> = listOf(
            "CREATE TABLE IF NOT EXISTS schema_meta (" +
                "  key   TEXT PRIMARY KEY," +
                "  value TEXT NOT NULL" +
                ")",
            "INSERT OR IGNORE INTO schema_meta(key, value) VALUES ('schema_version', '1')",
            "CREATE TABLE IF NOT EXISTS notes (" +
                "  id          INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  path        TEXT    NOT NULL UNIQUE," +
                "  title       TEXT    NOT NULL," +
                "  body        TEXT    NOT NULL," +
                "  created     TEXT," +
                "  modified    TEXT," +
                "  file_mtime  INTEGER NOT NULL," +
                "  size_bytes  INTEGER NOT NULL" +
                ")",
            "CREATE VIRTUAL TABLE IF NOT EXISTS notes_fts USING fts5(" +
                "  title," +
                "  body," +
                "  content  = 'notes'," +
                "  content_rowid = 'id'," +
                "  tokenize = 'unicode61'" +
                ")",
            "CREATE TRIGGER IF NOT EXISTS notes_ai AFTER INSERT ON notes BEGIN" +
                "  INSERT INTO notes_fts(rowid, title, body) VALUES (new.id, new.title, new.body);" +
                "END",
            "CREATE TRIGGER IF NOT EXISTS notes_ad AFTER DELETE ON notes BEGIN" +
                "  INSERT INTO notes_fts(notes_fts, rowid, title, body)" +
                "  VALUES ('delete', old.id, old.title, old.body);" +
                "END",
            "CREATE TRIGGER IF NOT EXISTS notes_au AFTER UPDATE ON notes BEGIN" +
                "  INSERT INTO notes_fts(notes_fts, rowid, title, body)" +
                "  VALUES ('delete', old.id, old.title, old.body);" +
                "  INSERT INTO notes_fts(rowid, title, body) VALUES (new.id, new.title, new.body);" +
                "END",
            "CREATE TABLE IF NOT EXISTS tags (" +
                "  id   INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  name TEXT NOT NULL UNIQUE COLLATE NOCASE" +
                ")",
            "CREATE TABLE IF NOT EXISTS note_tags (" +
                "  note_id INTEGER NOT NULL REFERENCES notes(id) ON DELETE CASCADE," +
                "  tag_id  INTEGER NOT NULL REFERENCES tags(id) ON DELETE CASCADE," +
                "  PRIMARY KEY (note_id, tag_id)" +
                ")",
            "CREATE TABLE IF NOT EXISTS links (" +
                "  id         INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  source_id  INTEGER NOT NULL REFERENCES notes(id) ON DELETE CASCADE," +
                "  target_id  INTEGER REFERENCES notes(id) ON DELETE CASCADE," +
                "  raw_target TEXT    NOT NULL," +
                "  kind       TEXT    NOT NULL CHECK (kind IN ('wiki', 'markdown'))," +
                "  UNIQUE (source_id, raw_target, kind)" +
                ")",
            "CREATE INDEX IF NOT EXISTS idx_links_target ON links(target_id)",
            "CREATE TABLE IF NOT EXISTS favorites (" +
                "  note_id  INTEGER PRIMARY KEY REFERENCES notes(id) ON DELETE CASCADE," +
                "  added_at TEXT NOT NULL" +
                ")",
        )
    }
}
