package com.safetyhat.macc.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorPalette = darkColors(
    primary = Color(0xFF4CAF50),
    primaryVariant = Color(0xFF388E3C),
    secondary = Color(0xFFFFC107)
)

private val LightColorPalette = lightColors(
    primary = Color(0xFF4CAF50),
    primaryVariant = Color(0xFF388E3C),
    secondary = Color(0xFFFFC107),
    background = Color.White,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color.Black,
    onSurface = Color.Black
)

@Composable
fun SafetyHatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) {
        DarkColorPalette
    } else {
        LightColorPalette
    }

    MaterialTheme(
        colors = colors,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
