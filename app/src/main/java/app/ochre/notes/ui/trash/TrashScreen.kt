package app.ochre.notes.ui.trash

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.ochre.notes.data.NoteRepository
import app.ochre.notes.domain.MarkdownUtils
import app.ochre.notes.domain.MemoUi
import app.ochre.notes.ui.components.EmptyState
import app.ochre.notes.ui.components.SoftIconButton
import app.ochre.notes.ui.ochreViewModel
import app.ochre.notes.ui.util.Formatting
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private class TrashViewModel(private val repository: NoteRepository) : ViewModel() {
    val notes = repository.observeTrash()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val messages = Channel<String>(Channel.BUFFERED)
    val message = messages.receiveAsFlow()

    fun restore(uid: String) = viewModelScope.launch {
        repository.restore(uid)
        messages.trySend("Note restored")
    }

    fun deleteForever(uid: String) = viewModelScope.launch {
        repository.deleteForever(uid)
        messages.trySend("Deleted forever")
    }

    fun emptyTrash() = viewModelScope.launch {
        repository.emptyTrash()
        messages.trySend("Trash emptied")
    }
}

@Composable
fun TrashScreen(
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    showMessage: (String) -> Unit,
) {
    val viewModel = ochreViewModel { container -> TrashViewModel(container.noteRepository) }
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    var confirmEmpty by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<MemoUi?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.message.collect(showMessage)
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SoftIconButton(
                Icons.AutoMirrored.Rounded.ArrowBack,
                "Back",
                onBack,
                container = MaterialTheme.colorScheme.surfaceContainerLow,
            )
            Spacer(Modifier.width(12.dp))
            Text("Trash", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            if (notes.isNotEmpty()) {
                TextButton(onClick = { confirmEmpty = true }) {
                    Text("Empty", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Kept for 30 days.",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Deleted notes wait here before they are gone for good.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
        }
        if (notes.isEmpty()) {
            EmptyState(
                title = "Trash is empty",
                body = "Nothing to recover. Notes you delete will rest here for thirty days.",
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(notes, key = { it.uid }) { memo ->
                    TrashCard(
                        memo = memo,
                        onOpen = { onOpenNote(memo.uid) },
                        onRestore = { viewModel.restore(memo.uid) },
                        onDelete = { deleteTarget = memo },
                    )
                }
            }
        }
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Empty trash?") },
            text = { Text("Every note and attachment in the trash will be deleted permanently.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmEmpty = false
                        viewModel.emptyTrash()
                    },
                ) { Text("Delete all", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Cancel") } },
        )
    }

    deleteTarget?.let { memo ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete forever?") },
            text = { Text("“${memo.title.ifBlank { "Untitled" }}” cannot be recovered.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        viewModel.deleteForever(memo.uid)
                    },
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TrashCard(
    memo: MemoUi,
    onOpen: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        onClick = onOpen,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                memo.title.ifBlank { "Untitled" },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val preview = MarkdownUtils.plainPreview(memo.content, 140)
            if (preview.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    memo.deletedAt?.let { "Deleted ${Formatting.relativeAge(it)}" } ?: "Deleted",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onRestore) { Text("Restore") }
                TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}