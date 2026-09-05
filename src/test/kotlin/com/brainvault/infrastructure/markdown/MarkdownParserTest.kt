package com.brainvault.infrastructure.markdown

import com.brainvault.domain.model.LinkKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MarkdownParserTest {

    private val parser = MarkdownParser()

    @Test
    fun `extracts wiki link with raw target`() {
        val links = parser.extractLinks("See [[Some Note]] here.")
        assertEquals(1, links.size)
        assertEquals(LinkKind.WIKI, links[0].kind)
        assertEquals("Some Note", links[0].rawTarget)
        assertEquals("", links[0].sourcePath)
        assertEquals(null, links[0].targetPath)
    }

    @Test
    fun `extracts markdown link to md target`() {
        val links = parser.extractLinks("See [text](sub/other.md) to open.")
        assertEquals(1, links.size)
        assertEquals(LinkKind.MARKDOWN, links[0].kind)
        assertEquals("sub/other.md", links[0].rawTarget)
    }

    @Test
    fun `skips external, mailto and non-md targets`() {
        val links = parser.extractLinks(
            "[a](http://x.com) [b](https://y.com) [c](mailto:a@b) [d](img.png) [e](page.html)",
        )
        assertTrue(links.isEmpty(), "expected no stored links, got $links")
    }

    @Test
    fun `finds both wiki and md links together`() {
        val links = parser.extractLinks("[[A]] and [b](c.md)")
        assertEquals(2, links.size)
        assertEquals(LinkKind.WIKI, links[0].kind)
        assertEquals(LinkKind.MARKDOWN, links[1].kind)
    }

    @Test
    fun `renders self-contained html with inline style and bold`() {
        val html = parser.renderHtml("**bold**")
        assertTrue(html.contains("<strong>bold</strong>"), "got: $html")
        assertTrue(html.contains("<style>"))
        assertTrue(html.contains("body {"))
        assertTrue(html.contains("</html>"))
    }
}
