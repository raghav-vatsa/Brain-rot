package com.brainrot.detector.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BrainPink = Color(0xFFF7A0C4)
private val BrainDark = Color(0xFF3C203E)
private val TearBlue = Color(0xFF6DC5FF)
private val CardNight = Color(0xFF16122B)

private val DarkColors = darkColorScheme(
    primary = BrainPink,
    onPrimary = BrainDark,
    secondary = TearBlue,
    onSecondary = BrainDark,
    background = Color(0xFF100D1C),
    surface = CardNight,
    onSurface = Color(0xFFF4EEFF),
    surfaceVariant = Color(0xFF241E3D),
    onSurfaceVariant = Color(0xFFB9AFD6),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFB3407A),
    onPrimary = Color.White,
    secondary = Color(0xFF2C7FB8),
    background = Color(0xFFFDF7FB),
    surface = Color.White,
    onSurface = Color(0xFF20141F),
    surfaceVariant = Color(0xFFF4E4EE),
    onSurfaceVariant = Color(0xFF5C4657),
)

@Composable
fun BrainRotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
