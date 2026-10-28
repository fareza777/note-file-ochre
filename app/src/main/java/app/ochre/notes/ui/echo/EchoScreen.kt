package app.ochre.notes.ui.echo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.ochre.notes.data.NoteRepository
import app.ochre.notes.data.local.AttachmentKind
import app.ochre.notes.domain.MarkdownUtils
import app.ochre.notes.domain.MemoUi
import app.ochre.notes.ui.components.EmptyState
import app.ochre.notes.ui.components.SoftIconButton
import app.ochre.notes.ui.components.SparkMark
import app.ochre.notes.ui.ochreViewModel
import app.ochre.notes.ui.util.Formatting
import coil.compose.AsyncImage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class EchoState(
    val loading: Boolean = true,
    val picks: List<MemoUi> = emptyList(),
)

private class EchoViewModel(private val repository: NoteRepository) : ViewModel() {

    private val _state = MutableStateFlow(EchoState())
    val state = _state.asStateFlow()

    private val messages = Channel<String>(Channel.BUFFERED)
    val message = messages.receiveAsFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val picks = runCatching { repository.echoPicks(count = 5) }.getOrDefault(emptyList())
            _state.value = EchoState(loading = false, picks = picks)
        }
    }

    fun keep(uid: String) {
        viewModelScope.launch {
            repository.keepEcho(uid)
            messages.trySend("Kept. Echo will bring it back in a while.")
            remove(uid)
        }
    }

    fun dismiss(uid: String) {
        viewModelScope.launch {
            repository.dismissEcho(uid)
            remove(uid)
        }
    }

    private fun remove(uid: String) {
        _state.update { it.copy(picks = it.picks.filterNot { memo -> memo.uid == uid }) }
    }
}

@Composable
fun EchoScreen(
    onOpenNote: (String) -> Unit,
    showMessage: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val viewModel = ochreViewModel { container -> EchoViewModel(container.noteRepository) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.message.collect(showMessage)
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SparkMark(size = 28.dp)
                Spacer(Modifier.width(10.dp))
                Text("ECHO", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "Rediscover\nyour thinking.",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Older notes return at the right interval. Keep what still matters; let the rest fade.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(22.dp))
        }

        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.tertiary, strokeWidth = 2.dp)
            }
        } else if (state.picks.isEmpty()) {
            EmptyState(
                title = "Nothing echoing yet",
                body = "Notes older than two weeks start to resurface here. Keep writing — the quiet ones come back.",
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    bottom = contentPadding.calculateBottomPadding() + 90.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(state.picks, key = { it.uid }) { memo ->
                    EchoCard(
                        memo = memo,
                        onOpen = { onOpenNote(memo.uid) },
                        onKeep = { viewModel.keep(memo.uid) },
                        onDismiss = { viewModel.dismiss(memo.uid) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun EchoCard(
    memo: MemoUi,
    onOpen: () -> Unit,
    onKeep: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val image = memo.attachments.firstOrNull { it.kind == AttachmentKind.IMAGE }
    Surface(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(width = 7.dp, height = 7.dp)
                        .background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(50)),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "FROM ${Formatting.relativeAge(memo.createdAt).uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                if (memo.echoLevel > 0) {
                    Text(
                        "kept ${memo.echoLevel}×",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    Text(
                        memo.title.ifBlank { "Untitled" },
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val preview = MarkdownUtils.plainPreview(
                        memo.content.lineSequence().dropWhile { it.isBlank() }.drop(1).joinToString("\n"),
                        220,
                    )
                    if (preview.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            preview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (image != null) {
                    Spacer(Modifier.width(14.dp))
                    AsyncImage(
                        model = image.thumbnail ?: image.file,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(84.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = onKeep,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(50),
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.BookmarkAdded, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Keep", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    onClick = onDismiss,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = RoundedCornerShape(50),
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Not now", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    Formatting.shortDate(memo.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}