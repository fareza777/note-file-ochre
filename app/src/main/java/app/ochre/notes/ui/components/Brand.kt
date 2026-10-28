package app.ochre.notes.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ochre.notes.ui.theme.Ochre

fun DrawScope.drawSparkMark(color: Color = Ochre, dot: Boolean = true) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w * 0.5f, h * 0.04f)
        cubicTo(w * 0.55f, h * 0.38f, w * 0.62f, h * 0.45f, w * 0.96f, h * 0.5f)
        cubicTo(w * 0.62f, h * 0.55f, w * 0.55f, h * 0.62f, w * 0.5f, h * 0.96f)
        cubicTo(w * 0.45f, h * 0.62f, w * 0.38f, h * 0.55f, w * 0.04f, h * 0.5f)
        cubicTo(w * 0.38f, h * 0.45f, w * 0.45f, h * 0.38f, w * 0.5f, h * 0.04f)
        close()
    }
    drawPath(
        path = path,
        brush = Brush.linearGradient(
            colors = listOf(color.copy(alpha = 0.92f), color),
            start = Offset(0f, 0f),
            end = Offset(w, h),
        ),
    )
    if (dot) {
        drawCircle(
            color = color.copy(alpha = 0.55f),
            radius = w * 0.055f,
            center = Offset(w * 0.85f, h * 0.85f),
        )
    }
}

@Composable
fun SparkMark(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    color: Color = Ochre,
    dot: Boolean = true,
) {
    Canvas(modifier = modifier.size(size).clearAndSetSemantics { }) {
        drawSparkMark(color, dot)
    }
}

@Composable
fun Wordmark(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onBackground,
) {
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        Text(
            text = "Ochre",
            style = MaterialTheme.typography.displaySmall,
            color = color,
        )
    }
}
