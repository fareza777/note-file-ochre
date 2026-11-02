package app.ochre.notes.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ochre.notes.domain.NotebookUi

data class AccentOption(val name: String, val color: Color)

val AccentPalette = listOf(
    AccentOption("Ochre", Color(0xFFB4763F)),
    AccentOption("Clay", Color(0xFFC25A4A)),
    AccentOption("Amber", Color(0xFFD08C2E)),
    AccentOption("Sage", Color(0xFF5E8C61)),
    AccentOption("Teal", Color(0xFF3F7E8C)),
    AccentOption("Indigo", Color(0xFF6C6BA8)),
    AccentOption("Plum", Color(0xFFA65D8C)),
    AccentOption("Graphite", Color(0xFF6B6B6B)),
)

val DefaultNotebookColor: Int = AccentPalette.first().color.toArgb()

fun notebookColor(argb: Int?): Color = argb?.let { Color(it) } ?: AccentPalette.first().color

@Composable
fun NotebookDot(color: Color, size: Dp = 8.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
fun NotebookChip(
    name: String,
    color: Color,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        color = if (selected) color else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(50),
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (!selected) NotebookDot(color, 7.dp)
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) Color.White.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
fun CreateNotebookDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, color: Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(AccentPalette.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New notebook") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Name") },
                    placeholder = { Text("Ideas, Journal, Work…") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    "COLOUR",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AccentPalette.forEach { option ->
                        ColorSwatch(
                            color = option.color,
                            selected = option == selected,
                            onClick = { selected = option },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onCreate(name.trim(), selected.color.toArgb())
                    onDismiss()
                },
                enabled = name.isNotBlank(),
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun NotebookPickerDialog(
    notebooks: List<NotebookUi>,
    selectedUid: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
    onCreate: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to notebook") },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                NotebookPickerRow(
                    label = "No notebook",
                    color = null,
                    selected = selectedUid == null,
                    onClick = { onSelect(null); onDismiss() },
                )
                notebooks.forEach { notebook ->
                    NotebookPickerRow(
                        label = notebook.name,
                        color = Color(notebook.color),
                        selected = selectedUid == notebook.uid,
                        onClick = { onSelect(notebook.uid); onDismiss() },
                    )
                }
                if (notebooks.isEmpty()) {
                    Text(
                        "Notebooks group related notes together.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Row {
                if (onCreate != null) {
                    TextButton(onClick = onCreate) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New notebook")
                    }
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

@Composable
private fun NotebookPickerRow(
    label: String,
    color: Color?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (color != null) {
            NotebookDot(color, 10.dp)
        } else {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun AccentPickerDialog(
    selected: Int?,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Note colour") },
        text = {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(null); onDismiss() }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("No colour", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (selected == null) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    }
                }
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AccentPalette.forEach { option ->
                        val argb = option.color.toArgb()
                        ColorSwatch(
                            color = option.color,
                            selected = selected == argb,
                            onClick = { onSelect(argb); onDismiss() },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    size: Dp = 34.dp,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = "Selected",
                tint = Color.White,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}

@Composable
fun NotebookManagerSheet(
    notebooks: List<NotebookUi>,
    onCreate: (name: String, color: Int) -> Unit,
    onRename: (uid: String, name: String) -> Unit,
    onDelete: (uid: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var showCreate by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<NotebookUi?>(null) }
    var deleteTarget by remember { mutableStateOf<NotebookUi?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outlineVariant) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(bottom = 26.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "NOTEBOOKS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showCreate = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New")
                }
            }
            Spacer(Modifier.height(4.dp))
            if (notebooks.isEmpty()) {
                Text(
                    "Group related notes into notebooks. Notes stay safe when you rename or remove one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    notebooks.forEach { notebook ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            NotebookDot(Color(notebook.color), 11.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    notebook.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    if (notebook.count == 1) "1 note" else "${notebook.count} notes",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { renameTarget = notebook }) {
                                Icon(
                                    Icons.Outlined.Edit,
                                    contentDescription = "Rename ${notebook.name}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                            IconButton(onClick = { deleteTarget = notebook }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "Delete ${notebook.name}",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateNotebookDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, color -> onCreate(name, color) },
        )
    }

    renameTarget?.let { target ->
        var value by remember(target.uid) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename notebook") },
            text = {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRename(target.uid, value)
                        renameTarget = null
                    },
                    enabled = value.isNotBlank(),
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete “${target.name}”?") },
            text = { Text("The notes inside stay in your library, without a notebook.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(target.uid)
                        deleteTarget = null
                    },
                ) { Text("Delete notebook", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Keep") } },
        )
    }
}
