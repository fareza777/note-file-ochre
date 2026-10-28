package app.ochre.notes.ui.editor

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LockClock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.FormatStrikethrough
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ochre.notes.data.local.AttachmentKind
import app.ochre.notes.domain.MarkdownUtils
import app.ochre.notes.domain.MemoUi
import app.ochre.notes.ui.components.AudioBar
import app.ochre.notes.ui.components.ImageViewerDialog
import app.ochre.notes.ui.components.MarkdownText
import app.ochre.notes.ui.components.SoftIconButton
import app.ochre.notes.ui.components.TagChip
import app.ochre.notes.ui.components.WaveformBars
import app.ochre.notes.ui.ochreViewModel
import app.ochre.notes.ui.theme.MonoFamily
import app.ochre.notes.ui.theme.ochrePaper
import app.ochre.notes.ui.util.Formatting
import coil.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun EditorScreen(
    initialUid: String?,
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenTag: (String) -> Unit,
    showMessage: (String) -> Unit,
) {
    val viewModel = ochreViewModel(key = initialUid ?: "new") { container ->
        EditorViewModel(
            repository = container.noteRepository,
            attachmentStore = container.attachmentStore,
            contentResolver = container.appContext.contentResolver,
            initialUid = initialUid,
            context = container.appContext,
            reminderScheduler = container.reminderScheduler,
            initialContent = if (initialUid == null) container.draftInbox.consume() else null,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recording by viewModel.recording.collectAsStateWithLifecycle()
    val backlinks by viewModel.backlinks.collectAsStateWithLifecycle()
    val outgoing by viewModel.outgoingLinks.collectAsStateWithLifecycle()

    var fieldValue by remember { mutableStateOf(TextFieldValue(state.content)) }
    var seeded by remember { mutableStateOf(initialUid == null) }
    var preview by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showCapsule by remember { mutableStateOf(false) }
    var showReminder by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var imagePreview by remember { mutableStateOf<EditorAttachment?>(null) }
    var showFind by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var matchIndex by remember { mutableIntStateOf(0) }
    var showOutline by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val findFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    val findMatches = remember(fieldValue.text, findQuery) { findRanges(fieldValue.text, findQuery) }
    val currentMatch = findMatches.getOrNull(matchIndex)
    val highlightColor = MaterialTheme.colorScheme.tertiary

    LaunchedEffect(findMatches) {
        if (matchIndex >= findMatches.size) matchIndex = 0
    }
    LaunchedEffect(findQuery) {
        if (showFind && findQuery.isNotBlank() && findMatches.isNotEmpty()) {
            matchIndex = 0
            val first = findMatches.first()
            fieldValue = fieldValue.copy(selection = TextRange(first.first, first.last + 1))
        }
    }

    val closeFind: () -> Unit = {
        showFind = false
        findQuery = ""
        replaceQuery = ""
        matchIndex = 0
        scope.launch {
            delay(80)
            focusRequester.requestFocus()
        }
    }
    val jumpToMatch: (Int) -> Unit = { delta ->
        if (findMatches.isNotEmpty()) {
            val next = ((matchIndex + delta) % findMatches.size + findMatches.size) % findMatches.size
            matchIndex = next
            val range = findMatches[next]
            fieldValue = fieldValue.copy(selection = TextRange(range.first, range.last + 1))
        }
    }
    val replaceCurrentMatch: () -> Unit = {
        val range = findMatches.getOrNull(matchIndex)
        if (range != null) {
            val newText = fieldValue.text.replaceRange(range.first, range.last + 1, replaceQuery)
            val cursor = (range.first + replaceQuery.length).coerceAtMost(newText.length)
            fieldValue = TextFieldValue(newText, TextRange(cursor))
            viewModel.onContentChange(newText)
        }
    }
    val replaceEveryMatch: () -> Unit = {
        if (findMatches.isNotEmpty()) {
            val count = findMatches.size
            val builder = StringBuilder(fieldValue.text)
            findMatches.asReversed().forEach { range ->
                builder.replace(range.first, range.last + 1, replaceQuery)
            }
            val newText = builder.toString()
            fieldValue = TextFieldValue(newText, TextRange(0))
            viewModel.onContentChange(newText)
            showMessage("Replaced $count ${if (count == 1) "match" else "matches"}")
        }
    }

    LaunchedEffect(state.initialized) {
        if (state.initialized && !seeded) {
            fieldValue = TextFieldValue(state.content)
            seeded = true
        }
    }
    LaunchedEffect(state.content, preview) {
        if (preview && state.content != fieldValue.text) {
            fieldValue = TextFieldValue(state.content)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.message.collect { showMessage(it) }
    }
    LaunchedEffect(initialUid) {
        if (initialUid == null) {
            delay(250)
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(10),
    ) { uris -> viewModel.addImages(uris) }
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> viewModel.addFiles(uris) }
    val recordPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.startRecording()
        } else {
            showMessage("Microphone permission is needed for voice notes")
        }
    }
    val requestRecording = {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.startRecording() else recordPermission.launch(android.Manifest.permission.RECORD_AUDIO)
    }
    val notifyPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) showMessage("Notifications are off, so this reminder will stay silent")
    }
    val ensureNotifications = {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) notifyPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val back = {
        keyboard?.hide()
        viewModel.flush { onBack() }
    }
    BackHandler(onBack = back)

    val tags = remember(state.content) { MarkdownUtils.parse(state.content).tags }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .ochrePaper()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        EditorTopBar(
            title = state.title.ifBlank { if (initialUid == null) "New note" else "Untitled" },
            subtitle = when {
                state.error != null -> state.error!!
                state.saving -> "Saving…"
                state.savedAt != null -> "${state.content.length} characters · Saved ${Formatting.time(state.savedAt!!)}"
                else -> "Draft"
            },
            preview = preview,
            isPinned = state.memo?.isPinned == true,
            onBack = back,
            onTogglePreview = {
                preview = !preview
                if (preview) {
                    showFind = false
                } else {
                    scope.launch {
                        delay(60)
                        focusRequester.requestFocus()
                    }
                }
            },
            onMore = { showMenu = true },
        )

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (!state.initialized) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.tertiary, strokeWidth = 2.dp)
                }
            } else if (preview) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    if (state.content.isBlank()) {
                        Text(
                            "Nothing written yet.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        val body = previewBody(state.content)
                        MarkdownText(
                            text = body.text,
                            lineOffset = body.lineOffset,
                            onWikiLink = { title ->
                                val target = outgoing.firstOrNull { it.title.equals(title, ignoreCase = true) }
                                if (target?.targetUid != null) {
                                    viewModel.flush { onOpenNote(target.targetUid) }
                                } else {
                                    viewModel.createLinkedNote(title) { uid -> onOpenNote(uid) }
                                }
                            },
                            onTag = { tag -> viewModel.flush { onOpenTag(MarkdownUtils.normalizeTag(tag)) } },
                            onToggleCheckbox = viewModel::toggleChecklist,
                        )
                    }
                    AttachmentSection(
                        attachments = state.attachments,
                        editable = false,
                        onRemove = {},
                        onOpenImage = { imagePreview = it },
                    )
                    ThreadsSection(
                        backlinks = backlinks,
                        outgoing = outgoing,
                        onOpenNote = { uid -> viewModel.flush { onOpenNote(uid) } },
                        onCreate = { title -> viewModel.createLinkedNote(title) { uid -> onOpenNote(uid) } },
                    )
                    Spacer(Modifier.height(28.dp))
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    if (showFind) {
                        FindReplaceBar(
                            query = findQuery,
                            replacement = replaceQuery,
                            counter = when {
                                findQuery.isBlank() -> ""
                                findMatches.isEmpty() -> "No matches"
                                else -> "${matchIndex + 1}/${findMatches.size}"
                            },
                            counterIsError = findQuery.isNotBlank() && findMatches.isEmpty(),
                            hasMatches = findMatches.isNotEmpty(),
                            onQueryChange = { next ->
                                findQuery = next
                                matchIndex = 0
                            },
                            onReplacementChange = { replaceQuery = it },
                            onPrevious = { jumpToMatch(-1) },
                            onNext = { jumpToMatch(1) },
                            onReplace = replaceCurrentMatch,
                            onReplaceAll = replaceEveryMatch,
                            onClose = closeFind,
                            focusRequester = findFocusRequester,
                        )
                    }
                    val highlightTransformation = if (showFind && findQuery.isNotBlank()) {
                        remember(findQuery, currentMatch, highlightColor) {
                            MatchHighlightTransformation(findQuery, currentMatch, highlightColor)
                        }
                    } else {
                        VisualTransformation.None
                    }
                    BasicTextField(
                        value = fieldValue,
                        onValueChange = { next ->
                            fieldValue = next
                            viewModel.onContentChange(next.text)
                        },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.tertiary),
                        visualTransformation = highlightTransformation,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .focusRequester(focusRequester),
                        decorationBox = { inner ->
                            Box {
                                if (fieldValue.text.isEmpty()) {
                                    Text(
                                        "Start writing…",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                    )
                                }
                                inner()
                            }
                        },
                    )
                    if (state.importing > 0) {
                        Row(
                            Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Adding attachments…", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (tags.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            tags.take(6).forEach { tag ->
                                TagChip(
                                    name = tag.display,
                                    onClick = {
                                        viewModel.flush { onOpenTag(tag.norm) }
                                    },
                                )
                            }
                        }
                    }
                    AttachmentSection(
                        attachments = state.attachments,
                        editable = true,
                        onRemove = viewModel::removeAttachment,
                        onOpenImage = { imagePreview = it },
                    )
                    EditorToolbar(
                        onAction = { action ->
                            when (action) {
                                EditorAction.Photo -> imagePicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                                EditorAction.File -> filePicker.launch(arrayOf("*/*"))
                                EditorAction.Record -> requestRecording()
                                else -> {
                                    val result = applyToolbarAction(action, fieldValue)
                                    fieldValue = result
                                    viewModel.onContentChange(result.text)
                                    focusRequester.requestFocus()
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    DropdownMenu(
        expanded = showMenu,
        onDismissRequest = { showMenu = false },
    ) {
        DropdownMenuItem(
            text = { Text(if (state.memo?.isPinned == true) "Unpin" else "Pin to top") },
            leadingIcon = { Icon(Icons.Rounded.PushPin, null) },
            onClick = {
                showMenu = false
                viewModel.togglePin()
            },
        )
        DropdownMenuItem(
            text = { Text("Archive") },
            leadingIcon = { Icon(Icons.Outlined.Archive, null) },
            onClick = {
                showMenu = false
                viewModel.toggleArchive()
                showMessage("Moved to archive")
            },
        )
        DropdownMenuItem(
            text = { Text(if (state.memo?.activeReminder != null) "Change reminder" else "Remind me…") },
            leadingIcon = { Icon(Icons.Outlined.Notifications, null) },
            onClick = {
                showMenu = false
                showReminder = true
            },
        )
        if (state.memo?.activeReminder != null) {
            DropdownMenuItem(
                text = { Text("Clear reminder") },
                leadingIcon = { Icon(Icons.Outlined.Notifications, null) },
                onClick = {
                    showMenu = false
                    state.memo?.activeReminder?.let { viewModel.clearReminder(it.uid) }
                },
            )
        }
        DropdownMenuItem(
            text = { Text("Seal in a capsule…") },
            leadingIcon = { Icon(Icons.Outlined.LockClock, null) },
            onClick = {
                showMenu = false
                showCapsule = true
            },
        )
        DropdownMenuItem(
            text = { Text("Find & replace") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            onClick = {
                showMenu = false
                val selectedText = fieldValue.text.substring(fieldValue.selection.min, fieldValue.selection.max)
                if (selectedText.isNotBlank() && '\n' !in selectedText) findQuery = selectedText
                showFind = true
                scope.launch {
                    delay(140)
                    findFocusRequester.requestFocus()
                }
            },
        )
        DropdownMenuItem(
            text = { Text("Outline") },
            leadingIcon = { Icon(Icons.Rounded.FormatListBulleted, null) },
            onClick = {
                showMenu = false
                if (MarkdownUtils.outline(state.content).isEmpty()) {
                    showMessage("Add a heading to build an outline")
                } else {
                    showOutline = true
                }
            },
        )
        DropdownMenuItem(
            text = { Text("Duplicate") },
            leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
            onClick = {
                showMenu = false
                keyboard?.hide()
                viewModel.duplicate { uid -> onOpenNote(uid) }
            },
        )
        DropdownMenuItem(
            text = { Text("Copy text") },
            leadingIcon = { Icon(Icons.Rounded.InsertDriveFile, null) },
            onClick = {
                showMenu = false
                clipboard.setText(AnnotatedString(state.content))
                showMessage("Copied to clipboard")
            },
        )
        DropdownMenuItem(
            text = { Text("Share") },
            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Send, null) },
            onClick = {
                showMenu = false
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, state.content)
                }
                context.startActivity(Intent.createChooser(intent, "Share note"))
            },
        )
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        DropdownMenuItem(
            text = { Text("Move to trash", color = MaterialTheme.colorScheme.error) },
            leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
            onClick = {
                showMenu = false
                showDelete = true
            },
        )
    }

    if (showOutline) {
        val outline = remember(state.content) { MarkdownUtils.outline(state.content) }
        ModalBottomSheet(
            onDismissRequest = { showOutline = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outlineVariant) },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 28.dp),
            ) {
                Text(
                    "OUTLINE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    state.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(18.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    outline.forEach { entry ->
                        Surface(
                            onClick = {
                                showOutline = false
                                preview = false
                                val offset = entry.offset.coerceIn(0, state.content.length)
                                fieldValue = TextFieldValue(state.content, TextRange(offset))
                                scope.launch {
                                    delay(260)
                                    focusRequester.requestFocus()
                                }
                            },
                            color = Color.Transparent,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = ((entry.level - 1) * 14).dp,
                                        top = 9.dp,
                                        bottom = 9.dp,
                                        end = 12.dp,
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .background(
                                            if (entry.level <= 2) {
                                                MaterialTheme.colorScheme.tertiary
                                            } else {
                                                MaterialTheme.colorScheme.outlineVariant
                                            },
                                            CircleShape,
                                        ),
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    entry.title,
                                    style = if (entry.level <= 2) {
                                        MaterialTheme.typography.titleMedium
                                    } else {
                                        MaterialTheme.typography.bodyMedium
                                    },
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "H${entry.level}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showReminder) {
        ReminderPickerDialog(
            initialMillis = state.memo?.activeReminder?.fireAt,
            onDismiss = { showReminder = false },
            onConfirm = { millis ->
                showReminder = false
                viewModel.setReminder(millis)
                ensureNotifications()
            },
        )
    }

    if (showCapsule) {
        CapsuleDialog(
            sealedUntil = state.memo?.sealedUntil,
            onDismiss = { showCapsule = false },
            onConfirm = { until ->
                showCapsule = false
                viewModel.setCapsule(until)
            },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Move to trash?") },
            text = { Text("You can restore it from Trash for 30 days.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDelete = false
                        keyboard?.hide()
                        viewModel.trash(onBack)
                    },
                ) { Text("Move to trash", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Keep") } },
        )
    }

    imagePreview?.let { attachment ->
        ImageViewerDialog(
            model = attachment.file ?: attachment.thumbnail,
            title = attachment.originalName,
            shareFile = attachment.file,
            mimeType = attachment.mimeType,
            onDismiss = { imagePreview = null },
        )
    }

    if (recording.active) {
        RecordingSheet(
            elapsedMs = recording.elapsedMs,
            amplitudes = recording.amplitudes,
            onCancel = viewModel::cancelRecording,
            onStop = viewModel::stopRecording,
        )
    }
}

@Composable
private fun RecordingSheet(
    elapsedMs: Long,
    amplitudes: List<Float>,
    onCancel: () -> Unit,
    onStop: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outlineVariant) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .padding(bottom = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "RECORDING",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                Formatting.duration(elapsedMs),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(18.dp))
            WaveformBars(
                values = amplitudes.takeLast(64),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            )
            Spacer(Modifier.height(26.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Discard", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    onClick = onStop,
                    modifier = Modifier.weight(1.4f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    ),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Keep voice note")
                }
            }
        }
    }
}

