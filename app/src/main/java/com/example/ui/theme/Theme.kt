package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private fun createColorScheme(palette: AccentPalette, isDark: Boolean): ColorScheme {
    return when (palette) {
        AccentPalette.CYBER_CYAN -> if (isDark) {
            darkColorScheme(
                primary = CyanPrimaryDark,
                onPrimary = Color.Black,
                secondary = CyanSecondaryDark,
                onSecondary = Color.Black,
                tertiary = CyanTertiaryDark,
                onTertiary = Color.Black,
                background = CyanBackgroundDark,
                onBackground = Color(0xFFF1F5F9),
                surface = CyanSurfaceDark,
                onSurface = Color(0xFFF1F5F9),
                surfaceVariant = CyanSurfaceVariantDark,
                onSurfaceVariant = Color(0xFFCBD5E1)
            )
        } else {
            lightColorScheme(
                primary = CyanPrimaryLight,
                onPrimary = Color.White,
                secondary = CyanSecondaryLight,
                onSecondary = Color.White,
                tertiary = CyanTertiaryLight,
                onTertiary = Color.White,
                background = CyanBackgroundLight,
                onBackground = Color(0xFF0F172A),
                surface = CyanSurfaceLight,
                onSurface = Color(0xFF0F172A),
                surfaceVariant = CyanSurfaceVariantLight,
                onSurfaceVariant = Color(0xFF334155)
            )
        }

        AccentPalette.NEURAL_VIOLET -> if (isDark) {
            darkColorScheme(
                primary = VioletPrimaryDark,
                onPrimary = Color.Black,
                secondary = VioletSecondaryDark,
                onSecondary = Color.Black,
                tertiary = VioletTertiaryDark,
                onTertiary = Color.Black,
                background = VioletBackgroundDark,
                onBackground = Color(0xFFF5F3FF),
                surface = VioletSurfaceDark,
                onSurface = Color(0xFFF5F3FF),
                surfaceVariant = VioletSurfaceVariantDark,
                onSurfaceVariant = Color(0xFFDDD6FE)
            )
        } else {
            lightColorScheme(
                primary = VioletPrimaryLight,
                onPrimary = Color.White,
                secondary = VioletSecondaryLight,
                onSecondary = Color.White,
                tertiary = VioletTertiaryLight,
                onTertiary = Color.White,
                background = VioletBackgroundLight,
                onBackground = Color(0xFF1E1B4B),
                surface = VioletSurfaceLight,
                onSurface = Color(0xFF1E1B4B),
                surfaceVariant = VioletSurfaceVariantLight,
                onSurfaceVariant = Color(0xFF4C1D95)
            )
        }

        AccentPalette.EMERALD_MATRIX -> if (isDark) {
            darkColorScheme(
                primary = EmeraldPrimaryDark,
                onPrimary = Color.Black,
                secondary = EmeraldSecondaryDark,
                onSecondary = Color.Black,
                tertiary = EmeraldTertiaryDark,
                onTertiary = Color.Black,
                background = EmeraldBackgroundDark,
                onBackground = Color(0xFFECFDF5),
                surface = EmeraldSurfaceDark,
                onSurface = Color(0xFFECFDF5),
                surfaceVariant = EmeraldSurfaceVariantDark,
                onSurfaceVariant = Color(0xFFA7F3D0)
            )
        } else {
            lightColorScheme(
                primary = EmeraldPrimaryLight,
                onPrimary = Color.White,
                secondary = EmeraldSecondaryLight,
                onSecondary = Color.White,
                tertiary = EmeraldTertiaryLight,
                onTertiary = Color.White,
                background = EmeraldBackgroundLight,
                onBackground = Color(0xFF022C22),
                surface = EmeraldSurfaceLight,
                onSurface = Color(0xFF022C22),
                surfaceVariant = EmeraldSurfaceVariantLight,
                onSurfaceVariant = Color(0xFF064E3B)
            )
        }

        AccentPalette.OBSIDIAN_STEALTH -> if (isDark) {
            darkColorScheme(
                primary = ObsidianPrimaryDark,
                onPrimary = Color.Black,
                secondary = ObsidianSecondaryDark,
                onSecondary = Color.Black,
                tertiary = ObsidianTertiaryDark,
                onTertiary = Color.Black,
                background = ObsidianBackgroundDark,
                onBackground = Color(0xFFF8FAFC),
                surface = ObsidianSurfaceDark,
                onSurface = Color(0xFFF8FAFC),
                surfaceVariant = ObsidianSurfaceVariantDark,
                onSurfaceVariant = Color(0xFF94A3B8)
            )
        } else {
            lightColorScheme(
                primary = ObsidianPrimaryLight,
                onPrimary = Color.White,
                secondary = ObsidianSecondaryLight,
                onSecondary = Color.White,
                tertiary = ObsidianTertiaryLight,
                onTertiary = Color.White,
                background = ObsidianBackgroundLight,
                onBackground = Color(0xFF09090B),
                surface = ObsidianSurfaceLight,
                onSurface = Color(0xFF09090B),
                surfaceVariant = ObsidianSurfaceVariantLight,
                onSurfaceVariant = Color(0xFF27272A)
            )
        }
    }
}

@Composable
fun MyApplicationTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    accentPalette: AccentPalette = AccentPalette.CYBER_CYAN,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val colorScheme = createColorScheme(accentPalette, isDark)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
