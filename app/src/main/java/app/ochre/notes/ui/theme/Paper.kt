package app.ochre.notes.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun Modifier.ochrePaper(): Modifier {
    val dark = MaterialTheme.colorScheme.background.isDark()
    val gradient = if (dark) {
        listOf(Color(0xFF1B1610), Color(0xFF14110B), Color(0xFF0C0A06))
    } else {
        listOf(Color(0xFFFDFAF4), Color(0xFFF8F1E5), Color(0xFFEFE1CC))
    }
    val topGlow = if (dark) Color(0xFFB4763F).copy(alpha = 0.16f) else Color(0xFFE8B87B).copy(alpha = 0.28f)
    val bottomGlow = if (dark) Color(0xFF8A5A2C).copy(alpha = 0.10f) else Color(0xFFD8AE83).copy(alpha = 0.20f)
    val shade = if (dark) Color(0xFF000000).copy(alpha = 0.22f) else Color(0xFF7A5630).copy(alpha = 0.06f)

    return drawWithCache {
        val base = Brush.verticalGradient(gradient)
        val warmTop = Brush.radialGradient(
            colors = listOf(topGlow, Color.Transparent),
            center = Offset(size.width * 0.94f, size.height * 0.02f),
            radius = size.maxDimension * 0.72f,
        )
        val warmBottom = Brush.radialGradient(
            colors = listOf(bottomGlow, Color.Transparent),
            center = Offset(size.width * 0.04f, size.height * 0.96f),
            radius = size.maxDimension * 0.68f,
        )
        val vignette = Brush.radialGradient(
            colors = listOf(Color.Transparent, shade),
            center = Offset(size.width * 0.5f, size.height * 0.4f),
            radius = size.maxDimension * 1.05f,
        )
        onDrawBehind {
            drawRect(base)
            drawRect(warmTop)
            drawRect(warmBottom)
            drawRect(vignette)
        }
    }
}
