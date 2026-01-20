package com.shakercontrol.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Dark theme is the default for industrial HMI applications.
 * Reduces eye strain in various lighting conditions and
 * makes status indicators more visible.
 */
private val DarkColorScheme = darkColorScheme(
    // Primary colors
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,

    // Secondary colors
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,

    // Background and surface
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainer = SurfaceContainerDark,

    // Error (using our alarm color)
    error = SemanticColors.Alarm,
    onError = SemanticColors.OnAlarm,
    errorContainer = SemanticColors.AlarmContainer,
    onErrorContainer = OnSurfaceDark,

    // Outline
    outline = OnSurfaceVariantDark,
    outlineVariant = SurfaceVariantDark
)

@Composable
fun ShakerControlTheme(
    darkTheme: Boolean = true,  // Always dark for HMI
    content: @Composable () -> Unit
) {
    // For now, always use dark theme as per spec
    val colorScheme = DarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
