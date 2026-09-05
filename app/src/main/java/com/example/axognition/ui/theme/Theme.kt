package com.example.axognition.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

val AxognitionShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

val LocalAxognitionDarkTheme = staticCompositionLocalOf { false }

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA9C7FF), primaryContainer = Navy,
    secondary = Color(0xFFBBC7DB), secondaryContainer = Color(0xFF28364B),
    tertiary = Color(0xFF7DE0A9),
    background = Color(0xFF101722), surface = Color(0xFF171F2B),
    surfaceVariant = Color(0xFF293442), onBackground = Color(0xFFE7EEF9),
    onSurface = Color(0xFFE7EEF9), error = Color(0xFFFFB4AB)
)

private val LightColorScheme = lightColorScheme(
    primary = Navy, primaryContainer = NavyLight,
    secondary = Slate, secondaryContainer = Color(0xFFE1E9F5),
    tertiary = Success, tertiaryContainer = SuccessLight,
    background = Mist, surface = Color.White, surfaceVariant = Color(0xFFEAF0F7),
    onPrimary = Color.White, onBackground = Ink, onSurface = Ink,
    error = Alert, errorContainer = AlertLight
)

@Composable
fun AxognitionTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalAxognitionDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AxognitionShapes,
            content = content
        )
    }
}
