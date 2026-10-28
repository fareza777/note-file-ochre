package app.ochre.notes.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ochre.notes.domain.MarkdownUtils
import app.ochre.notes.ui.theme.MonoFamily

private enum class TableAlign { Start, Center, End }

private fun TableAlign.textAlign(): TextAlign = when (this) {
    TableAlign.Start -> TextAlign.Start
    TableAlign.Center -> TextAlign.Center
    TableAlign.End -> TextAlign.End
}

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock

    data class Paragraph(val text: String) : MdBlock

    data class Quote(val text: String) : MdBlock

    data class Bullet(
        val text: String,
        val marker: String,
        val checked: Boolean?,
        val line: Int = -1,
        val level: Int = 0,
    ) : MdBlock

    data class Code(val text: String) : MdBlock

    data class Table(
        val header: List<String>,
        val aligns: List<TableAlign>,
        val rows: List<List<String>>,
    ) : MdBlock

    data object Divider : MdBlock
}

private val headingRegex = Regex("^\\s{0,3}(#{1,6})\\s+(.*)$")
private val bulletRegex = Regex("^(\\s{0,8})[-*+]\\s+(.*)$")
private val checkboxRegex = Regex("^(\\s{0,8})[-*+]\\s+\\[([ xX])]\\s+(.*)$")
private val orderedRegex = Regex("^(\\s{0,8})(\\d{1,3})[.)]\\s+(.*)$")
private val quoteRegex = Regex("^\\s{0,3}>\\s?(.*)$")
private val dividerRegex = Regex("^\\s{0,3}([-*_])(?:\\s*\\1){2,}\\s*$")
private val tableSeparatorRegex = Regex("^\\s*\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?\\s*$")

private fun parseBlocks(source: String): List<MdBlock> {
    if (source.isBlank()) return emptyList()
    val lines = source.lines()
    val blocks = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()
    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += MdBlock.Paragraph(paragraph.toString().trim())
            paragraph.clear()
        }
    }
    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("```") || trimmed.startsWith("~~~") -> {
                flushParagraph()
                val fence = trimmed.take(3)
                val code = mutableListOf<String>()
                index++
                while (index < lines.size && !lines[index].trimStart().startsWith(fence)) {
                    code += lines[index]
                    index++
                }
                blocks += MdBlock.Code(code.joinToString("\n"))
            }
            line.isBlank() -> flushParagraph()
            dividerRegex.matches(line) -> {
                flushParagraph()
                blocks += MdBlock.Divider
            }
            line.contains('|') && lines.getOrNull(index + 1)?.let { tableSeparatorRegex.matches(it) } == true -> {
                flushParagraph()
                val header = splitTableRow(line)
                val aligns = splitTableRow(lines[index + 1]).map(::alignmentOf)
                val rows = mutableListOf<List<String>>()
                var rowIndex = index + 2
                while (rowIndex < lines.size) {
                    val rowLine = lines[rowIndex]
                    if (rowLine.isBlank() || !rowLine.contains('|')) break
                    val cells = splitTableRow(rowLine)
                    rows += List(header.size) { column -> cells.getOrNull(column).orEmpty() }
                    rowIndex++
                }
                blocks += MdBlock.Table(header, aligns, rows)
                index = rowIndex
                continue
            }
            headingRegex.matches(line) -> {
                flushParagraph()
                val match = headingRegex.matchEntire(line)!!
                blocks += MdBlock.Heading(match.groupValues[1].length, match.groupValues[2].trim())
            }
            quoteRegex.matches(line) -> {
                flushParagraph()
                val quoteLines = mutableListOf<String>()
                while (index < lines.size && quoteRegex.matches(lines[index])) {
                    quoteLines += quoteRegex.matchEntire(lines[index])!!.groupValues[1]
                    index++
                }
                blocks += MdBlock.Quote(quoteLines.joinToString("\n"))
                continue
            }
            checkboxRegex.matches(line) -> {
                flushParagraph()
                val match = checkboxRegex.matchEntire(line)!!
                blocks += MdBlock.Bullet(
                    text = match.groupValues[3],
                    marker = "checkbox",
                    checked = match.groupValues[2] != " ",
                    line = index,
                    level = nestingLevel(match.groupValues[1]),
                )
            }
            bulletRegex.matches(line) -> {
                flushParagraph()
                val match = bulletRegex.matchEntire(line)!!
                blocks += MdBlock.Bullet(
                    text = match.groupValues[2],
                    marker = "•",
                    checked = null,
                    level = nestingLevel(match.groupValues[1]),
                )
            }
            orderedRegex.matches(line) -> {
                flushParagraph()
                val match = orderedRegex.matchEntire(line)!!
                blocks += MdBlock.Bullet(
                    text = match.groupValues[3],
                    marker = "${match.groupValues[2]}.",
                    checked = null,
                    level = nestingLevel(match.groupValues[1]),
                )
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(line.trim())
            }
        }
        index++
    }
    flushParagraph()
    return blocks
}

