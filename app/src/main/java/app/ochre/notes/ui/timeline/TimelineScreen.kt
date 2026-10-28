package app.ochre.notes.ui.timeline

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LockClock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ochre.notes.domain.FeedFilter
import app.ochre.notes.domain.FeedOrder
import app.ochre.notes.domain.MarkdownUtils
import app.ochre.notes.domain.MemoUi
import app.ochre.notes.ui.components.EmptyState
import app.ochre.notes.ui.components.FilterBar
import app.ochre.notes.ui.components.MemoCard
import app.ochre.notes.ui.components.SoftIconButton
import app.ochre.notes.ui.components.SparkMark
import app.ochre.notes.ui.components.TagChip
import app.ochre.notes.ui.ochreViewModel
import app.ochre.notes.ui.util.Formatting
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun TimelineScreen(
    onOpenNote: (String) -> Unit,
    onNewNote: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenCapsules: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenTag: (String) -> Unit,
    showMessage: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val viewModel = ochreViewModel { container ->
        TimelineViewModel(container.noteRepository, container.settingsRepository)
    }
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val filter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val order by viewModel.selectedOrder.collectAsStateWithLifecycle()
    val echo by viewModel.echo.collectAsStateWithLifecycle()
    val activeCount by viewModel.activeCount.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(viewModel) {
        viewModel.message.collect { showMessage(it) }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (lastVisible >= notes.size - 3) viewModel.loadMore()
            }
    }

    var selection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showTagDialog by remember { mutableStateOf(false) }
    val selectedNotes = notes.filter { it.uid in selection }
    val allPinned = selectedNotes.isNotEmpty() && selectedNotes.all { it.isPinned }
    val allArchived = selectedNotes.isNotEmpty() && selectedNotes.all { it.isArchived }
    val onCardClick: (MemoUi) -> Unit = { memo ->
        if (selection.isEmpty()) {
            onOpenNote(memo.uid)
        } else {
            selection = if (memo.uid in selection) selection - memo.uid else selection + memo.uid
        }
    }
    val onCardLongClick: (MemoUi) -> Unit = { memo -> selection = selection + memo.uid }
    BackHandler(enabled = selection.isNotEmpty()) { selection = emptySet() }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = if (selection.isEmpty()) 12.dp else 84.dp,
                bottom = contentPadding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
        item(key = "header") {
            TimelineHeader(
                activeCount = activeCount,
                order = order,
                onSetOrder = viewModel::setOrder,
                onOpenCapsules = onOpenCapsules,
                onOpenTrash = onOpenTrash,
            )
        }
        item(key = "search") {
            SearchPill(onClick = onOpenSearch)
        }
        item(key = "filters") {
            FilterBar(selected = filter, onSelect = viewModel::setFilter)
        }

        if (filter == FeedFilter.ALL && tags.isNotEmpty()) {
            item(key = "tags") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tags.take(12), key = { it.norm }) { tag ->
                        TagChip(
                            name = tag.display,
                            onClick = { onOpenTag(tag.norm) },
                        )
                    }
                }
            }
        }

        if (filter == FeedFilter.ALL && echo.isNotEmpty()) {
            item(key = "echo") {
                EchoTeaser(
                    memo = echo.first(),
                    extra = echo.size - 1,
                    onOpen = { onOpenNote(echo.first().uid) },
                    onKeep = { viewModel.keepEcho(echo.first().uid) },
                    onDismiss = { viewModel.dismissEcho(echo.first().uid) },
                )
            }
        }

        if (notes.isEmpty()) {
            item(key = "empty") {
                val message = when (filter) {
                    FeedFilter.ALL -> "Nothing here yet. Write the first thought that comes." to "Write a note"
                    FeedFilter.PINNED -> "Pinned notes will wait here." to null
                    FeedFilter.MEDIA -> "Photos and voice notes will collect here." to null
                    FeedFilter.REMINDERS -> "Notes with upcoming reminders show up here." to null
                    FeedFilter.ARCHIVED -> "Archived notes rest here." to null
                }
                EmptyState(
                    title = if (filter == FeedFilter.ALL) "A quiet start" else "Nothing here yet",
                    body = message.first,
                    actionLabel = message.second,
                    onAction = if (message.second != null) onNewNote else null,
                )
            }
        } else {
            val pinned = notes.filter { it.isPinned }
            val rest = notes.filterNot { it.isPinned }
            if (pinned.isNotEmpty()) {
                item(key = "pinnedHeader") {
                    Text(
                        text = "PINNED",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(pinned, key = { "p_${it.uid}" }) { memo ->
                    MemoCard(
                        memo = memo,
                        onClick = { onCardClick(memo) },
                        onLongClick = { onCardLongClick(memo) },
                        selected = memo.uid in selection,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            val grouped = rest.groupBy { Formatting.dayOf(it.createdAt) }
            grouped.forEach { (day, dayNotes) ->
                item(key = "day_$day") {
                    Text(
                        text = Formatting.dayHeader(dayNotes.first().createdAt).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(dayNotes, key = { it.uid }) { memo ->
                    MemoCard(
                        memo = memo,
                        onClick = { onCardClick(memo) },
                        onLongClick = { onCardLongClick(memo) },
                        selected = memo.uid in selection,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

        if (selection.isNotEmpty()) {
            SelectionBar(
                count = selection.size,
                allPinned = allPinned,
                allArchived = allArchived,
                onClose = { selection = emptySet() },
                onTag = { showTagDialog = true },
                onPin = {
                    viewModel.setPinned(selection.toList(), !allPinned)
                    selection = emptySet()
                },
                onArchive = {
                    viewModel.setArchived(selection.toList(), !allArchived)
                    selection = emptySet()
                },
                onDelete = {
                    viewModel.trashMany(selection.toList())
                    selection = emptySet()
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        if (showTagDialog) {
            BulkTagDialog(
                count = selection.size,
                onDismiss = { showTagDialog = false },
                onConfirm = { tag ->
                    viewModel.addTag(selection.toList(), tag)
                    showTagDialog = false
                    selection = emptySet()
                },
            )
        }
    }
}

@Composable
private fun BulkTagDialog(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(120)
        focusRequester.requestFocus()
        keyboard?.show()
    }
    val cleaned = value.trim().removePrefix("#")
        .replace(Regex("[^\\p{L}\\p{N}_-]+"), "-")
        .trim('-')
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (count == 1) "Add a tag" else "Add a tag to $count notes") },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    label = { Text("Tag") },
                    placeholder = { Text("travel") },
                    supportingText = {
                        Text(if (cleaned.isBlank()) "Letters, numbers, dashes" else "#$cleaned")
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.focusRequester(focusRequester),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(cleaned) }, enabled = cleaned.isNotBlank()) {
                Text("Add tag")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun TimelineHeader(
    activeCount: Int,
    order: FeedOrder,
    onSetOrder: (FeedOrder) -> Unit,
    onOpenCapsules: () -> Unit,
    onOpenTrash: () -> Unit,
) {
    var sortMenu by remember { mutableStateOf(false) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SparkMark(size = 22.dp)
            Spacer(Modifier.width(9.dp))
            Text(
                text = "OCHRE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Box {
                SoftIconButton(
                    Icons.AutoMirrored.Outlined.Sort,
                    if (order == FeedOrder.NEWEST) "Sort: newest first" else "Sort: oldest first",
                    { sortMenu = true },
                )
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Newest first") },
                        trailingIcon = {
                            if (order == FeedOrder.NEWEST) {
                                Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            }
                        },
                        onClick = {
                            sortMenu = false
                            onSetOrder(FeedOrder.NEWEST)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Oldest first") },
                        trailingIcon = {
                            if (order == FeedOrder.OLDEST) {
                                Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            }
                        },
                        onClick = {
                            sortMenu = false
                            onSetOrder(FeedOrder.OLDEST)
                        },
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            SoftIconButton(Icons.Outlined.LockClock, "Capsules", onOpenCapsules)
            Spacer(Modifier.width(8.dp))
            SoftIconButton(Icons.Outlined.Delete, "Trash", onOpenTrash)
        }
        Spacer(Modifier.height(22.dp))
        Text(
            text = Formatting.greeting(),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (activeCount == 0) {
                "Your thoughts, held with care."
            } else {
                "${Formatting.number(activeCount)} ${if (activeCount == 1) "note" else "notes"} in your library"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchPill(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Search notes, tags, ideas",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EchoTeaser(
    memo: MemoUi,
    extra: Int,
    onOpen: () -> Unit,
    onKeep: () -> Unit,
    onDismiss: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val dark = MaterialTheme.colorScheme.background.luminanceIsDarkTheme()
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = if (dark) 0.35f else 0.55f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SparkMark(size = 16.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "ECHO · ${Formatting.relativeAge(memo.createdAt).uppercase()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Spacer(Modifier.weight(1f))
                SoftIconButton(
                    Icons.Rounded.BookmarkAdded,
                    "Keep this echo",
                    onKeep,
                    container = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                )
                Spacer(Modifier.width(6.dp))
                SoftIconButton(
                    Icons.Rounded.Close,
                    "Dismiss",
                    onDismiss,
                    container = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = memo.title.ifBlank { "Untitled" },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val preview = MarkdownUtils.plainPreview(
                memo.content.lineSequence().dropWhile { it.isBlank() }.drop(1).joinToString("\n"),
                maxChars = 140,
            )
            if (preview.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (extra > 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "+$extra more waiting in Echo",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f),
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.Color.luminanceIsDarkTheme(): Boolean =
    (red * 0.299f + green * 0.587f + blue * 0.114f) < 0.5f

@Composable
private fun SelectionBar(
    count: Int,
    allPinned: Boolean,
    allArchived: Boolean,
    onClose: () -> Unit,
    onTag: () -> Unit,
    onPin: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 10.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = "Clear selection", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = if (count == 1) "1 selected" else "$count selected",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onTag) {
                Icon(
                    Icons.Rounded.Tag,
                    contentDescription = "Add tag",
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            }
            IconButton(onClick = onPin) {
                Icon(
                    Icons.Rounded.PushPin,
                    contentDescription = if (allPinned) "Unpin" else "Pin",
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            }
            IconButton(onClick = onArchive) {
                Icon(
                    if (allArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                    contentDescription = if (allArchived) "Unarchive" else "Archive",
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Move to trash",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}