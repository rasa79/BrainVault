package com.brainvault.infrastructure.markdown

import com.brainvault.domain.model.NoteMeta
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class FrontMatterCodecTest {

    private val codec = FrontMatterCodec()

    @Test
    fun `round trip preserves keys and body`() {
        val raw = "---\n" +
            "title: Kotlin vs Java\n" +
            "tags: [kotlin, java]\n" +
            "created: 2026-09-05T10:00:00\n" +
            "modified: 2026-09-05T12:30:00\n" +
            "rating: 4\n" +
            "project: BrainVault\n" +
            "---\n\n" +
            "# Kotlin vs Java\n\nBody *text*."

        val (yaml, body) = codec.split(raw)
        val meta = codec.toMeta(yaml)

        assertEquals("Kotlin vs Java", meta.title)
        assertEquals(listOf("kotlin", "java"), meta.tags)
        assertEquals(LocalDateTime.of(2026, 9, 5, 10, 0, 0), meta.created)
        assertEquals(LocalDateTime.of(2026, 9, 5, 12, 30, 0), meta.modified)
        assertEquals(4, meta.extras["rating"])
        assertEquals("BrainVault", meta.extras["project"])

        val out = codec.serialize(meta, body)

        // Body survives byte-for-byte.
        val (y2, b2) = codec.split(out)
        assertEquals(body, b2)

        // Re-parsed meta is identical (keys + extras preserved).
        assertEquals(meta, codec.toMeta(y2))
    }

    @Test
    fun `file without a leading block is body-only`() {
        val raw = "# Just body\n\nNo front matter here."
        val (yaml, body) = codec.split(raw)
        assertTrue(yaml.isEmpty())
        assertEquals(raw, body)
    }

    @Test
    fun `malformed YAML falls back to body-only whole file`() {
        val raw = "---\ntitle: [unclosed\n---\n\nBody"
        val (yaml, body) = codec.split(raw)
        assertTrue(yaml.isEmpty())
        assertEquals(raw, body)
    }

    @Test
    fun `unknown keys are preserved on round trip`() {
        val raw = "---\ntitle: T\nrating: 4\nproject: BV\ncustom_nested:\n  a: 1\n  b: 2\n---\n\nBody"
        val (yaml, body) = codec.split(raw)
        val meta = codec.toMeta(yaml)
        val out = codec.serialize(meta, body)
        val (y2, _) = codec.split(out)
        val meta2 = codec.toMeta(y2)

        assertEquals(4, meta2.extras["rating"])
        assertEquals("BV", meta2.extras["project"])
        assertEquals(mapOf("a" to 1, "b" to 2), meta2.extras["custom_nested"])
        assertEquals("T", meta2.title)
    }

    @Test
    fun `scalar tags are coerced to a single-string list`() {
        val raw = "---\ntags: kotlin\n---\n\nBody"
        val (yaml, _) = codec.split(raw)
        val meta = codec.toMeta(yaml)
        assertEquals(listOf("kotlin"), meta.tags)
    }

    @Test
    fun `title is quoted and dates are serialized as full timestamps`() {
        val meta = NoteMeta(
            title = "T",
            tags = listOf("a"),
            created = LocalDateTime.of(2026, 9, 5, 10, 0, 0),
        )
        val out = codec.serialize(meta, "Body")
        assertTrue(out.contains("created") && out.contains("2026-09-05T10:00:00"), "got: $out")
    }
}