private fun nestingLevel(indent: String): Int = (indent.length / 2).coerceIn(0, 5)

private fun splitTableRow(line: String): List<String> {
    var text = line.trim()
    if (text.startsWith("|")) text = text.removePrefix("|")
    if (text.endsWith("|") && !text.endsWith("\\|")) text = text.dropLast(1)
    val cells = mutableListOf<String>()
    val cell = StringBuilder()
    var index = 0
    while (index < text.length) {
        val character = text[index]
        if (character == '\\' && index + 1 < text.length && text[index + 1] == '|') {
            cell.append('|')
            index += 2
            continue
        }
        if (character == '|') {
            cells += cell.toString().trim()
            cell.clear()
        } else {
            cell.append(character)
        }
        index++
    }
    cells += cell.toString().trim()
    return cells
}

private fun alignmentOf(spec: String): TableAlign {
    val trimmed = spec.trim()
    return when {
        trimmed.startsWith(":") && trimmed.endsWith(":") -> TableAlign.Center
        trimmed.endsWith(":") -> TableAlign.End
        else -> TableAlign.Start
    }
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    baseStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    onWikiLink: (String) -> Unit = {},
    onTag: (String) -> Unit = {},
    onToggleCheckbox: ((Int) -> Unit)? = null,
    lineOffset: Int = 0,
) {
    val blocks = remember(text) { parseBlocks(text) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineLarge
                        2 -> MaterialTheme.typography.headlineMedium
                        3 -> MaterialTheme.typography.headlineSmall
                        else -> MaterialTheme.typography.titleLarge
                    }
                    Spacer(Modifier.height(if (block.level <= 2) 8.dp else 2.dp))
                    InlineMarkdown(block.text, style.copy(color = MaterialTheme.colorScheme.onSurface), onWikiLink, onTag)
                }
                is MdBlock.Paragraph -> InlineMarkdown(block.text, baseStyle, onWikiLink, onTag)
                is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f), RoundedCornerShape(2.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                    InlineMarkdown(
                        block.text,
                        baseStyle.copy(fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant),
                        onWikiLink,
                        onTag,
                    )
                }
                is MdBlock.Bullet -> {
                    val toggleLine = block.line.takeIf { block.checked != null && onToggleCheckbox != null }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (block.level * 18).dp)
                            .then(
                                if (toggleLine != null) {
                                    Modifier.clickable(
                                        onClickLabel = if (block.checked == true) "Mark as not done" else "Mark as done",
                                    ) { onToggleCheckbox?.invoke(toggleLine + lineOffset) }
                                } else {
                                    Modifier
                                },
                            ),
                        verticalAlignment = Alignment.Top,
                    ) {
                        if (block.checked != null) {
                            CheckBoxMark(checked = block.checked)
                        } else {
                            Text(
                                text = block.marker,
                                style = baseStyle,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.width(22.dp),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        InlineMarkdown(
                            block.text,
                            if (block.checked == true) {
                                baseStyle.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textDecoration = TextDecoration.LineThrough,
                                )
                            } else {
                                baseStyle
                            },
                            onWikiLink,
                            onTag,
                        )
                    }
                }
                is MdBlock.Code -> {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = block.text.ifBlank { " " },
                            style = baseStyle.copy(
                                fontFamily = MonoFamily,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        )
                    }
                }
                is MdBlock.Table -> MarkdownTable(block, onWikiLink, onTag)
                MdBlock.Divider -> Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(1.dp)),
                )
            }
        }
    }
}

@Composable
private fun MarkdownTable(
    table: MdBlock.Table,
    onWikiLink: (String) -> Unit,
    onTag: (String) -> Unit,
) {
    val border = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val horizontalBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val cellStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val tableWidth = maxOf(maxWidth, (table.header.size * 96).dp)
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.65f),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, border),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                Column(Modifier.width(tableWidth)) {
                    TableRow(
                        cells = table.header,
                        aligns = table.aligns,
                        header = true,
                        cellStyle = cellStyle,
                        onWikiLink = onWikiLink,
                        onTag = onTag,
                    )
                    table.rows.forEach { row ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(horizontalBorder),
                        )
                        TableRow(
                            cells = row,
                            aligns = table.aligns,
                            header = false,
                            cellStyle = cellStyle,
                            onWikiLink = onWikiLink,
                            onTag = onTag,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TableRow(
    cells: List<String>,
    aligns: List<TableAlign>,
    header: Boolean,
    cellStyle: TextStyle,
    onWikiLink: (String) -> Unit,
    onTag: (String) -> Unit,
) {
    val verticalBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(
                if (header) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f) else Color.Transparent,
            ),
    ) {
        cells.forEachIndexed { column, cell ->
            if (column > 0) {
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(verticalBorder),
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                InlineMarkdown(
                    text = cell.ifBlank { " " },
                    style = if (header) cellStyle.copy(fontWeight = FontWeight.SemiBold) else cellStyle,
                    onWikiLink = onWikiLink,
                    onTag = onTag,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = aligns.getOrNull(column)?.textAlign() ?: TextAlign.Start,
                )
            }
        }
    }
}

