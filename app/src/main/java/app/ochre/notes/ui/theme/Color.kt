package app.ochre.notes.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs

val Ochre = Color(0xFFB4763F)
val OchreLight = Color(0xFFE0A96F)
val Paper = Color(0xFFFAF5ED)
val Ink = Color(0xFF211C16)

val LightColors = lightColorScheme(
    primary = Color(0xFF26201A),
    onPrimary = Color(0xFFFFF8EF),
    primaryContainer = Color(0xFFEDE1D0),
    onPrimaryContainer = Color(0xFF2E2419),
    secondary = Color(0xFF6C5F52),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDE2D4),
    onSecondaryContainer = Color(0xFF2C241B),
    tertiary = Color(0xFFA45F2C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF7DFC8),
    onTertiaryContainer = Color(0xFF3B1F0A),
    background = Color(0xFFFAF5ED),
    onBackground = Color(0xFF211C16),
    surface = Color(0xFFFFFCF7),
    onSurface = Color(0xFF211C16),
    surfaceVariant = Color(0xFFEEE6DA),
    onSurfaceVariant = Color(0xFF6A6054),
    outline = Color(0xFF8D7F6F),
    outlineVariant = Color(0xFFDCD1C1),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF37302A),
    inverseOnSurface = Color(0xFFF8EFE3),
    inversePrimary = Color(0xFFE7C9A3),
    surfaceDim = Color(0xFFE9E0D4),
    surfaceBright = Color(0xFFFFFCF7),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F2E9),
    surfaceContainer = Color(0xFFF3ECE1),
    surfaceContainerHigh = Color(0xFFEEE6DA),
    surfaceContainerHighest = Color(0xFFE8DFD1),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFFF0E2CE),
    onPrimary = Color(0xFF32271C),
    primaryContainer = Color(0xFF423425),
    onPrimaryContainer = Color(0xFFF5E7D2),
    secondary = Color(0xFFD3C4B2),
    onSecondary = Color(0xFF342C22),
    secondaryContainer = Color(0xFF453B30),
    onSecondaryContainer = Color(0xFFF0E0CD),
    tertiary = Color(0xFFE4A76C),
    onTertiary = Color(0xFF3D220C),
    tertiaryContainer = Color(0xFF5A3A1E),
    onTertiaryContainer = Color(0xFFFFDCC0),
    background = Color(0xFF121009),
    onBackground = Color(0xFFEDE4D6),
    surface = Color(0xFF191510),
    onSurface = Color(0xFFF1E8DA),
    surfaceVariant = Color(0xFF2B241C),
    onSurfaceVariant = Color(0xFFC9BCA9),
    outline = Color(0xFF938574),
    outlineVariant = Color(0xFF443A2F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFF1E8DA),
    inverseOnSurface = Color(0xFF332C24),
    inversePrimary = Color(0xFF65513B),
    surfaceDim = Color(0xFF121009),
    surfaceBright = Color(0xFF3A332A),
    surfaceContainerLowest = Color(0xFF0D0B08),
    surfaceContainerLow = Color(0xFF17130E),
    surfaceContainer = Color(0xFF1D1812),
    surfaceContainerHigh = Color(0xFF251F17),
    surfaceContainerHighest = Color(0xFF2C251C),
)

fun tagColor(name: String, dark: Boolean): Color {
    val hue = (abs(name.lowercase().hashCode()) % 360).toFloat()
    return if (dark) {
        Color.hsl(hue, 0.38f, 0.74f)
    } else {
        Color.hsl(hue, 0.46f, 0.32f)
    }
}

fun tagContainerColor(name: String, dark: Boolean): Color {
    val hue = (abs(name.lowercase().hashCode()) % 360).toFloat()
    return if (dark) {
        Color.hsl(hue, 0.26f, 0.20f)
    } else {
        Color.hsl(hue, 0.42f, 0.92f)
    }
}

fun Color.isDark(): Boolean = luminance() < 0.5f

fun Color.hex(): String = "#%06X".format(toArgb() and 0xFFFFFF)
