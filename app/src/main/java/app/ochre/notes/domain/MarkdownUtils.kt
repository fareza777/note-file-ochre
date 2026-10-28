package app.ochre.notes.domain

import java.text.Normalizer
import java.util.Locale

data class ParsedTag(val norm: String, val display: String)

data class ParsedLink(val title: String, val norm: String)

data class ParsedContent(
    val title: String,
    val titleNorm: String,
    val tags: List<ParsedTag>,
    val links: List<ParsedLink>,
    val wordCount: Int,
)

data class OutlineEntry(val level: Int, val title: String, val offset: Int)

object MarkdownUtils {

    private val tagRegex = Regex("(?<![\\p{L}\\p{N}_#])#([\\p{L}\\p{N}_-]{1,50})")
    private val wikiLinkRegex = Regex("\\[\\[([^\\[\\]\\n]{1,200})]]")
    private val fencedCodeRegex = Regex("(?s)(```|~~~).*?(```|~~~)")
    private val inlineCodeRegex = Regex("`[^`\\n]*`")
    private val inlineMdRegex = Regex("(\\*\\*|__|~~|\\*|_|`)")
    private val linkTextRegex = Regex("\\[([^\\]]+)]\\([^)]*\\)")
    private val strippedPrefixRegex = Regex("^\\s{0,3}(#{1,6}\\s+|>\\s?|[-*+]\\s+(\\[[ xX]]\\s+)?|\\d{1,3}[.)]\\s+)")
    private val collapseWhitespace = Regex("\\s+")
    private val checklistLineRegex = Regex("^([ \\t]{0,3}[-*+]\\s+\\[)([ xX])(].*)$")
    private val outlineHeadingRegex = Regex("^\\s{0,3}(#{1,6})\\s+(.*)$")

    fun parse(content: String): ParsedContent {
        val title = extractTitle(content)
        val masked = maskCode(content)
        val tags = tagRegex.findAll(masked)
            .map { it.groupValues[1] }
            .map { raw -> ParsedTag(normalizeTag(raw), raw) }
            .distinctBy { it.norm }
            .toList()
        val links = wikiLinkRegex.findAll(masked)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .map { ParsedLink(it, normalizeTitle(it)) }
            .distinctBy { it.norm }
            .toList()
        return ParsedContent(
            title = title,
            titleNorm = normalizeTitle(title),
            tags = tags,
            links = links,
            wordCount = wordCount(content),
        )
    }

    fun extractTitle(content: String): String {
        val line = content.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?: return "Untitled"
        var title = ensureAsciiHyphens(line)
        title = strippedPrefixRegex.replace(title, "")
        title = linkTextRegex.replace(title) { it.groupValues[1] }
        title = title.replace(wikiLinkRegex, "$1")
        title = title.replace(inlineMdRegex, "")
        title = title.trim().trimEnd('.', ':')
        if (title.isBlank()) return "Untitled"
        return if (title.length > 160) title.take(157).trimEnd() + "…" else title
    }

    fun normalizeTitle(title: String): String {
        val stripped = linkTextRegex.replace(title, "$1")
            .replace(wikiLinkRegex, "$1")
            .replace(inlineMdRegex, "")
        return normalizeForMatch(stripped)
    }

    fun normalizeTag(tag: String): String =
        normalizeForMatch(tag.removePrefix("#")).replace(" ", "-")

    private fun normalizeForMatch(value: String): String {
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(collapseWhitespace, " ")
            .trim()
        return if (normalized.length > 200) normalized.take(200) else normalized
    }

    fun maskCode(content: String): String {
        val chars = content.toCharArray()
        fun maskRange(start: Int, endExclusive: Int) {
            for (i in start until endExclusive.coerceAtMost(chars.size)) {
                if (chars[i] != '\n') chars[i] = ' '
            }
        }
        fencedCodeRegex.findAll(content).forEach { maskRange(it.range.first, it.range.last + 1) }
        inlineCodeRegex.findAll(content).forEach { match ->
            val inNewline = content.substring(match.range.first, match.range.last + 1).contains('\n')
            if (!inNewline) maskRange(match.range.first, match.range.last + 1)
        }
        return String(chars)
    }

    fun wordCount(content: String): Int {
        if (content.isBlank()) return 0
        return content.split(collapseWhitespace).count { it.isNotBlank() && it != "```" }
    }

    fun plainPreview(content: String, maxChars: Int = 180): String {
        val withoutImages = content.replace(Regex("!\\[[^\\]]*]\\([^)]*\\)"), "")
        val stripped = withoutImages
            .replace(fencedCodeRegex, " [code] ")
            .replace(linkTextRegex, "$1")
            .replace(wikiLinkRegex, "$1")
            .replace(Regex("^\\s{0,3}(#{1,6}\\s+|>\\s?|[-*+]\\s+(\\[[ xX]]\\s+)?|\\d{1,3}[.)]\\s+)", RegexOption.MULTILINE), "")
            .replace(inlineMdRegex, "")
            .replace(collapseWhitespace, " ")
            .trim()
        return if (stripped.length > maxChars) stripped.take(maxChars).trimEnd() + "…" else stripped
    }

