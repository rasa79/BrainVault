package com.brainvault.infrastructure.persistence

import com.brainvault.domain.model.Tag
import com.brainvault.domain.port.TagRepository
import java.sql.Connection

/**
 * [TagRepository] over SQLite. Tags are normalized: `tags` holds unique names,
 * `note_tags` the many-to-many edges. A note's tag set is replaced wholesale via
 * `replaceTagsForNote` (delete-then-insert).
 */
class SqliteTagRepository(private val db: Database) : TagRepository {

    override fun replaceTagsForNote(noteId: Long, tags: List<String>) {
        db.tx { conn ->
            conn.prepareStatement("DELETE FROM note_tags WHERE note_id = ?").use { ps ->
                ps.setLong(1, noteId)
                ps.executeUpdate()
            }
            val cleanTags = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            for (tag in cleanTags) {
                conn.prepareStatement("INSERT OR IGNORE INTO tags(name) VALUES (?)").use { ps ->
                    ps.setString(1, tag)
                    ps.executeUpdate()
                }
                val tagId = selectTagId(conn, tag) ?: continue
                conn.prepareStatement(
                    "INSERT OR IGNORE INTO note_tags(note_id, tag_id) VALUES (?, ?)",
                ).use { ps ->
                    ps.setLong(1, noteId)
                    ps.setLong(2, tagId)
                    ps.executeUpdate()
                }
            }
        }
    }

    override fun allTagsWithCounts(): List<Tag> {
        // Drop any tag that no longer has an edge (count 0) within a transaction.
        db.tx { conn ->
            conn.createStatement().use { st ->
                st.executeUpdate(
                    "DELETE FROM tags WHERE id NOT IN (SELECT DISTINCT tag_id FROM note_tags)",
                )
            }
        }
        val result = mutableListOf<Tag>()
        db.connection.prepareStatement(
            "SELECT t.name, COUNT(nt.note_id) AS c FROM tags t " +
                "LEFT JOIN note_tags nt ON nt.tag_id = t.id " +
                "GROUP BY t.id, t.name ORDER BY t.name",
        ).use { ps ->
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    result += Tag(name = rs.getString("name"), noteCount = rs.getInt("c"))
                }
            }
        }
        return result
    }

    override fun notePathsForTag(tagName: String): List<String> {
        val paths = mutableListOf<String>()
        db.connection.prepareStatement(
            "SELECT n.path FROM notes n " +
                "JOIN note_tags nt ON nt.note_id = n.id " +
                "JOIN tags t ON t.id = nt.tag_id " +
                "WHERE t.name = ? ORDER BY n.path",
        ).use { ps ->
            ps.setString(1, tagName)
            ps.executeQuery().use { rs -> while (rs.next()) paths.add(rs.getString("path")) }
        }
        return paths
    }

    private fun selectTagId(conn: Connection, name: String): Long? {
        conn.prepareStatement("SELECT id FROM tags WHERE name = ?").use { ps ->
            ps.setString(1, name)
            ps.executeQuery().use { rs -> return if (rs.next()) rs.getLong(1) else null }
        }
    }
}