private data class PreviewBody(val text: String, val lineOffset: Int)

private fun previewBody(content: String): PreviewBody {
    val lines = content.lines()
    val first = lines.indexOfFirst { it.isNotBlank() }
    if (first < 0) return PreviewBody(content, 0)
    val rest = lines.drop(first + 1).joinToString("\n")
    return if (rest.isBlank()) PreviewBody(content, 0) else PreviewBody(rest, first + 1)
}

@Composable
private fun EditorTopBar(
    title: String,
    subtitle: String,
    preview: Boolean,
    isPinned: Boolean,
    onBack: () -> Unit,
    onTogglePreview: () -> Unit,
    onMore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SoftIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack, container = MaterialTheme.colorScheme.surfaceContainerLow)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isPinned) {
            Icon(
                Icons.Rounded.PushPin,
                contentDescription = "Pinned",
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(16.dp),
            )
        }
        SoftIconButton(
            if (preview) Icons.Rounded.Edit else Icons.Rounded.Visibility,
            if (preview) "Edit" else "Preview",
            onTogglePreview,
            container = MaterialTheme.colorScheme.surfaceContainerLow,
        )
        Spacer(Modifier.width(6.dp))
        SoftIconButton(Icons.Rounded.MoreVert, "More", onMore, container = MaterialTheme.colorScheme.surfaceContainerLow)
    }
}

