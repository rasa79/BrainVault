package com.brainvault.infrastructure.persistence

import com.brainvault.testutil.TestVaults
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DatabaseTest {

    @Test
    fun `initSchema is idempotent`() {
        val db = Database(TestVaults.tempDbFile())
        try {
            db.initSchema()
            db.initSchema()
            db.initSchema()
        } finally {
            db.close()
        }
    }

    @Test
    fun `builds schema tables and triggers`() {
        val db = Database(TestVaults.tempDbFile())
        try {
            db.initSchema()
            db.connection.createStatement().use { st ->
                val tables = mutableSetOf<String>()
                st.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type IN ('table','view')",
                ).use { rs -> while (rs.next()) tables.add(rs.getString("name")) }
                listOf("notes", "notes_fts", "tags", "note_tags", "links", "favorites", "schema_meta")
                    .forEach { t -> assertEquals(true, t in tables, "missing table/view $t") }
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun `pragmas are applied on the connection`() {
        val db = Database(TestVaults.tempDbFile())
        try {
            db.connection.createStatement().use { st ->
                st.executeQuery("PRAGMA journal_mode").use { rs ->
                    assertEquals("wal", rs.getString(1).lowercase())
                }
                st.executeQuery("PRAGMA foreign_keys").use { rs ->
                    assertEquals(1, rs.getInt(1))
                }
                st.executeQuery("PRAGMA busy_timeout").use { rs ->
                    assertEquals(5000, rs.getInt(1))
                }
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun `tx commits and rolls back`() {
        val db = Database(TestVaults.tempDbFile())
        try {
            db.initSchema()
            // Commit path.
            val committed = db.tx { conn ->
                conn.createStatement().use { it.execute("INSERT INTO schema_meta(key, value) VALUES ('k1','v1')") }
                true
            }
            assertEquals(true, committed)
            db.connection.createStatement().use { st ->
                st.executeQuery("SELECT value FROM schema_meta WHERE key='k1'").use { rs ->
                    assertEquals(true, rs.next())
                    assertEquals("v1", rs.getString(1))
                }
            }
            // Rollback path.
            try {
                db.tx { conn ->
                    conn.createStatement().use { it.execute("INSERT INTO schema_meta(key, value) VALUES ('k2','v2')") }
                    throw IllegalStateException("boom")
                }
            } catch (e: IllegalStateException) {
                // expected
            }
            db.connection.createStatement().use { st ->
                st.executeQuery("SELECT COUNT(*) FROM schema_meta WHERE key='k2'").use { rs ->
                    rs.next()
                    assertEquals(0, rs.getInt(1))
                }
            }
        } finally {
            db.close()
        }
    }
}
