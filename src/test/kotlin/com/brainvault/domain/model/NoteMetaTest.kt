package com.brainvault.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class NoteMetaTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 9, 5, 12, 30, 0)

    @Test
    fun `effectiveTitle uses title or falls back`() {
        assertEquals("Fallback", NoteMeta().effectiveTitle("Fallback"))
        assertEquals("T", NoteMeta(title = "T").effectiveTitle("Fallback"))
    }

    @Test
    fun `withModified copies only modified`() {
        val meta = NoteMeta(title = "T", tags = listOf("a"), created = now.minusDays(1))
        val updated = meta.withModified(now)
        assertEquals(now, updated.modified)
        assertEquals("T", updated.title)
        assertEquals(listOf("a"), updated.tags)
        assertEquals(meta.created, updated.created)
    }

    @Test
    fun `mergeInto writes known keys and preserves extras`() {
        val raw = mutableMapOf<String, Any?>("rating" to 4, "project" to "BrainVault")
        val meta = NoteMeta(
            title = "T",
            tags = listOf("k1", "k2"),
            created = now,
            modified = now,
        )
        meta.mergeInto(raw)
        assertEquals("T", raw["title"])
        assertEquals(listOf("k1", "k2"), raw["tags"])
        assertEquals(now, raw["created"])
        assertEquals(now, raw["modified"])
        assertEquals(4, raw["rating"])
        assertEquals("BrainVault", raw["project"])
    }

    @Test
    fun `mergeInto removes known keys whose value is null`() {
        val raw = mutableMapOf<String, Any?>("title" to "old", "created" to now)
        val meta = NoteMeta(tags = listOf("a"))
        meta.mergeInto(raw)
        assertFalse(raw.containsKey("title"))
        assertFalse(raw.containsKey("created"))
        assertTrue(raw.containsKey("tags"))
    }
}