private enum class EditorAction {
    H1, H2, Bold, Italic, Strike, Bullet, Numbered, Checkbox, Quote, Code, Table, Link, Tag, Photo, File, Record,
}

@Composable
private fun EditorToolbar(onAction: (EditorAction) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ToolbarButton(icon = Icons.Outlined.TextFields, label = "H1") { onAction(EditorAction.H1) }
            ToolbarButton(icon = Icons.Outlined.TextFields, label = "H2") { onAction(EditorAction.H2) }
            ToolbarButton(icon = Icons.Rounded.FormatBold, label = "Bold") { onAction(EditorAction.Bold) }
            ToolbarButton(icon = Icons.Rounded.FormatItalic, label = "Italic") { onAction(EditorAction.Italic) }
            ToolbarButton(icon = Icons.Rounded.FormatStrikethrough, label = "Strike") { onAction(EditorAction.Strike) }
            ToolbarButton(icon = Icons.Rounded.FormatListBulleted, label = "List") { onAction(EditorAction.Bullet) }
            ToolbarButton(icon = Icons.Rounded.FormatListNumbered, label = "Numbered") { onAction(EditorAction.Numbered) }
            ToolbarButton(icon = Icons.Rounded.CheckBox, label = "Checkbox") { onAction(EditorAction.Checkbox) }
            ToolbarButton(icon = Icons.Rounded.FormatQuote, label = "Quote") { onAction(EditorAction.Quote) }
            ToolbarButton(icon = Icons.Rounded.Code, label = "Code") { onAction(EditorAction.Code) }
            ToolbarButton(icon = Icons.Rounded.TableChart, label = "Table") { onAction(EditorAction.Table) }
            ToolbarButton(icon = Icons.Rounded.Link, label = "Link") { onAction(EditorAction.Link) }
            ToolbarButton(icon = Icons.Rounded.Tag, label = "Tag") { onAction(EditorAction.Tag) }
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Spacer(Modifier.width(4.dp))
            ToolbarButton(icon = Icons.Rounded.AddPhotoAlternate, label = "Photo") { onAction(EditorAction.Photo) }
            ToolbarButton(icon = Icons.Rounded.Mic, label = "Voice") { onAction(EditorAction.Record) }
            ToolbarButton(icon = Icons.Outlined.AttachFile, label = "File") { onAction(EditorAction.File) }
        }
    }
}

