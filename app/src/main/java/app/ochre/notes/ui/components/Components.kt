package app.ochre.notes.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ochre.notes.data.local.AttachmentKind
import app.ochre.notes.domain.FeedFilter
import app.ochre.notes.domain.MarkdownUtils
import app.ochre.notes.domain.MemoUi
import app.ochre.notes.ui.theme.tagColor
import app.ochre.notes.ui.theme.tagContainerColor
import app.ochre.notes.ui.util.Formatting
import coil.compose.AsyncImage
import java.io.File

@Composable
fun SoftIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(container)
            .combinedClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun TagChip(
    name: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    dark: Boolean = MaterialTheme.colorScheme.background.luminanceIsDark(),
) {
    val background by animateColorAsState(
        targetValue = if (selected) tagColor(name, dark) else tagContainerColor(name, dark),
        label = "tagBackground",
    )
    val content = if (selected) Color.White else tagColor(name, dark)
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.combinedClickable(onClick = onClick) else Modifier),
        color = background,
        contentColor = content,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = "#$name",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            maxLines = 1,
        )
    }
}

private fun Color.luminanceIsDark(): Boolean = (red * 0.299f + green * 0.587f + blue * 0.114f) < 0.5f

@Composable
fun FilterBar(
    selected: FeedFilter,
    onSelect: (FeedFilter) -> Unit,
    modifier: Modifier = Modifier,
    includeArchived: Boolean = true,
) {
    val filters = buildList {
        add(FeedFilter.ALL to "All")
        add(FeedFilter.PINNED to "Pinned")
        add(FeedFilter.MEDIA to "Media")
        add(FeedFilter.REMINDERS to "Reminders")
        if (includeArchived) add(FeedFilter.ARCHIVED to "Archived")
    }
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        filters.forEach { (filter, label) ->
            val isSelected = selected == filter
            val bringIntoView = remember(filter) { BringIntoViewRequester() }
            LaunchedEffect(isSelected) {
                if (isSelected) bringIntoView.bringIntoView()
            }
            val background by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                label = "filterBackground",
            )
            val content by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "filterContent",
            )
            Surface(
                onClick = { onSelect(filter) },
                color = background,
                contentColor = content,
                shape = RoundedCornerShape(50),
                border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.bringIntoViewRequester(bringIntoView),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    showSpark: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showSpark) {
            SparkMark(size = 34.dp)
            Spacer(Modifier.height(20.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(22.dp))
            Surface(
                onClick = onAction,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 13.dp),
                )
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        trailing?.invoke()
    }
}

