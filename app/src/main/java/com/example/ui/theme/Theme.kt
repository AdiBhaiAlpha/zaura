package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val ZauraDarkColorScheme = darkColorScheme(
    primary = ZauraCyan,
    onPrimary = Color.Black,
    primaryContainer = ZauraSurfaceVariantDark,
    onPrimaryContainer = ZauraCyanLight,
    secondary = ZauraIndigo,
    onSecondary = Color.White,
    secondaryContainer = ZauraSurfaceCard,
    onSecondaryContainer = ZauraIndigoLight,
    tertiary = ZauraEmerald,
    background = ZauraNavyDark,
    onBackground = ZauraTextPrimary,
    surface = ZauraSurfaceDark,
    onSurface = ZauraTextPrimary,
    surfaceVariant = ZauraSurfaceVariantDark,
    onSurfaceVariant = ZauraTextSecondary,
    outline = ZauraBorder,
    outlineVariant = ZauraBorderFocus,
    error = ZauraRose
)

private val ZauraLightColorScheme = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0369A1),
    secondary = Color(0xFF4F46E5),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEEF2FF),
    onSecondaryContainer = Color(0xFF3730A3),
    tertiary = Color(0xFF059669),
    background = ZauraBgLight,
    onBackground = ZauraTextPrimaryLight,
    surface = ZauraSurfaceLight,
    onSurface = ZauraTextPrimaryLight,
    surfaceVariant = ZauraSurfaceVariantLight,
    onSurfaceVariant = ZauraTextSecondaryLight,
    outline = ZauraBorderLight,
    error = Color(0xFFDC2626)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ZauraDarkColorScheme else ZauraLightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.surface.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
