package app.ochre.notes.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ochre.notes.ui.util.Formatting
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

private val zone: ZoneId get() = ZoneId.systemDefault()

private fun atTime(date: LocalDate, hour: Int, minute: Int): Long =
    ZonedDateTime.of(date, LocalTime.of(hour, minute), zone).toInstant().toEpochMilli()

internal fun pickerDateMillis(date: LocalDate): Long =
    date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

internal fun dateFromPickerMillis(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

private fun startOfToday(): LocalDate = LocalDate.now(zone)

@Composable
fun CapsuleDialog(
    sealedUntil: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long?) -> Unit,
) {
    var customDate by remember { mutableStateOf<Long?>(null) }
    val today = startOfToday()
    val presets = listOf(
        "Tomorrow" to atTime(today.plusDays(1), 8, 0),
        "Next week" to atTime(today.plusDays(7), 8, 0),
        "In a month" to atTime(today.plusMonths(1), 8, 0),
        "In a year" to atTime(today.plusYears(1), 8, 0),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seal in a capsule") },
        text = {
            Column {
                Text(
                    "The note disappears from your timeline and comes back on the day you choose.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                presets.forEach { (label, millis) ->
                    PresetRow(label, Formatting.dateTime(millis)) { onConfirm(millis) }
                }
                PresetRow("Choose a date…", null) { customDate = atTime(today.plusDays(1), 8, 0) }
                if (sealedUntil != null) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        onClick = { onConfirm(null) },
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.LockOpen, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(10.dp))
                            Text("Remove seal", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )

    customDate?.let { seed ->
        DateTimeDialog(
            initialMillis = seed,
            title = "Seal until",
            onDismiss = { customDate = null },
            onConfirm = { millis ->
                customDate = null
                onConfirm(millis)
            },
        )
    }
}

@Composable
fun ReminderPickerDialog(
    initialMillis: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val now = System.currentTimeMillis()
    val today = startOfToday()
    val presets: List<Pair<String, Long>> = listOf(
        "In an hour" to (now + 3_600_000L),
        "Tonight" to (atTime(today, 20, 0).takeIf { it > now } ?: atTime(today.plusDays(1), 20, 0)),
        "Tomorrow morning" to atTime(today.plusDays(1), 9, 0),
        "Next week" to atTime(today.plusDays(7), 9, 0),
    )
    var custom by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remind me") },
        text = {
            Column {
                Text(
                    "A gentle notification will bring this note back to you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                presets.forEach { (label, millis) ->
                    PresetRow(label, Formatting.reminderTime(millis, now)) { onConfirm(millis) }
                }
                PresetRow("Pick date and time…", null) { custom = true }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (custom) {
        DateTimeDialog(
            initialMillis = initialMillis ?: (now + 3_600_000L),
            title = "Remind me at",
            onDismiss = { custom = false },
            onConfirm = { millis ->
                custom = false
                onConfirm(millis)
            },
        )
    }
}

@Composable
private fun PresetRow(label: String, detail: String?, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.AccessTime,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(12.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DateTimeDialog(
    initialMillis: Long,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val initial = Instant.ofEpochMilli(initialMillis).atZone(zone)
    var choosingTime by remember { mutableStateOf(false) }
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = pickerDateMillis(initial.toLocalDate()),
    )
    val timeState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
    )

    if (!choosingTime) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = { choosingTime = true }) { Text("Next") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            DatePicker(state = dateState, showModeToggle = false)
        }
    } else {
        val date = dateFromPickerMillis(dateState.selectedDateMillis ?: pickerDateMillis(initial.toLocalDate()))
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(Formatting.shortDate(atTime(date, 12, 0))) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state = timeState)
                }
            },
            confirmButton = {
                TextButton(onClick = { onConfirm(atTime(date, timeState.hour, timeState.minute)) }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
    }
}