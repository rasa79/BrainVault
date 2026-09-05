package com.brainvault.infrastructure.persistence

import com.brainvault.domain.model.SearchHit
import com.brainvault.domain.port.SearchIndex

/**
 * FTS5-backed [SearchIndex]. The query string is bound directly as the MATCH
 * argument; escaping user input is the responsibility of `SearchService`, which
 * wraps every token in double quotes. `snippet()` supplies `<b>` highlight
 * markers and `bm25()` the rank (lower = better).
 */
class Fts5SearchIndex(private val db: Database) : SearchIndex {

    override fun search(query: String, limit: Int): List<SearchHit> {
        val hits = mutableListOf<SearchHit>()
        db.connection.prepareStatement(SEARCH_SQL).use { ps ->
            ps.setString(1, query)
            ps.setInt(2, limit)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    hits += SearchHit(
                        path = rs.getString("path"),
                        title = rs.getString("title"),
                        snippet = rs.getString("snippet"),
                        rank = rs.getDouble("rank"),
                    )
                }
            }
        }
        return hits
    }

    override fun rebuild() {
        db.connection.createStatement().use { st ->
            st.execute("INSERT INTO notes_fts(notes_fts) VALUES('rebuild')")
        }
    }

    override fun clear() {
        db.connection.createStatement().use { st ->
            st.execute("DELETE FROM notes")
        }
    }

    private companion object {
        // Verbatim from §5.3. snippet(bold column index 1 = body markers '<b>'/'</b>',
        // ellipsis '…', up to 12 tokens).
        val SEARCH_SQL: String = """
            SELECT n.path, n.title,
                   snippet(notes_fts, 1, '<b>', '</b>', '…', 12) AS snippet,
                   bm25(notes_fts) AS rank
            FROM notes_fts
            JOIN notes n ON n.id = notes_fts.rowid
            WHERE notes_fts MATCH ?
            ORDER BY rank
            LIMIT ?
        """.trimIndent()
    }
}
