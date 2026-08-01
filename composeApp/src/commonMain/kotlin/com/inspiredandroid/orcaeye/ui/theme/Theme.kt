package com.inspiredandroid.orcaeye.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Full schemes so Material defaults (purple seed) never leak through.
private val LightColors =
    lightColorScheme(
        primary = Color(0xFF111111),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE8E8E8),
        onPrimaryContainer = Color(0xFF111111),
        secondary = Color(0xFF3A3A3A),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE0E0E0),
        onSecondaryContainer = Color(0xFF1A1A1A),
        tertiary = Color(0xFF5A5A5A),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFEDEDED),
        onTertiaryContainer = Color(0xFF2A2A2A),
        background = Color(0xFFFAFAFA),
        onBackground = Color(0xFF111111),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF111111),
        surfaceVariant = Color(0xFFF0F0F0),
        onSurfaceVariant = Color(0xFF5C5C5C),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF5F5F5),
        surfaceContainer = Color(0xFFF0F0F0),
        surfaceContainerHigh = Color(0xFFEBEBEB),
        surfaceContainerHighest = Color(0xFFE4E4E4),
        outline = Color(0xFFBDBDBD),
        outlineVariant = Color(0xFFE0E0E0),
        inverseSurface = Color(0xFF1A1A1A),
        inverseOnSurface = Color(0xFFF5F5F5),
        inversePrimary = Color(0xFFE0E0E0),
        scrim = Color(0xFF000000),
        error = Color(0xFFB3261E),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFFF0F0F0),
        onPrimary = Color(0xFF111111),
        primaryContainer = Color(0xFF2A2A2A),
        onPrimaryContainer = Color(0xFFF0F0F0),
        secondary = Color(0xFFC8C8C8),
        onSecondary = Color(0xFF1A1A1A),
        secondaryContainer = Color(0xFF333333),
        onSecondaryContainer = Color(0xFFE8E8E8),
        tertiary = Color(0xFFB0B0B0),
        onTertiary = Color(0xFF1A1A1A),
        tertiaryContainer = Color(0xFF2E2E2E),
        onTertiaryContainer = Color(0xFFE0E0E0),
        background = Color(0xFF121212),
        onBackground = Color(0xFFF0F0F0),
        surface = Color(0xFF1A1A1A),
        onSurface = Color(0xFFF0F0F0),
        surfaceVariant = Color(0xFF2A2A2A),
        onSurfaceVariant = Color(0xFFB0B0B0),
        surfaceContainerLowest = Color(0xFF0E0E0E),
        surfaceContainerLow = Color(0xFF161616),
        surfaceContainer = Color(0xFF1E1E1E),
        surfaceContainerHigh = Color(0xFF262626),
        surfaceContainerHighest = Color(0xFF2E2E2E),
        outline = Color(0xFF5A5A5A),
        outlineVariant = Color(0xFF333333),
        inverseSurface = Color(0xFFE8E8E8),
        inverseOnSurface = Color(0xFF1A1A1A),
        inversePrimary = Color(0xFF2A2A2A),
        scrim = Color(0xFF000000),
        error = Color(0xFFF2B8B5),
        onError = Color(0xFF601410),
        errorContainer = Color(0xFF8C1D18),
        onErrorContainer = Color(0xFFF9DEDC),
    )

@Composable
fun OrcaeyeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
