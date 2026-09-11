package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = UtilityBlue500,
    onPrimary = Color.White,
    primaryContainer = UtilityDarkBlue,
    onPrimaryContainer = UtilityBlue100,
    secondary = Slate400,
    onSecondary = Slate900,
    secondaryContainer = CleanSurfaceElevatedDark,
    onSecondaryContainer = Slate200,
    tertiary = StatusPosted,
    onTertiary = Color.White,
    background = CleanBgDark,
    onBackground = Color(0xFFF8FAFC),
    surface = CleanSurfaceDark,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = CleanSurfaceElevatedDark,
    onSurfaceVariant = Slate400,
    outline = CleanBorderDark,
    outlineVariant = Slate800
)

private val LightColorScheme = lightColorScheme(
    primary = UtilityBlue600,
    onPrimary = Color.White,
    primaryContainer = UtilityBlue50,
    onPrimaryContainer = UtilityBlue700,
    secondary = Slate700,
    onSecondary = Color.White,
    secondaryContainer = Slate100,
    onSecondaryContainer = Slate900,
    tertiary = StatusPosted,
    onTertiary = Color.White,
    background = CleanBgLight,
    onBackground = Slate900,
    surface = CleanSurfaceLight,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate500,
    outline = CleanBorderLight,
    outlineVariant = CleanBorderSoft
)

@Composable
fun AutoPostStudioTheme(
    darkTheme: Boolean = false, // Clean utility design is optimized for bright, crisp minimal layout
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Alias for compatibility
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    AutoPostStudioTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
}