@Composable
private fun ToolbarButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (label == "H1" || label == "H2") {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
        }
    }
}

private fun applyToolbarAction(action: EditorAction, value: TextFieldValue): TextFieldValue {
    val text = value.text
    val start = value.selection.min
    val end = value.selection.max
    val result = when (action) {
        EditorAction.H1 -> MarkdownUtils.toggleLinePrefix(text, start, end, "# ")
        EditorAction.H2 -> MarkdownUtils.toggleLinePrefix(text, start, end, "## ")
        EditorAction.Bold -> MarkdownUtils.insertAtCursor(text, start, end, "**")
        EditorAction.Italic -> MarkdownUtils.insertAtCursor(text, start, end, "*")
        EditorAction.Strike -> MarkdownUtils.insertAtCursor(text, start, end, "~~")
        EditorAction.Bullet -> MarkdownUtils.toggleLinePrefix(text, start, end, "- ")
        EditorAction.Numbered -> MarkdownUtils.toggleLinePrefix(text, start, end, "1. ")
        EditorAction.Checkbox -> MarkdownUtils.toggleLinePrefix(text, start, end, "- [ ] ")
        EditorAction.Quote -> MarkdownUtils.toggleLinePrefix(text, start, end, "> ")
        EditorAction.Code -> MarkdownUtils.insertAtCursor(text, start, end, "```\n", "\n```")
        EditorAction.Table -> {
            val lead = if (start > 0 && text[start - 1] != '\n') "\n\n" else ""
            val table = "| Column 1 | Column 2 |\n| --- | --- |\n|  |  |\n|  |  |\n"
            val insert = lead + table
            val newText = text.substring(0, start) + insert + text.substring(end)
            val headerStart = start + lead.length + 2
            newText to (headerStart..(headerStart + "Column 1".length))
        }
        EditorAction.Link -> MarkdownUtils.insertAtCursor(text, start, end, "[", "](https://)")
        EditorAction.Tag -> MarkdownUtils.insertAtCursor(text, start, end, "#", "")
        else -> text to (start..start)
    }
    return TextFieldValue(result.first, TextRange(result.second.first, result.second.last))
}

