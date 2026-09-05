package com.brainvault.infrastructure.persistence

import com.brainvault.domain.model.Note
import com.brainvault.domain.model.NoteMeta
import com.brainvault.domain.port.NoteRepository
import java.sql.ResultSet
import java.time.LocalDateTime

/**
 * [NoteRepository] over SQLite. The `notes` table is the source of truth for the
 * derived index; the raw Markdown files remain the true source. The FTS5
 * external-content table is kept in sync by triggers, so every write must go
 * through this repository (the hard write-path rule).
 */
class SqliteNoteRepository(private val db: Database) : NoteRepository {

    override fun upsert(note: Note): Long {
        return db.tx { conn ->
            // file_mtime / size_bytes are not on the domain model; the repo
            // records a current-time proxy and the body size at index time.
            conn.prepareStatement(UPSERT_SQL).use { ps ->
                ps.setString(1, note.path)
                ps.setString(2, note.title)
                ps.setString(3, note.body)
                ps.setString(4, note.meta.created?.toString())
                ps.setString(5, note.meta.modified?.toString())
                ps.setLong(6, System.currentTimeMillis())
                ps.setLong(7, note.body.toByteArray(Charsets.UTF_8).size.toLong())
                ps.executeUpdate()
            }
            conn.prepareStatement("SELECT id FROM notes WHERE path = ?").use { ps ->
                ps.setString(1, note.path)
                ps.executeQuery().use { rs ->
                    check(rs.next()) { "note upserted but id not found for ${note.path}" }
                    rs.getLong(1)
                }
            }
        }
    }

    override fun deleteByPath(path: String) {
        db.tx { conn ->
            conn.prepareStatement("DELETE FROM notes WHERE path = ?").use { ps ->
                ps.setString(1, path)
                ps.executeUpdate()
            }
        }
    }

    override fun findByPath(path: String): Note? {
        return db.connection.prepareStatement(
            "SELECT id, path, title, body, created, modified FROM notes WHERE path = ?",
        ).use { ps ->
            ps.setString(1, path)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toNote() else null }
        }
    }

    override fun findByTitle(title: String): Note? {
        return db.connection.prepareStatement(FIND_BY_TITLE_SQL).use { ps ->
            ps.setString(1, title)
            ps.setString(2, "%/$title.md")
            ps.setString(3, "$title.md")
            ps.executeQuery().use { rs -> if (rs.next()) rs.toNote() else null }
        }
    }

    override fun allPaths(): List<String> {
        val paths = mutableListOf<String>()
        db.connection.prepareStatement("SELECT path FROM notes ORDER BY path").use { ps ->
            ps.executeQuery().use { rs -> while (rs.next()) paths.add(rs.getString("path")) }
        }
        return paths
    }

    override fun count(): Int {
        db.connection.prepareStatement("SELECT COUNT(*) AS c FROM notes").use { ps ->
            ps.executeQuery().use { rs -> return rs.next().let { rs.getInt("c") } }
        }
    }

    private fun ResultSet.toNote(): Note {
        val id = getLong("id")
        val path = getString("path")
        val title = getString("title")
        val body = getString("body")
        val created = getString("created")?.let { parseLocal(it) }
        val modified = getString("modified")?.let { parseLocal(it) }
        // The repository does not re-derive front-matter tags (they live in the
        // normalized tags tables); meta.title is null and the effective title is
        // the note.title column. Extras are not reconstructed here.
        val meta = NoteMeta(title = null, tags = emptyList(), created = created, modified = modified)
        return Note(id = id, path = path, title = title, meta = meta, body = body)
    }

    private fun parseLocal(s: String): LocalDateTime = try {
        LocalDateTime.parse(s)
    } catch (e: Exception) {
        try {
            LocalDateTime.parse(s, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
        } catch (e2: Exception) {
            LocalDateTime.of(1970, 1, 1, 0, 0)
        }
    }

    private companion object {
        val UPSERT_SQL: String = """
            INSERT INTO notes (path, title, body, created, modified, file_mtime, size_bytes)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(path) DO UPDATE SET
                title = excluded.title,
                body = excluded.body,
                created = excluded.created,
                modified = excluded.modified,
                file_mtime = excluded.file_mtime,
                size_bytes = excluded.size_bytes
        """.trimIndent()

        val FIND_BY_TITLE_SQL: String = """
            SELECT id, path, title, body, created, modified FROM notes
            WHERE lower(title) = lower(?)
               OR lower(path) LIKE lower(?) ESCAPE '\'
               OR lower(path) LIKE lower(?) ESCAPE '\'
            LIMIT 1
        """.trimIndent()
    }
}
