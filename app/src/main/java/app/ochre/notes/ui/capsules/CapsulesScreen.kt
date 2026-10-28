package app.ochre.notes.ui.capsules

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.ochre.notes.data.NoteRepository
import app.ochre.notes.domain.MemoUi
import app.ochre.notes.ui.components.EmptyState
import app.ochre.notes.ui.components.MemoCard
import app.ochre.notes.ui.components.SoftIconButton
import app.ochre.notes.ui.ochreViewModel
import app.ochre.notes.ui.util.Formatting
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

private class CapsulesViewModel(repository: NoteRepository) : ViewModel() {
    val capsules = repository.observeCapsules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun CapsulesScreen(
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    showMessage: (String) -> Unit,
) {
    val viewModel = ochreViewModel { container -> CapsulesViewModel(container.noteRepository) }
    val capsules by viewModel.capsules.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    val locked = capsules.filter { it.isSealed(now) }
    val opened = capsules.filterNot { it.isSealed(now) }

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
            Text("Capsules", style = MaterialTheme.typography.titleMedium)
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Time, held.",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Seal a note today and it will vanish from your timeline until the day you chose. A letter to your future self.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
        }
        if (capsules.isEmpty()) {
            EmptyState(
                title = "No capsules yet",
                body = "Open any note, tap the menu, and choose “Seal in a capsule”. It will keep your words safe until the right moment.",
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (locked.isNotEmpty()) {
                    item {
                        Text(
                            "SEALED · ${locked.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(locked, key = { "sealed_${it.uid}" }) { memo ->
                        MemoCard(
                            memo = memo,
                            locked = true,
                            onClick = {
                                val until = memo.sealedUntil ?: now
                                showMessage("This capsule opens ${Formatting.countdown(until, now)}.")
                            },
                        )
                    }
                }
                if (opened.isNotEmpty()) {
                    item {
                        Text(
                            "OPENED",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    items(opened, key = { "opened_${it.uid}" }) { memo ->
                        CapsuleOpenedCard(memo = memo, now = now, onOpen = { onOpenNote(memo.uid) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CapsuleOpenedCard(memo: MemoUi, now: Long, onOpen: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Capsule · opened ${Formatting.relativeAge(memo.sealedUntil ?: memo.updatedAt, now)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
        }
        MemoCard(memo = memo, onClick = onOpen)
    }
}