@Composable
private fun AttachmentSection(
    attachments: List<EditorAttachment>,
    editable: Boolean,
    onRemove: (String) -> Unit,
    onOpenImage: (EditorAttachment) -> Unit,
) {
    if (attachments.isEmpty()) return
    if (!editable) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            attachments.filter { it.kind == AttachmentKind.IMAGE }.forEach { attachment ->
                AsyncImage(
                    model = attachment.file,
                    contentDescription = attachment.originalName ?: "Photo",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { onOpenImage(attachment) },
                )
            }
            attachments.filter { it.kind != AttachmentKind.IMAGE }.forEach { attachment ->
                AttachmentChip(attachment = attachment)
            }
        }
        return
    }
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(attachments, key = { it.uid }) { attachment ->
            Box {
                if (attachment.kind == AttachmentKind.AUDIO) {
                    AudioBar(
                        file = attachment.file,
                        waveform = attachment.waveform,
                        durationHint = attachment.durationMs,
                        modifier = Modifier
                            .width(236.dp)
                            .height(76.dp),
                    )
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .size(width = 92.dp, height = 76.dp)
                            .then(
                                if (attachment.kind == AttachmentKind.IMAGE) {
                                    Modifier.clickable { onOpenImage(attachment) }
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        if (attachment.kind == AttachmentKind.IMAGE) {
                            AsyncImage(
                                model = attachment.thumbnail ?: attachment.file,
                                contentDescription = attachment.originalName ?: "Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    Icons.Rounded.InsertDriveFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    Formatting.bytes(attachment.sizeBytes),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
                        .clickable { onRemove(attachment.uid) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Remove attachment", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(13.dp))
                }
            }
        }
    }
}

@Composable
private fun AttachmentChip(attachment: EditorAttachment) {
    if (attachment.kind == AttachmentKind.AUDIO) {
        AudioBar(
            file = attachment.file,
            waveform = attachment.waveform,
            durationHint = attachment.durationMs,
        )
        return
    }
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { openAttachment(context, attachment.file, attachment.mimeType) },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                attachment.originalName ?: "Attachment",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(Formatting.bytes(attachment.sizeBytes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun openAttachment(context: android.content.Context, file: File?, mimeType: String) {
    if (file == null || !file.exists()) return
    runCatching {
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Open attachment"))
    }
}

@Composable
private fun FindReplaceBar(
    query: String,
    replacement: String,
    counter: String,
    counterIsError: Boolean,
    hasMatches: Boolean,
    onQueryChange: (String) -> Unit,
    onReplacementChange: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit,
    focusRequester: FocusRequester,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                FindField(
                    value = query,
                    placeholder = "Find in note",
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    focusRequester = focusRequester,
                )
                if (counter.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        counter,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (counterIsError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                FindBarIcon(Icons.Rounded.KeyboardArrowUp, "Previous match", hasMatches, onPrevious)
                FindBarIcon(Icons.Rounded.KeyboardArrowDown, "Next match", hasMatches, onNext)
                FindBarIcon(Icons.Rounded.Close, "Close find", true, onClose)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                FindField(
                    value = replacement,
                    placeholder = "Replace with",
                    onValueChange = onReplacementChange,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onReplace,
                    enabled = hasMatches,
                    contentPadding = PaddingValues(horizontal = 10.dp),
                ) {
                    Text("Replace", style = MaterialTheme.typography.labelLarge)
                }
                TextButton(
                    onClick = onReplaceAll,
                    enabled = hasMatches,
                    contentPadding = PaddingValues(horizontal = 10.dp),
                ) {
                    Text("All", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun FindField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    Box(modifier) {
        if (value.isEmpty()) {
            Text(
                placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                maxLines = 1,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.tertiary),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        )
    }
}

@Composable
private fun FindBarIcon(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            },
            modifier = Modifier.size(20.dp),
        )
    }
}

private fun findRanges(text: String, query: String): List<IntRange> {
    if (query.isEmpty()) return emptyList()
    val ranges = mutableListOf<IntRange>()
    var start = 0
    while (start <= text.length - query.length) {
        val index = text.indexOf(query, start, ignoreCase = true)
        if (index < 0) break
        ranges += index until index + query.length
        start = index + query.length
    }
    return ranges
}

private class MatchHighlightTransformation(
    private val query: String,
    private val current: IntRange?,
    private val color: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (query.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val builder = AnnotatedString.Builder(text)
        findRanges(text.text, query).forEach { range ->
            builder.addStyle(
                SpanStyle(
                    background = color.copy(alpha = if (range == current) 0.32f else 0.12f),
                ),
                range.first,
                range.last + 1,
            )
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

@Composable
private fun ThreadsSection(
    backlinks: List<MemoUi>,
    outgoing: List<app.ochre.notes.domain.OutgoingLinkUi>,
    onOpenNote: (String) -> Unit,
    onCreate: (String) -> Unit,
) {
    if (backlinks.isEmpty() && outgoing.isEmpty()) return
    Column(Modifier.padding(top = 26.dp)) {
        if (outgoing.isNotEmpty()) {
            Text(
                "LINKS OUT",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                outgoing.forEach { link ->
                    Surface(
                        onClick = {
                            if (link.targetUid != null) onOpenNote(link.targetUid) else onCreate(link.title)
                        },
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Text(
                            text = "[[" + link.title + "]]",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
        }
        if (backlinks.isNotEmpty()) {
            Text(
                "MENTIONED IN",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                backlinks.take(8).forEach { memo ->
                    Surface(
                        onClick = { onOpenNote(memo.uid) },
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                memo.title.ifBlank { "Untitled" },
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val preview = MarkdownUtils.plainPreview(memo.content, 100)
                            if (preview.isNotBlank()) {
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    preview,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}