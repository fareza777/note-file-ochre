package app.ochre.notes.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownUtilsTest {

    @Test
    fun `extracts tags outside code fences`() {
        val parsed = MarkdownUtils.parse(
            """
            # Planning
            Keep #work and #Deep-Work in mind.

            ```kotlin
            val notATag = "#hidden"
            ```
            Inline `#nope` stays code too, but #reading counts.
            """.trimIndent(),
        )
        assertEquals(listOf("work", "deep-work", "reading"), parsed.tags.map { it.norm })
        assertEquals("Planning", parsed.title)
    }

    @Test
    fun `extracts wiki links and normalises them`() {
        val parsed = MarkdownUtils.parse("Talk to [[Morning Pages]] and [[ideas]] before writing.")
        assertEquals(listOf("morning pages", "ideas"), parsed.links.map { it.norm })
        assertEquals(listOf("Morning Pages", "ideas"), parsed.links.map { it.title })
    }

    @Test
    fun `title falls back to untitled for empty content`() {
        assertEquals("Untitled", MarkdownUtils.extractTitle("   \n\n "))
    }

    @Test
    fun `title strips markdown decoration`() {
        assertEquals("A calm title", MarkdownUtils.extractTitle("## **A calm** [[title]]:"))
        assertEquals("Read me", MarkdownUtils.extractTitle("- [ ] Read me."))
    }

    @Test
    fun `preview drops images and fenced code`() {
        val preview = MarkdownUtils.plainPreview(
            "![photo](img.jpg)\n\n# Heading\n\nSome **bold** text.\n\n```\nsecretline\n```",
        )
        assertFalse(preview.contains("photo"))
        assertFalse(preview.contains("secretline"))
        assertTrue(preview.contains("Heading"))
        assertTrue(preview.contains("bold"))
    }

    @Test
    fun `normalise tag handles unicode and spaces`() {
        assertEquals("kerja", MarkdownUtils.normalizeTag("#Kerja"))
        assertEquals("deep-work", MarkdownUtils.normalizeTag("Deep Work"))
        assertEquals("ide-baru", MarkdownUtils.normalizeTag("#Ide Baru"))
    }

    @Test
    fun `word count ignores blank input`() {
        assertEquals(0, MarkdownUtils.wordCount("   "))
        assertEquals(3, MarkdownUtils.wordCount("one two three\n"))
    }

    @Test
    fun `toggles checkbox on the requested line only`() {
        val content = "# Plan\n\n- [ ] First\n- [x] Second\n  * [ ] Third"
        val checked = MarkdownUtils.toggleChecklist(content, 2)
        assertEquals("# Plan\n\n- [x] First\n- [x] Second\n  * [ ] Third", checked)
        assertEquals(content, MarkdownUtils.toggleChecklist(checked, 2))
    }

    @Test
    fun `toggle checklist leaves non checklist lines alone`() {
        val content = "Plain line\n- bullet"
        assertEquals(content, MarkdownUtils.toggleChecklist(content, 0))
        assertEquals(content, MarkdownUtils.toggleChecklist(content, 1))
        assertEquals(content, MarkdownUtils.toggleChecklist(content, 9))
    }

    @Test
    fun `line prefix inserts on an empty line`() {
        val (text, cursor) = MarkdownUtils.toggleLinePrefix("Title\n\n", 7, 7, "- [ ] ")
        assertEquals("Title\n\n- [ ] ", text)
        assertEquals(13..13, cursor)
    }

    @Test
    fun `line prefix toggles existing marker`() {
        val (marked, _) = MarkdownUtils.toggleLinePrefix("Title\n", 6, 6, "- [ ] ")
        assertEquals("Title\n- [ ] ", marked)
        val (cleared, _) = MarkdownUtils.toggleLinePrefix(marked, marked.length, marked.length, "- [ ] ")
        assertEquals("Title\n", cleared)
    }

    @Test
    fun `outline lists headings with levels and offsets`() {
        val content = "# Trip\n\nIntro\n\n## Day **one**\n\n### [[Morning Pages]]\n"
        val outline = MarkdownUtils.outline(content)
        assertEquals(listOf("Trip", "Day one", "Morning Pages"), outline.map { it.title })
        assertEquals(listOf(1, 2, 3), outline.map { it.level })
        assertEquals(content.indexOf("## Day"), outline[1].offset)
        assertEquals(content.indexOf("###"), outline[2].offset)
    }

    @Test
    fun `outline skips headings inside fenced code`() {
        val content = "# Real\n\n```\n# Fake\n```\n\n## Also real"
        val outline = MarkdownUtils.outline(content)
        assertEquals(listOf("Real", "Also real"), outline.map { it.title })
    }

    @Test
    fun `outline is empty when no headings exist`() {
        assertTrue(MarkdownUtils.outline("Just a note\nwith two lines").isEmpty())
        assertTrue(MarkdownUtils.outline("   ").isEmpty())
    }
}