@Composable
fun WaveformBars(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.tertiary,
    playedFraction: Float = 0f,
) {
    Canvas(modifier) {
        val bars = if (values.isEmpty()) List(28) { 0.35f + ((it * 37) % 50) / 100f } else values
        val maxBars = minOf(bars.size, 64)
        val width = size.width / maxBars
        val barWidth = (width * 0.46f).coerceAtLeast(1.5f)
        val centerY = size.height / 2f
        bars.take(maxBars).forEachIndexed { index, raw ->
            val height = (raw.coerceIn(0.05f, 1f)) * size.height * 0.92f
            val x = index * width + width / 2f
            val active = index.toFloat() / maxBars <= playedFraction
            drawLine(
                color = if (active) color else color.copy(alpha = 0.32f),
                start = Offset(x, centerY - height / 2f),
                end = Offset(x, centerY + height / 2f),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
fun MemoCard(
    memo: MemoUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    highlight: List<String> = emptyList(),
    locked: Boolean = false,
    showTime: Boolean = true,
    selected: Boolean = false,
) {
    val now = System.currentTimeMillis()
    val images = memo.attachments.filter { it.kind == AttachmentKind.IMAGE }
    val audio = memo.attachments.firstOrNull { it.kind == AttachmentKind.AUDIO }
    val sealed = memo.isSealed(now)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier.combinedClickable(onClick = onClick)
                },
            ),
        color = if (selected) {
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        ),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    if (locked) {
                        Text(
                            text = "Sealed capsule",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = displayTitle(memo),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (memo.isPinned) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Rounded.PushPin,
                        contentDescription = "Pinned",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .padding(top = 3.dp)
                            .size(15.dp),
                    )
                }
                if (sealed && !locked) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Rounded.Lock,
                        contentDescription = "Sealed",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(top = 3.dp)
                            .size(15.dp),
                    )
                }
            }

            if (locked) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Opens ${memo.sealedUntil?.let { Formatting.countdown(it, now) } ?: "later"} · ${Formatting.dateTime(memo.sealedUntil ?: now)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val preview = previewOf(memo)
                if (preview.isNotBlank()) {
                    Spacer(Modifier.height(7.dp))
                    Text(
                        text = if (highlight.isEmpty()) AnnotatedString(preview) else highlightPreview(preview, highlight),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (images.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        images.take(3).forEach { image ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(if (images.size == 1) 168.dp else 116.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            ) {
                                AsyncImage(
                                    model = image.thumbnail ?: image.file,
                                    contentDescription = image.originalName ?: "Photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        if (images.size > 3) {
                            Box(
                                Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("+${images.size - 3}", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }

                if (audio != null) {
                    Spacer(Modifier.height(12.dp))
                    AudioBar(
                        file = audio.file,
                        waveform = audio.waveform,
                        durationHint = audio.durationMs,
                    )
                }
            }

            if (memo.tags.isNotEmpty() || memo.activeReminder != null || (showTime)) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showTime) {
                        Text(
                            text = if (Formatting.dayOf(memo.createdAt) == Formatting.dayOf(now)) {
                                Formatting.time(memo.createdAt)
                            } else {
                                "${Formatting.shortDate(memo.createdAt)} · ${Formatting.time(memo.createdAt)}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        )
                    }
                    if (memo.activeReminder != null) {
                        Spacer(Modifier.width(10.dp))
                        Icon(
                            Icons.Rounded.Notifications,
                            contentDescription = "Reminder",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = Formatting.reminderTime(memo.activeReminder!!.fireAt, now),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    if (memo.hasImages || memo.hasAudio) {
                        Spacer(Modifier.width(10.dp))
                        Icon(
                            if (memo.hasImages) Icons.Rounded.Image else Icons.Rounded.Mic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(13.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
                if (memo.tags.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        memo.tags.take(4).forEach { tag -> TagChip(tag.display) }
                        if (memo.tags.size > 4) {
                            Text(
                                "+${memo.tags.size - 4}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 5.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun displayTitle(memo: MemoUi): String {
    if (memo.title.isNotBlank() && memo.title != "Untitled") return memo.title
    return when {
        memo.hasImages -> "Photo note"
        memo.hasAudio -> "Voice note"
        else -> "Untitled"
    }
}

private fun previewOf(memo: MemoUi): String {
    val body = memo.content.lineSequence()
        .dropWhile { it.isBlank() }
        .drop(1)
        .joinToString("\n")
    return MarkdownUtils.plainPreview(if (body.isBlank()) "" else body, maxChars = 200)
}

private fun highlightPreview(text: String, tokens: List<String>): AnnotatedString {
    val cleaned = tokens.filter { it.length > 1 }.distinct()
    if (cleaned.isEmpty()) return AnnotatedString(text)
    val builder = buildAnnotatedString {
        if (cleaned.isEmpty()) return@buildAnnotatedString
        val regex = Regex(cleaned.joinToString("|") { Regex.escape(it) }, RegexOption.IGNORE_CASE)
        var last = 0
        regex.findAll(text).forEach { match ->
            append(text.substring(last, match.range.first))
            pushStyle(SpanStyle(background = Color(0x33B4763F), fontWeight = FontWeight.Medium))
            append(match.value)
            pop()
            last = match.range.last + 1
        }
        append(text.substring(last))
    }
    return builder
}