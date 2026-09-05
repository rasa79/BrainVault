package com.brainvault.infrastructure.markdown

import com.brainvault.domain.model.NoteMeta
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.nodes.Tag
import org.yaml.snakeyaml.representer.Representer
import org.yaml.snakeyaml.resolver.Resolver
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

/**
 * Splits, parses and serializes the optional YAML front-matter block of a note
 * file. A note file is the YAML block (when present) followed by the Markdown
 * body:
 *
 * ```
 * ---
 * title: Kotlin vs Java
 * tags: [kotlin, java]
 * ---
 *
 * Body text …
 * ```
 */
class FrontMatterCodec {

    private val yamlParser = Yaml(
        SafeConstructor(LoaderOptions()),
        Representer(DumperOptions()),
        DumperOptions(),
        LoaderOptions(),
        StringDatesResolver(),
    )

    private val dumpOptions = DumperOptions().apply {
        defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        isPrettyFlow = true
    }

    private val yamlDumper = Yaml(
        SafeConstructor(LoaderOptions()),
        Representer(dumpOptions),
        dumpOptions,
    )

    private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    /** The four keys FrontMatterCodec owns and serializes first. */
    private val knownKeys = setOf("title", "tags", "created", "modified")

    /**
     * Splits a raw file into (rawYamlMap, body). The body is the markdown after
     * the front-matter block, with the canonical separator blank line removed.
     * A file without a valid leading block — or with malformed YAML — yields
     * `(emptyMap(), rawFile)` and is treated as body-only.
     */
    fun split(rawFile: String): Pair<Map<String, Any?>, String> {
        val bodyOnly = emptyMap<String, Any?>() to rawFile

        val lines = rawFile.split("\n")
        if (lines.isEmpty() || lines[0].trim() != "---") {
            return bodyOnly
        }

        var closeIdx = -1
        for (i in 1 until lines.size) {
            if (lines[i].trim() == "---") {
                closeIdx = i
                break
            }
        }
        if (closeIdx == -1) {
            return bodyOnly
        }

        val yamlText = lines.subList(1, closeIdx).joinToString("\n")

        // Body begins after the closing "---" line plus the separator newline(s).
        val closingStart = lineTick(lines, closeIdx, rawFile)
        var p = closingStart + lines[closeIdx].length
        if (p < rawFile.length && rawFile[p] == '\r') p++
        if (p < rawFile.length && rawFile[p] == '\n') p++
        // Remove the single blank line that serialize() always emits after "---".
        if (p < rawFile.length && rawFile[p] == '\r') p++
        if (p < rawFile.length && rawFile[p] == '\n') p++
        val body = if (p < rawFile.length) rawFile.substring(p) else ""

        val parsed = parseYaml(yamlText)
        if (parsed == null) return bodyOnly
        return parsed to body
    }

    /** Character offset of the start of the given line within [rawFile]. */
    private fun lineTick(lines: List<String>, lineIndex: Int, rawFile: String): Int {
        var pos = 0
        for (i in 0 until lineIndex) {
            pos += lines[i].length + 1 // +1 for the '\n' separator
        }
        if (pos > rawFile.length) pos = rawFile.length
        return pos
    }

    private fun parseYaml(yamlText: String): Map<String, Any?>? {
        if (yamlText.isBlank()) return emptyMap()
        return try {
            val loaded = yamlParser.load<Any?>(yamlText)
            when (loaded) {
                is Map<*, *> -> loaded.entries.associate { (k, v) -> k.toString() to v }
                else -> emptyMap()
            }
        } catch (e: Exception) {
            // Malformed YAML → body-only, never crash.
            println("WARNING: malformed front-matter YAML, treating file as body-only: ${e.message}")
            null
        }
    }