    fun outline(content: String): List<OutlineEntry> {
        if (content.isBlank()) return emptyList()
        val entries = mutableListOf<OutlineEntry>()
        var fence: String? = null
        var offset = 0
        for (line in content.lines()) {
            val trimmed = line.trimStart()
            val marker = when {
                trimmed.startsWith("```") -> "```"
                trimmed.startsWith("~~~") -> "~~~"
                else -> null
            }
            if (marker != null) {
                if (fence == null) fence = marker else if (fence == marker) fence = null
                offset += line.length + 1
                continue
            }
            if (fence == null) {
                val match = outlineHeadingRegex.matchEntire(line)
                if (match != null) {
                    val raw = match.groupValues[2].trim()
                    val title = plainInline(raw)
                    if (title.isNotBlank()) {
                        entries += OutlineEntry(match.groupValues[1].length, title, offset + line.indexOf('#'))
                    }
                }
            }
            offset += line.length + 1
        }
        return entries
    }

    fun plainInline(markdown: String): String = linkTextRegex.replace(markdown) { it.groupValues[1] }
        .replace(wikiLinkRegex, "$1")
        .replace(inlineMdRegex, "")
        .trim()

    fun replaceTag(content: String, oldNorm: String, newDisplay: String): String {
        val masked = maskCode(content)
        val replacements = tagRegex.findAll(masked)
            .filter { normalizeTag(it.groupValues[1]) == oldNorm }
            .toList()
            .asReversed()
        if (replacements.isEmpty()) return content
        val builder = StringBuilder(content)
        val replacement = if (newDisplay.isBlank()) "" else "#" + newDisplay.removePrefix("#")
        replacements.forEach { match ->
            builder.replace(match.range.first, match.range.last + 1, replacement)
        }
        return builder.toString()
    }

    fun insertAtCursor(text: String, selectionStart: Int, selectionEnd: Int, prefix: String, suffix: String = prefix): Pair<String, IntRange> {
        val start = selectionStart.coerceIn(0, text.length)
        val end = selectionEnd.coerceIn(start, text.length)
        val selected = text.substring(start, end)
        val insert = prefix + selected + suffix
        val newText = text.substring(0, start) + insert + text.substring(end)
        val cursor = if (selected.isEmpty()) start + prefix.length else start + insert.length
        return newText to cursor..cursor
    }

    /**
     * Applies a line prefix to every line touched by the selection, toggling it off when all lines already have it.
     */
    fun toggleLinePrefix(text: String, selectionStart: Int, selectionEnd: Int, prefix: String): Pair<String, IntRange> {
        val start = selectionStart.coerceIn(0, text.length)
        val end = selectionEnd.coerceIn(start, text.length)
        val lineStart = text.lastIndexOf('\n', (start - 1).coerceAtLeast(0)).let { if (it < 0 || start == 0) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', end).let { if (it < 0) text.length else it }
        val block = text.substring(lineStart, lineEnd)
        if (block.isBlank()) {
            val newText = text.substring(0, start) + prefix + text.substring(start)
            val cursor = start + prefix.length
            return newText to cursor..cursor
        }
        val lines = block.split('\n')
        val allPrefixed = lines.filter { it.isNotBlank() }.all { it.startsWith(prefix) }
        val newLines = lines.map { line ->
            when {
                line.isBlank() -> line
                allPrefixed -> line.removePrefix(prefix)
                line.startsWith(prefix) -> line
                else -> prefix + line
            }
        }
        val newBlock = newLines.joinToString("\n")
        val newText = text.substring(0, lineStart) + newBlock + text.substring(lineEnd)
        val delta = newBlock.length - block.length
        val newCursorEnd = (end + delta).coerceIn(lineStart, lineStart + newBlock.length)
        return newText to (lineStart + if (allPrefixed) 0 else prefix.length)..newCursorEnd
    }

    fun toggleChecklist(content: String, lineIndex: Int): String {
        val lines = content.lines().toMutableList()
        if (lineIndex !in lines.indices) return content
        val match = checklistLineRegex.matchEntire(lines[lineIndex]) ?: return content
        val marker = if (match.groupValues[2].isBlank()) "x" else " "
        lines[lineIndex] = match.groupValues[1] + marker + match.groupValues[3]
        return lines.joinToString("\n")
    }

    private fun ensureAsciiHyphens(input: String): String =
        input.replace('\u2013', '-').replace('\u2014', '-')
}
