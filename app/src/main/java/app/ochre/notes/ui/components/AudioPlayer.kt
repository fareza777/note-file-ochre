package app.ochre.notes.ui.components

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.ochre.notes.ui.util.Formatting
import java.io.File
import kotlinx.coroutines.delay

@Composable
fun AudioBar(
    file: File?,
    waveform: List<Float>,
    durationHint: Long?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var player by remember(file) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(file) { mutableStateOf(false) }
    var position by remember(file) { mutableStateOf(0L) }
    var total by remember(file) { mutableStateOf(durationHint ?: 0L) }
    var failed by remember(file) { mutableStateOf(false) }

    DisposableEffect(file) {
        onDispose {
            runCatching { player?.release() }
            player = null
        }
    }

    LaunchedEffect(playing) {
        while (playing) {
            position = runCatching { player?.currentPosition?.toLong() ?: 0L }.getOrDefault(0L)
            delay(120)
        }
    }

    fun toggle() {
        if (file == null || failed) return
        val existing = player
        if (existing == null) {
            val media = MediaPlayer()
            val created = runCatching {
                media.setDataSource(file.absolutePath)
                media.prepare()
                media.setOnCompletionListener {
                    playing = false
                    position = 0L
                    runCatching { it.seekTo(0) }
                }
            }.isSuccess
            if (!created) {
                failed = true
                runCatching { media.release() }
                return
            }
            val reported = media.duration.toLong()
            if (reported > 0) total = reported
            player = media
            media.start()
            playing = true
        } else if (existing.isPlaying) {
            existing.pause()
            playing = false
        } else {
            existing.start()
            playing = true
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .clickable { toggle() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (playing) "Pause" else "Play voice note",
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        WaveformBars(
            values = waveform,
            modifier = Modifier
                .weight(1f)
                .height(24.dp),
            playedFraction = if (total > 0) (position.toFloat() / total).coerceIn(0f, 1f) else 0f,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = if (playing || position > 0) Formatting.duration(position) else Formatting.duration(total),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}