@Composable
private fun CheckBoxMark(checked: Boolean) {
    Box(
        modifier = Modifier
            .padding(top = 3.dp)
            .size(19.dp)
            .background(
                if (checked) MaterialTheme.colorScheme.tertiary else Color.Transparent,
                RoundedCornerShape(5.dp),
            )
            .then(
                Modifier.background(
                    Color.Transparent,
                    RoundedCornerShape(5.dp),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiary,
                modifier = Modifier.size(14.dp),
            )
        } else {
            Box(
                Modifier
                    .size(19.dp)
                    .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(5.dp)),
            )
            Box(
                Modifier
                    .size(17.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp)),
            )
        }
    }
}

private val inlineRegex = Regex(
    "(`[^`\\n]+`|\\*\\*[^*\\n]+\\*\\*|__[^_\\n]+__|~~[^~\\n]+~~|\\*[^*\\n]+\\*|_[^_\\n]+_|\\[\\[[^\\]]+]]|\\[[^\\]]+]\\([^)]*\\)|#[\\p{L}\\p{N}_-]+)",
)

@Composable
private fun InlineMarkdown(
    text: String,
    style: TextStyle,
    onWikiLink: (String) -> Unit,
    onTag: (String) -> Unit,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
) {
    val annotated = remember(text, style, onWikiLink, onTag) {
        buildInline(text, style, onWikiLink, onTag)
    }
    Text(text = annotated, style = style, modifier = modifier, textAlign = textAlign)
}

private fun buildInline(
    text: String,
    style: TextStyle,
    onWikiLink: (String) -> Unit,
    onTag: (String) -> Unit,
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    var last = 0
    inlineRegex.findAll(text).forEach { match ->
        if (match.range.first > last) builder.append(text.substring(last, match.range.first))
        val token = match.value
        val start = builder.length
        when {
            token.startsWith("`") -> {
                builder.pushStyle(
                    SpanStyle(
                        fontFamily = MonoFamily,
                        background = style.color.copy(alpha = 0.08f),
                        fontSize = style.fontSize * 0.9f,
                    ),
                )
                builder.append(token.removeSurrounding("`"))
                builder.pop()
            }
            token.startsWith("**") || token.startsWith("__") -> {
                builder.pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
                builder.append(token.substring(2, token.length - 2))
                builder.pop()
            }
            token.startsWith("~~") -> {
                builder.pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = style.color.copy(alpha = 0.7f)))
                builder.append(token.substring(2, token.length - 2))
                builder.pop()
            }
            token.startsWith("[[") -> {
                val target = token.removeSurrounding("[[", "]]").trim()
                builder.addLink(
                    LinkAnnotation.Clickable(
                        tag = "wiki:$target",
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = Color(0xFFA45F2C),
                                fontWeight = FontWeight.Medium,
                                textDecoration = TextDecoration.Underline,
                            ),
                        ),
                        linkInteractionListener = { onWikiLink(target) },
                    ),
                    start,
                    start + target.length,
                )
                builder.append(target)
            }
            token.startsWith("#") -> {
                val tag = token.removePrefix("#")
                builder.pushStyle(SpanStyle(color = Color(0xFFA45F2C), fontWeight = FontWeight.Medium))
                builder.append(tag)
                builder.pop()
                builder.addLink(
                    LinkAnnotation.Clickable(
                        tag = "tag:$tag",
                        styles = TextLinkStyles(),
                        linkInteractionListener = { onTag(tag) },
                    ),
                    start,
                    builder.length,
                )
            }
            token.startsWith("[") && token.contains("](") -> {
                val label = token.substringAfter('[').substringBefore("]")
                builder.pushStyle(
                    SpanStyle(
                        color = Color(0xFFA45F2C),
                        textDecoration = TextDecoration.Underline,
                    ),
                )
                builder.append(label)
                builder.pop()
            }
            token.startsWith("*") || token.startsWith("_") -> {
                builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                builder.append(token.substring(1, token.length - 1))
                builder.pop()
            }
            else -> builder.append(token)
        }
        last = match.range.last + 1
    }
    if (last < text.length) builder.append(text.substring(last))
    return builder.toAnnotatedString()
}