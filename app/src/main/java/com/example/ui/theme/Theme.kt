package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.data.model.ReaderTheme

private val LightPaperColorScheme = lightColorScheme(
    primary = PaperPrimary,
    onPrimary = PaperOnPrimary,
    primaryContainer = PaperPrimaryContainer,
    onPrimaryContainer = PaperOnPrimaryContainer,
    background = PaperBackground,
    onBackground = PaperTextPrimary,
    surface = PaperSurface,
    onSurface = PaperTextPrimary,
    surfaceVariant = PaperSurfaceVariant,
    onSurfaceVariant = PaperTextSecondary
)

private val OledBlackColorScheme = darkColorScheme(
    primary = OledPrimary,
    onPrimary = OledOnPrimary,
    primaryContainer = OledPrimaryContainer,
    onPrimaryContainer = OledOnPrimaryContainer,
    background = OledBackground,
    onBackground = OledTextPrimary,
    surface = OledSurface,
    onSurface = OledTextPrimary,
    surfaceVariant = OledSurfaceVariant,
    onSurfaceVariant = OledTextSecondary
)

private val SepiaColorScheme = lightColorScheme(
    primary = SepiaPrimary,
    onPrimary = SepiaOnPrimary,
    primaryContainer = SepiaPrimaryContainer,
    onPrimaryContainer = SepiaOnPrimaryContainer,
    background = SepiaBackground,
    onBackground = SepiaTextPrimary,
    surface = SepiaSurface,
    onSurface = SepiaTextPrimary,
    surfaceVariant = SepiaSurfaceVariant,
    onSurfaceVariant = SepiaTextSecondary
)

@Composable
fun ReaderAppTheme(
    readerTheme: ReaderTheme = ReaderTheme.LIGHT,
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isDark = readerTheme == ReaderTheme.OLED_DARK

    val colorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) {
                // Keep true #000000 background on OLED even when Material You accents are active
                dynamicDarkColorScheme(context).copy(
                    background = Color(0xFF000000),
                    surface = Color(0xFF0C0C0E)
                )
            } else {
                dynamicLightColorScheme(context)
            }
        }
        readerTheme == ReaderTheme.OLED_DARK -> OledBlackColorScheme
        readerTheme == ReaderTheme.SEPIA -> SepiaColorScheme
        else -> LightPaperColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
