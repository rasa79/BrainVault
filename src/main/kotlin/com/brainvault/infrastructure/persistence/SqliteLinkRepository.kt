package com.brainvault.infrastructure.persistence

import com.brainvault.domain.model.Link
import com.brainvault.domain.model.LinkKind
import com.brainvault.domain.port.LinkRepository
import java.sql.Connection

/**
 * [LinkRepository] over SQLite. Each source note's links are replaced wholesale.
 * The caller resolves a link's target to a vault-relative path; this repository
 * maps paths to rowids (so it stays persistence-dumb).
 */
class SqliteLinkRepository(private val db: Database) : LinkRepository {

    override fun replaceLinksFrom(sourceId: Long, links: List<Link>) {
        db.tx { conn ->
            conn.prepareStatement("DELETE FROM links WHERE source_id = ?").use { ps ->
                ps.setLong(1, sourceId)
                ps.executeUpdate()
            }
            for (link in links) {
                val targetId = link.targetPath?.let { findNoteId(conn, it) }
                conn.prepareStatement(
                    "INSERT INTO links (source_id, target_id, raw_target, kind) VALUES (?, ?, ?, ?)",
                ).use { ps ->
                    ps.setLong(1, sourceId)
                    if (targetId == null) ps.setNull(2, java.sql.Types.INTEGER) else ps.setLong(2, targetId)
                    ps.setString(3, link.rawTarget)
                    ps.setString(4, link.kind.name.lowercase())
                    ps.executeUpdate()
                }
            }
        }
    }

    override fun backlinksFor(targetId: Long): List<Link> {
        val result = mutableListOf<Link>()
        db.connection.prepareStatement(
            "SELECT src.path AS source_path, tgt.path AS target_path, l.raw_target, l.kind " +
                "FROM links l " +
                "JOIN notes src ON src.id = l.source_id " +
                "JOIN notes tgt ON tgt.id = l.target_id " +
                "WHERE l.target_id = ? " +
                "ORDER BY src.path",
        ).use { ps ->
            ps.setLong(1, targetId)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    result += Link(
                        sourcePath = rs.getString("source_path"),
                        targetPath = rs.getString("target_path"),
                        rawTarget = rs.getString("raw_target"),
                        kind = parseKind(rs.getString("kind")),
                    )
                }
            }
        }
        return result
    }

    override fun unresolvedLinks(): List<Link> {
        val result = mutableListOf<Link>()
        db.connection.prepareStatement(
            "SELECT src.path AS source_path, l.raw_target, l.kind " +
                "FROM links l JOIN notes src ON src.id = l.source_id " +
                "WHERE l.target_id IS NULL",
        ).use { ps ->
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    result += Link(
                        sourcePath = rs.getString("source_path"),
                        targetPath = null,
                        rawTarget = rs.getString("raw_target"),
                        kind = parseKind(rs.getString("kind")),
                    )
                }
            }
        }
        return result
    }

    private fun findNoteId(conn: Connection, path: String): Long? {
        conn.prepareStatement("SELECT id FROM notes WHERE path = ?").use { ps ->
            ps.setString(1, path)
            ps.executeQuery().use { rs -> return if (rs.next()) rs.getLong(1) else null }
        }
    }

    private fun parseKind(raw: String): LinkKind {
        return try {
            LinkKind.valueOf(raw.uppercase())
        } catch (e: Exception) {
            LinkKind.MARKDOWN
        }
    }
}
