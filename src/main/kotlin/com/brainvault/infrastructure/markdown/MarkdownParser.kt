package com.brainvault.infrastructure.markdown

import com.brainvault.domain.model.Link
import com.brainvault.domain.model.LinkKind
import com.vladsch.flexmark.ast.Link as FlexmarkLink
import com.vladsch.flexmark.ext.autolink.AutolinkExtension
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.ext.wikilink.WikiLink
import com.vladsch.flexmark.ext.wikilink.WikiLinkExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.ast.Node
import com.vladsch.flexmark.util.data.MutableDataSet

/**
 * Renders Markdown to self-contained HTML and extracts internal links from the
 * parsed AST. The flexmark Parser/Renderer are built once and reused.
 */
class MarkdownParser {

    // ============================================================
    // LEARN[KJV-013] init blocks and primary constructors
    // Kotlin:
    //   A class body's `init { }` block runs as part of construction, after the
    //   primary-constructor property initializers. It is the right place to do
    //   one-time setup that does not belong to a property initializer. Here we
    //   assemble the flexmark options and build the parser + renderer exactly
    //   once per instance (they are immutable and thread-safe to reuse).
    // Java 25 equivalent:
    //   A constructor body — but Kotlin classes may have a primary constructor
    //   in the header (there is none here) plus `init` blocks interleaved with
    //   property initializers, so field setup order is explicit and readable.
    // Differences:
    //   - Kotlin separates the constructor signature (header) from `init` setup,
    //     so setup code reads as a block rather than constructor parameters.
    //   - Multiple `init` blocks run in order; Java has a single constructor.
    // ============================================================
    private val options: MutableDataSet = MutableDataSet()
    private val parser: Parser
    private val htmlRenderer: HtmlRenderer

    init {
        options.set(
            Parser.EXTENSIONS,
            listOf(
                TablesExtension.create(),
                StrikethroughExtension.create(),
                AutolinkExtension.create(),
                // Wiki links ([[Some Note]]) are a BrainVault core feature.
                WikiLinkExtension.create(),
            ),
        )
        parser = Parser.builder(options).build()
        htmlRenderer = HtmlRenderer.builder(options).build()
    }

    // ============================================================
    // LEARN[KJV-014] by lazy / delegated properties
    // Kotlin:
    //   `by lazy { ... }` is a *delegated* property: the value is computed on
    //   first access and cached. `by` is the delegation operator; `lazy` is the
    //   stdlib delegating object. (Most Kotlin delegation is custom, e.g.
    //   `by Observable(...)`, but `by lazy` is the most common built-in.)
    // Java 25 equivalent:
    //   private final Foo foo; ...
    //   private Foo foo() { return (foo != null) ? foo : (foo = compute()); }
    //   or a memoizing wrapper like java.util.function.Supplier + synchronized.
    // Differences:
    //   - `by lazy` is thread-safe by default and reads as a property, not a
    //     getter-with-memoization. It defers construction until actually needed.
    // ============================================================
    val styleSheet: String by lazy {
        """
        body { margin: 1.5rem auto; max-width: 720px; line-height: 1.6; font-family: system-ui, sans-serif; }
        code, pre { font-family: "JetBrains Mono", Consolas, monospace; }
        pre { background: #f5f5f5; padding: 0.8rem; border-radius: 4px; overflow-x: auto; }
        blockquote { border-left: 3px solid #ccc; margin-left: 0; padding-left: 1rem; color: #555; }
        table { border-collapse: collapse; }
        th, td { border: 1px solid #ccc; padding: 0.3rem 0.6rem; }
        b { color: #1a6e2e; }
        """.trimIndent()
    }

    /**
     * Renders a Markdown body into a self-contained HTML document (inline
     * stylesheet, no external resources — the offline rule).
     */
    fun renderHtml(markdownBody: String): String {
        val document = parser.parse(markdownBody)
        val rendered = htmlRenderer.render(document)
        return """<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<style>
$styleSheet
</style>
</head>
<body>
$rendered
</body>
</html>"""
    }

    /**
     * Extracts internal links from a Markdown body. Wiki links
     * (`[[Some Note]]`) become [LinkKind.WIKI]; Markdown links to `*.md`
     * targets become [LinkKind.MARKDOWN]. External URLs and non-`.md`
     * targets are skipped. `sourcePath`/`targetPath` are left as `""`/`null`
     * for the caller to fill in.
     */
    fun extractLinks(markdownBody: String): List<Link> {
        val document = parser.parse(markdownBody)
        val links = mutableListOf<Link>()
        accept(document, links)
        return links
    }

    private fun accept(node: Node, out: MutableList<Link>) {
        when (node) {
            is WikiLink -> {
                val raw = pageRef(node)
                if (raw.isNotBlank()) {
                    out += Link("", null, raw, LinkKind.WIKI)
                }
            }
            is FlexmarkLink -> {
                val url = node.getUrl()?.toString()?.trim() ?: ""
                if (StoredLinkPredicate.test(url)) {
                    out += Link("", null, url, LinkKind.MARKDOWN)
                }
            }
            else -> Unit
        }
        // `node.children` maps to getChildren(): the child nodes of the AST node.
        for (child in node.children) {
            accept(child, out)
        }
    }

    private fun pageRef(node: WikiLink): String {
        val ref = node.getPageRef()?.toString()?.trim()
        if (!ref.isNullOrBlank()) return ref
        return node.getText()?.toString()?.trim() ?: ""
    }
}

// ============================================================
// LEARN[KJV-015] object / companion object and top-level declarations
// Kotlin:
//   A top-level `private object StoredLinkPredicate` is a *singleton*
//   (a class with a single instance). `object` is Kotlin's one-liner singleton;
//   members are reached as `StoredLinkPredicate.test(...)` — no `new`, no
//   `static`. Because it is `private` to the file, it is a file-private helper.
// Java 25 equivalent:
//   private static final class StoredLinkPredicate { static boolean test(...) }
//   or a utility class with private constructor + static methods.
// Differences:
//   - `object` gives you a true singleton with no extra boilerplate.
//   - `companion object` inside a class gives the class its static side; a
//     top-level `object` is standalone (like a Java utility class).
// ============================================================
private object StoredLinkPredicate {
    private val prefixes = listOf("http://", "https://", "mailto:")

    fun test(url: String): Boolean {
        val u = url.trim()
        if (prefixes.any { u.startsWith(it) }) return false
        return u.lowercase().endsWith(".md")
    }
}