    /**
     * Converts a raw YAML map into a typed [NoteMeta]: known keys become typed
     * fields, every other key is preserved verbatim in [NoteMeta.extras].
     */
    fun toMeta(raw: Map<String, Any?>): NoteMeta {
        val title = raw["title"] as? String

        // ============================================================
        // LEARN[KJV-010] `when` vs Java switch (expression form)
        // Kotlin:
        //   `when (v) { ... }` is an *expression*: it returns a value. Branches
        //   use type tests (`is List<*>`) and the `else` branch is the default.
        //   It is more powerful than Java's switch (no break, matches on types
        //   and arbitrary predicates, and can be exhaustive).
        // Java 25 equivalent:
        //   Java's `switch` gained pattern matching for `case` type checks, but a
        //   value-producing switch expression is newer than the classic if/else.
        // Differences:
        //   - Kotlin `when` needs no `break`/`default` keywords and is an
        //     expression that always returns a value.
        //   - Kotlin exhaustiveness for enums/sealed types needs no default.
        // ============================================================
        val tags: List<String> = when (val v = raw["tags"]) {
            null -> emptyList()
            is List<*> -> v.mapNotNull { it?.toString() }
            else -> listOf(v.toString())
        }

        val created = parseDateTime(raw["created"])
        val modified = parseDateTime(raw["modified"])

        // ============================================================
        // LEARN[KJV-011] Collections operators (map/filterKeys) vs Streams
        // Kotlin:
        //   `filterKeys { it !in knownKeys }` returns a new map with only the
        //   keys the predicate accepts. It is eager (returns a real collection),
        //   not lazily-iterated like Java's Stream.filter(...).
        // Java 25 equivalent:
        //   raw.entrySet().stream().filter(e -> !KNOWN.contains(e.getKey()))
        //       .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        // Differences:
        //   - Kotlin collection operators are extension functions on the
        //     collection itself; Java needs a Stream pipeline + terminal op.
        //   - Kotlin operators are inline & eager by default; Streams are lazy
        //     until a terminal operation.
        // ============================================================
        val extras = raw.filterKeys { it !in knownKeys }

        return NoteMeta(title = title, tags = tags, created = created, modified = modified, extras = extras)
    }

    private fun parseDateTime(v: Any?): LocalDateTime? {
        if (v == null) return null
        if (v is LocalDateTime) return v
        val s = v.toString().trim()
        return try {
            LocalDateTime.parse(s)
        } catch (e: Exception) {
            try {
                LocalDateTime.parse(s, dateFormat)
            } catch (e2: Exception) {
                null
            }
        }
    }

    /**
     * Serializes a [NoteMeta] + Markdown [body] back into a full file string.
     * Known keys are written first in the order title, tags, created, modified,
     * then the extras keys (sorted); dates are written as yyyy-MM-dd'T'HH:mm:ss.
     */
    fun serialize(meta: NoteMeta, body: String): String {
        val map = LinkedHashMap<String, Any?>()
        meta.title?.let { map["title"] = it }
        map["tags"] = meta.tags
        meta.created?.let { map["created"] = it.format(dateFormat) }
        meta.modified?.let { map["modified"] = it.format(dateFormat) }
        for ((k, v) in meta.extras.toSortedMap()) {
            map[k] = v
        }

        val yamlText = if (map.isEmpty()) "" else yamlDumper.dump(map)

        // ============================================================
        // LEARN[KJV-012] String templates vs Java string concatenation
        // Kotlin:
        //   `"---\n$yamlText---\n\n$body"` plugs variables into a string with a
        //   `$` template. For an empty meta, yamlText is "" so the output is
        //   "---\n---\n\n". Braces are only needed for expressions: `"${a + b}"`.
        // Java 25 equivalent:
        //   "\u2014\u2014\u2014\n" + yamlText + "---\n\n" + body (JDK 21+
        //   string templates are preview; classic concatenation is the norm).
        // Differences:
        //   - Kotlin templates are built into the language and readable;
        //     Java concatenation requires manual `+` (or the newer templates).
        // ============================================================
        return "---\n$yamlText---\n\n$body"
    }
}

/**
 * A SnakeYAML [Resolver] that drops the YAML 1.1 timestamp implicit resolver,
 * so unquoted ISO date strings (e.g. `created: 2026-09-05T10:00:00`) are loaded
 * as plain `String` values rather than `java.util.Date`. Front-matter dates are
 * treated as the literal wall-clock text, not as a timezone-bearing instant —
 * consistent with the local-only app model (never shifted by the host zone).
 *
 * The base Resolver installs the timestamp resolver during construction via its
 * `addImplicitResolvers()` → `addImplicitResolver(...)`. Intercepting the latter
 * (and skipping only the TIMESTAMP tag) keeps every other built-in resolver
 * intact without reimplementing them.
 */
private class StringDatesResolver : Resolver() {
    override fun addImplicitResolver(tag: Tag, regexp: Pattern, first: String?) {
        if (tag != Tag.TIMESTAMP) super.addImplicitResolver(tag, regexp, first)
    }

    override fun addImplicitResolver(tag: Tag, regexp: Pattern, first: String?, limit: Int) {
        if (tag != Tag.TIMESTAMP) super.addImplicitResolver(tag, regexp, first, limit)
    }
}
