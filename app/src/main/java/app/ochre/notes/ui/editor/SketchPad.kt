package app.ochre.notes.ui.editor

import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.ochre.notes.ui.theme.Ink
import app.ochre.notes.ui.theme.Ochre
import app.ochre.notes.ui.theme.Paper
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class SketchStroke(
    val points: List<Offset>,
    val color: Color,
    val widthFraction: Float,
    val eraser: Boolean,
)

private val sketchColors = listOf(
    Ink,
    Ochre,
    Color(0xFFB3261E),
    Color(0xFF5B6E43),
    Color(0xFF3E5A7A),
)

private val sketchWidths = listOf(0.004f, 0.009f, 0.016f)

@Composable
fun SketchPad(
    onDismiss: () -> Unit,
    onSave: (File) -> Unit,
    onMessage: (String) -> Unit,
) {
    val strokes = remember { mutableStateListOf<SketchStroke>() }
    var current by remember { mutableStateOf<SketchStroke?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var swatch by remember { mutableStateOf(sketchColors.first()) }
    var widthFraction by remember { mutableFloatStateOf(sketchWidths[1]) }
    var eraser by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun exportSketch(): File {
        val width = 1600
        val ratio = if (canvasSize.width > 0) canvasSize.height.toFloat() / canvasSize.width else 4f / 3f
        val height = (width * ratio).roundToInt().coerceIn(1, 4000)
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(Paper.toArgb())
        strokes.forEach { stroke ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke.widthFraction * width
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = if (stroke.eraser) Paper.toArgb() else stroke.color.toArgb()
            }
            val mapped = stroke.points.map { Offset(it.x * width, it.y * height) }
            if (mapped.size == 1) {
                canvas.drawCircle(mapped.first().x, mapped.first().y, paint.strokeWidth / 2f, paint)
            } else {
                val path = Path().apply {
                    moveTo(mapped.first().x, mapped.first().y)
                    mapped.drop(1).forEach { lineTo(it.x, it.y) }
                }
                canvas.drawPath(path, paint)
            }
        }
        val file = File.createTempFile("sketch-", ".png")
        FileOutputStream(file).use { out -> bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out) }
        bitmap.recycle()
        return file
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close sketch")
                    }
                    Text(
                        "Sketch",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = {
                            if (saving) return@Button
                            saving = true
                            scope.launch {
                                runCatching { withContext(Dispatchers.IO) { exportSketch() } }
                                    .onSuccess { onSave(it) }
                                    .onFailure {
                                        saving = false
                                        onMessage(it.message ?: "Could not save sketch")
                                    }
                            }
                        },
                        enabled = strokes.isNotEmpty() && !saving,
                        modifier = Modifier.padding(start = 4.dp),
                    ) { Text("Save") }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(18.dp),
                            )
                            .clip(RoundedCornerShape(18.dp))
                            .background(Paper)
                            .onSizeChanged { canvasSize = it }
                            .pointerInput(Unit) {
                                detectTapGestures { position ->
                                    val width = size.width.toFloat()
                                    val height = size.height.toFloat()
                                    if (width <= 0f || height <= 0f) return@detectTapGestures
                                    strokes.add(
                                        SketchStroke(
                                            points = listOf(Offset(position.x / width, position.y / height)),
                                            color = swatch,
                                            widthFraction = widthFraction,
                                            eraser = eraser,
                                        ),
                                    )
                                }
                            }
                            .pointerInput(swatch, widthFraction, eraser) {
                                detectDragGestures(
                                    onDragStart = { position ->
                                        val width = size.width.toFloat()
                                        val height = size.height.toFloat()
                                        if (width <= 0f || height <= 0f) return@detectDragGestures
                                        current = SketchStroke(
                                            points = listOf(Offset(position.x / width, position.y / height)),
                                            color = swatch,
                                            widthFraction = widthFraction,
                                            eraser = eraser,
                                        )
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val width = size.width.toFloat()
                                        val height = size.height.toFloat()
                                        current = current?.let { stroke ->
                                            stroke.copy(
                                                points = stroke.points + Offset(
                                                    change.position.x / width,
                                                    change.position.y / height,
                                                ),
                                            )
                                        }
                                    },
                                    onDragEnd = {
                                        current?.let { strokes.add(it) }
                                        current = null
                                    },
                                    onDragCancel = { current = null },
                                )
                            },
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            val all = strokes.toList() + listOfNotNull(current)
                            all.forEach { stroke ->
                                val color = if (stroke.eraser) Paper else stroke.color
                                val strokeWidth = stroke.widthFraction * size.width
                                if (stroke.points.size == 1) {
                                    drawCircle(
                                        color = color,
                                        radius = strokeWidth / 2f,
                                        center = Offset(stroke.points.first().x * size.width, stroke.points.first().y * size.height),
                                    )
                                } else {
                                    val path = ComposePath().apply {
                                        moveTo(stroke.points.first().x * size.width, stroke.points.first().y * size.height)
                                        stroke.points.drop(1).forEach { point ->
                                            lineTo(point.x * size.width, point.y * size.height)
                                        }
                                    }
                                    drawPath(
                                        path = path,
                                        color = color,
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
                                    )
                                }
                            }
                        }
                        if (strokes.isEmpty() && current == null) {
                            Text(
                                "Draw with your finger",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Ink.copy(alpha = 0.35f),
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                    }
                }
                SketchTools(
                    swatch = swatch,
                    onSwatch = {
                        swatch = it
                        eraser = false
                    },
                    widthFraction = widthFraction,
                    onWidth = {
                        widthFraction = it
                        eraser = false
                    },
                    eraser = eraser,
                    onEraser = { eraser = !eraser },
                    canUndo = strokes.isNotEmpty(),
                    onUndo = { strokes.removeLastOrNull() },
                    canClear = strokes.isNotEmpty(),
                    onClear = { confirmClear = true },
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear the whole sketch?") },
            text = { Text("Every stroke will be removed. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        strokes.clear()
                        confirmClear = false
                    },
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Keep") }
            },
        )
    }
}

@Composable
private fun SketchTools(
    swatch: Color,
    onSwatch: (Color) -> Unit,
    widthFraction: Float,
    onWidth: (Float) -> Unit,
    eraser: Boolean,
    onEraser: () -> Unit,
    canUndo: Boolean,
    onUndo: () -> Unit,
    canClear: Boolean,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            sketchColors.forEach { color ->
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (!eraser && color == swatch) 3.dp else 1.dp,
                            color = if (!eraser && color == swatch) MaterialTheme.colorScheme.primary else Paper.copy(alpha = 0.5f),
                            shape = CircleShape,
                        )
                        .clickable { onSwatch(color) },
                )
            }
            Spacer(Modifier.weight(1f))
            Surface(
                onClick = onEraser,
                shape = RoundedCornerShape(12.dp),
                color = if (eraser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    "Eraser",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (eraser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Size",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            sketchWidths.forEach { width ->
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(if (width == widthFraction && !eraser) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
                        .clickable { onWidth(width) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size((8 * width / sketchWidths.first()).dp.coerceAtMost(22.dp))
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onUndo, enabled = canUndo) { Text("Undo") }
            TextButton(onClick = onClear, enabled = canClear) { Text("Clear") }
        }
    }
}
