package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val HudColorScheme = darkColorScheme(
    primary = HudCyan,
    onPrimary = Color(0xFF00252E),
    primaryContainer = HudSurfaceElevated,
    onPrimaryContainer = HudCyanBright,

    secondary = HudAmber,
    onSecondary = Color(0xFF2E1C00),
    secondaryContainer = Color(0xFF2D2005),
    onSecondaryContainer = HudAmber,

    tertiary = HudGreen,
    onTertiary = Color(0xFF002B13),
    tertiaryContainer = Color(0xFF052B18),
    onTertiaryContainer = HudGreen,

    background = HudBackground,
    onBackground = HudTextPrimary,

    surface = HudSurface,
    onSurface = HudTextPrimary,
    surfaceVariant = HudSurfaceElevated,
    onSurfaceVariant = HudTextSecondary,

    outline = HudBorder,
    outlineVariant = HudGridLine,

    error = HudRed,
    onError = Color.White
)

@Composable
fun NullHudTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                // Force white status bar & nav bar icons so they remain 100% visible on dark HUD
                // regardless of whether the user device has light theme ("white theme") or dark theme.
                insetsController.isAppearanceLightStatusBars = false
                insetsController.isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = HudColorScheme,
        typography = Typography,
        content = content
    )
}

// Backwards compatibility alias
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    NullHudTheme(content = content)
}
