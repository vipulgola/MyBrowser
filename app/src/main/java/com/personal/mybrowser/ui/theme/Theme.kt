package com.personal.mybrowser.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Mint,
    onPrimaryContainer = Ink,
    secondary = Slate,
    onSecondary = Color.White,
    secondaryContainer = Mist,
    onSecondaryContainer = Ink,
    background = Cloud,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Mist,
    onSurfaceVariant = Slate,
    outline = Color(0xFFA4BDB6),
)

private val DarkColorScheme = darkColorScheme(
    primary = BrightMint,
    onPrimary = Night,
    primaryContainer = Color(0xFF174C43),
    onPrimaryContainer = Color(0xFFD5F7EA),
    secondary = Color(0xFFADC9C2),
    onSecondary = Night,
    secondaryContainer = NightRaised,
    onSecondaryContainer = Color(0xFFE3F1EB),
    background = Night,
    onBackground = Color(0xFFE3F1EB),
    surface = NightSurface,
    onSurface = Color(0xFFE3F1EB),
    surfaceVariant = NightRaised,
    onSurfaceVariant = Color(0xFFBDD3CB),
    outline = NightOutline,
)

@Composable
fun MyBrowserTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
