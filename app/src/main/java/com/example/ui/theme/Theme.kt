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
                onPrimary = Color(0xFF030712),
                secondary = CyanSecondaryDark,
                onSecondary = Color(0xFF030712),
                tertiary = CyanTertiaryDark,
                onTertiary = Color(0xFF030712),
                background = CyanBackgroundDark,
                onBackground = Color(0xFFF1F5F9),
                surface = CyanSurfaceDark,
                onSurface = Color(0xFFF1F5F9),
                surfaceVariant = CyanSurfaceVariantDark,
                onSurfaceVariant = Color(0xFFCBD5E1),
                outline = CyanOutlineDark
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
                onSurfaceVariant = Color(0xFF334155),
                outline = CyanOutlineLight
            )
        }

        AccentPalette.NEURAL_VIOLET -> if (isDark) {
            darkColorScheme(
                primary = VioletPrimaryDark,
                onPrimary = Color(0xFF05030A),
                secondary = VioletSecondaryDark,
                onSecondary = Color(0xFF05030A),
                tertiary = VioletTertiaryDark,
                onTertiary = Color(0xFF05030A),
                background = VioletBackgroundDark,
                onBackground = Color(0xFFF5F3FF),
                surface = VioletSurfaceDark,
                onSurface = Color(0xFFF5F3FF),
                surfaceVariant = VioletSurfaceVariantDark,
                onSurfaceVariant = Color(0xFFDDD6FE),
                outline = VioletOutlineDark
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
                onSurfaceVariant = Color(0xFF4C1D95),
                outline = VioletOutlineLight
            )
        }

        AccentPalette.EMERALD_MATRIX -> if (isDark) {
            darkColorScheme(
                primary = EmeraldPrimaryDark,
                onPrimary = Color(0xFF020C06),
                secondary = EmeraldSecondaryDark,
                onSecondary = Color(0xFF020C06),
                tertiary = EmeraldTertiaryDark,
                onTertiary = Color(0xFF020C06),
                background = EmeraldBackgroundDark,
                onBackground = Color(0xFFECFDF5),
                surface = EmeraldSurfaceDark,
                onSurface = Color(0xFFECFDF5),
                surfaceVariant = EmeraldSurfaceVariantDark,
                onSurfaceVariant = Color(0xFFA7F3D0),
                outline = EmeraldOutlineDark
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
                onSurfaceVariant = Color(0xFF064E3B),
                outline = EmeraldOutlineLight
            )
        }

        AccentPalette.SOLAR_PLASMA -> if (isDark) {
            darkColorScheme(
                primary = SolarPrimaryDark,
                onPrimary = Color(0xFF0E0802),
                secondary = SolarSecondaryDark,
                onSecondary = Color(0xFF0E0802),
                tertiary = SolarTertiaryDark,
                onTertiary = Color(0xFF0E0802),
                background = SolarBackgroundDark,
                onBackground = Color(0xFFFFFBEB),
                surface = SolarSurfaceDark,
                onSurface = Color(0xFFFFFBEB),
                surfaceVariant = SolarSurfaceVariantDark,
                onSurfaceVariant = Color(0xFFFDE68A),
                outline = SolarOutlineDark
            )
        } else {
            lightColorScheme(
                primary = SolarPrimaryLight,
                onPrimary = Color.White,
                secondary = SolarSecondaryLight,
                onSecondary = Color.White,
                tertiary = SolarTertiaryLight,
                onTertiary = Color.White,
                background = SolarBackgroundLight,
                onBackground = Color(0xFF451A03),
                surface = SolarSurfaceLight,
                onSurface = Color(0xFF451A03),
                surfaceVariant = SolarSurfaceVariantLight,
                onSurfaceVariant = Color(0xFF78350F),
                outline = SolarOutlineLight
            )
        }

        AccentPalette.CRIMSON_VALKYRIE -> if (isDark) {
            darkColorScheme(
                primary = CrimsonPrimaryDark,
                onPrimary = Color(0xFF0D0307),
                secondary = CrimsonSecondaryDark,
                onSecondary = Color(0xFF0D0307),
                tertiary = CrimsonTertiaryDark,
                onTertiary = Color(0xFF0D0307),
                background = CrimsonBackgroundDark,
                onBackground = Color(0xFFFFF1F2),
                surface = CrimsonSurfaceDark,
                onSurface = Color(0xFFFFF1F2),
                surfaceVariant = CrimsonSurfaceVariantDark,
                onSurfaceVariant = Color(0xFFFECDD3),
                outline = CrimsonOutlineDark
            )
        } else {
            lightColorScheme(
                primary = CrimsonPrimaryLight,
                onPrimary = Color.White,
                secondary = CrimsonSecondaryLight,
                onSecondary = Color.White,
                tertiary = CrimsonTertiaryLight,
                onTertiary = Color.White,
                background = CrimsonBackgroundLight,
                onBackground = Color(0xFF4C0519),
                surface = CrimsonSurfaceLight,
                onSurface = Color(0xFF4C0519),
                surfaceVariant = CrimsonSurfaceVariantLight,
                onSurfaceVariant = Color(0xFF881337),
                outline = CrimsonOutlineLight
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
                onSurfaceVariant = Color(0xFF94A3B8),
                outline = ObsidianOutlineDark
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
                onSurfaceVariant = Color(0xFF27272A),
                outline = ObsidianOutlineLight
            )
        }
    }
}

@Composable
fun MyApplicationTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
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
