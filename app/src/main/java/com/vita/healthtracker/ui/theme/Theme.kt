package com.vita.healthtracker.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val VitaDarkColors = darkColorScheme(
    primary = VitaPrimary,
    onPrimary = VitaBackground,
    secondary = VitaSecondary,
    onSecondary = VitaBackground,
    tertiary = VitaTertiary,
    onTertiary = VitaBackground,
    background = VitaBackground,
    onBackground = VitaOnBackground,
    surface = VitaSurface,
    onSurface = VitaOnBackground,
    surfaceVariant = VitaSurfaceVariant,
    onSurfaceVariant = VitaOnSurfaceMuted,
    outline = VitaOutline,
    error = VitaError,
)

@Composable
fun VitaTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Vita 永远深色 (charlotte 偏好 #06080f),不跟系统切换
    val colorScheme = VitaDarkColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VitaTypography,
        content = content
    )
}
