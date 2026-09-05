package com.brainvault.infrastructure.persistence

import com.brainvault.domain.port.FavoriteRepository
import java.time.LocalDateTime

/**
 * [FavoriteRepository] over SQLite. Favorites are simple (note_id, added_at)
 * pairs; `added_at` is an ISO-8601 local datetime written at toggle time.
 */
class SqliteFavoriteRepository(private val db: Database) : FavoriteRepository {

    override fun add(noteId: Long) {
        db.tx { conn ->
            conn.prepareStatement(
                "INSERT OR IGNORE INTO favorites (note_id, added_at) VALUES (?, ?)",
            ).use { ps ->
                ps.setLong(1, noteId)
                ps.setString(2, LocalDateTime.now().toString())
                ps.executeUpdate()
            }
        }
    }

    override fun remove(noteId: Long) {
        db.tx { conn ->
            conn.prepareStatement("DELETE FROM favorites WHERE note_id = ?").use { ps ->
                ps.setLong(1, noteId)
                ps.executeUpdate()
            }
        }
    }

    override fun isFavorite(noteId: Long): Boolean {
        db.connection.prepareStatement(
            "SELECT 1 FROM favorites WHERE note_id = ?",
        ).use { ps ->
            ps.setLong(1, noteId)
            ps.executeQuery().use { rs -> return rs.next() }
        }
    }

    override fun favoriteNoteIds(): List<Long> {
        val ids = mutableListOf<Long>()
        db.connection.prepareStatement(
            "SELECT note_id FROM favorites ORDER BY added_at ASC",
        ).use { ps ->
            ps.executeQuery().use { rs -> while (rs.next()) ids.add(rs.getLong("note_id")) }
        }
        return ids
    }
}
