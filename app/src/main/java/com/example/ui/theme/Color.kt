package com.example.ui.theme

import androidx.compose.ui.graphics.Color

enum class AppThemeMode(val title: String, val subtitle: String) {
    DARK("Futuristic Dark", "OLED Deep Void & Quantum Accents"),
    LIGHT("Precision Light", "High-Contrast Laboratory White"),
    SYSTEM("System Adaptive", "Synchronizes with Android OS Schedule")
}

enum class AccentPalette(
    val title: String,
    val subtitle: String,
    val primaryPreview: Color,
    val secondaryPreview: Color,
    val accentGlow: Color
) {
    CYBER_CYAN(
        title = "Quantum Cyber",
        subtitle = "Electric Cyan & Deep Obsidian",
        primaryPreview = Color(0xFF00F0FF),
        secondaryPreview = Color(0xFF0077FE),
        accentGlow = Color(0xFF00F0FF)
    ),
    NEURAL_VIOLET(
        title = "Neural Violet",
        subtitle = "Synthetic Synapse & Hyper-Magenta",
        primaryPreview = Color(0xFFB066FF),
        secondaryPreview = Color(0xFFEC4899),
        accentGlow = Color(0xFFB066FF)
    ),
    EMERALD_MATRIX(
        title = "Emerald Matrix",
        subtitle = "Terminal Bio-Green & Quantum Jade",
        primaryPreview = Color(0xFF00FFA3),
        secondaryPreview = Color(0xFF10B981),
        accentGlow = Color(0xFF00FFA3)
    ),
    SOLAR_PLASMA(
        title = "Solar Plasma",
        subtitle = "Fusion Reactor Amber & Flare Orange",
        primaryPreview = Color(0xFFFF9F1C),
        secondaryPreview = Color(0xFFFF5E00),
        accentGlow = Color(0xFFFF9F1C)
    ),
    CRIMSON_VALKYRIE(
        title = "Crimson Valkyrie",
        subtitle = "Hyperpunk Laser Red & Cyber Ruby",
        primaryPreview = Color(0xFFFF2A6D),
        secondaryPreview = Color(0xFFFA0559),
        accentGlow = Color(0xFFFF2A6D)
    ),
    OBSIDIAN_STEALTH(
        title = "Obsidian Stealth",
        subtitle = "Pure Zero-Luminance OLED Monochrome",
        primaryPreview = Color(0xFFF8FAFC),
        secondaryPreview = Color(0xFF94A3B8),
        accentGlow = Color(0xFF38BDF8)
    )
}

// 1. Quantum Cyber Palette (Default Signature Futuristic Look)
val CyanPrimaryDark = Color(0xFF00F0FF)
val CyanSecondaryDark = Color(0xFF0077FE)
val CyanTertiaryDark = Color(0xFF00FFB2)
val CyanBackgroundDark = Color(0xFF050811)
val CyanSurfaceDark = Color(0xFF0B1322)
val CyanSurfaceVariantDark = Color(0xFF132238)
val CyanOutlineDark = Color(0xFF1E3A5F)

val CyanPrimaryLight = Color(0xFF0077FE)
val CyanSecondaryLight = Color(0xFF00A3FF)
val CyanTertiaryLight = Color(0xFF00BFA5)
val CyanBackgroundLight = Color(0xFFF1F5F9)
val CyanSurfaceLight = Color(0xFFFFFFFF)
val CyanSurfaceVariantLight = Color(0xFFE2E8F0)
val CyanOutlineLight = Color(0xFF94A3B8)

// 2. Neural Violet Palette
val VioletPrimaryDark = Color(0xFFB066FF)
val VioletSecondaryDark = Color(0xFFF43F5E)
val VioletTertiaryDark = Color(0xFF38BDF8)
val VioletBackgroundDark = Color(0xFF090614)
val VioletSurfaceDark = Color(0xFF130E26)
val VioletSurfaceVariantDark = Color(0xFF231842)
val VioletOutlineDark = Color(0xFF452C7B)

val VioletPrimaryLight = Color(0xFF8B5CF6)
val VioletSecondaryLight = Color(0xFFEC4899)
val VioletTertiaryLight = Color(0xFF0284C7)
val VioletBackgroundLight = Color(0xFFFAF5FF)
val VioletSurfaceLight = Color(0xFFFFFFFF)
val VioletSurfaceVariantLight = Color(0xFFF3E8FF)
val VioletOutlineLight = Color(0xFFC084FC)

// 3. Emerald Matrix Palette
val EmeraldPrimaryDark = Color(0xFF00FFA3)
val EmeraldSecondaryDark = Color(0xFF10B981)
val EmeraldTertiaryDark = Color(0xFFA3E635)
val EmeraldBackgroundDark = Color(0xFF030D08)
val EmeraldSurfaceDark = Color(0xFF081A12)
val EmeraldSurfaceVariantDark = Color(0xFF123324)
val EmeraldOutlineDark = Color(0xFF1B593E)

val EmeraldPrimaryLight = Color(0xFF059669)
val EmeraldSecondaryLight = Color(0xFF0D9488)
val EmeraldTertiaryLight = Color(0xFF65A30D)
val EmeraldBackgroundLight = Color(0xFFF0FDF4)
val EmeraldSurfaceLight = Color(0xFFFFFFFF)
val EmeraldSurfaceVariantLight = Color(0xFFDCFCE7)
val EmeraldOutlineLight = Color(0xFF86EFAC)

// 4. Solar Plasma Palette
val SolarPrimaryDark = Color(0xFFFF9F1C)
val SolarSecondaryDark = Color(0xFFFF5E00)
val SolarTertiaryDark = Color(0xFFFFD166)
val SolarBackgroundDark = Color(0xFF0E0903)
val SolarSurfaceDark = Color(0xFF1C1307)
val SolarSurfaceVariantDark = Color(0xFF33220E)
val SolarOutlineDark = Color(0xFF664319)

val SolarPrimaryLight = Color(0xFFD97706)
val SolarSecondaryLight = Color(0xFFEA580C)
val SolarTertiaryLight = Color(0xFFCA8A04)
val SolarBackgroundLight = Color(0xFFFFFBEB)
val SolarSurfaceLight = Color(0xFFFFFFFF)
val SolarSurfaceVariantLight = Color(0xFFFEF3C7)
val SolarOutlineLight = Color(0xFFFCD34D)

// 5. Crimson Valkyrie Palette
val CrimsonPrimaryDark = Color(0xFFFF2A6D)
val CrimsonSecondaryDark = Color(0xFFFA0559)
val CrimsonTertiaryDark = Color(0xFF05D9E8)
val CrimsonBackgroundDark = Color(0xFF0F0408)
val CrimsonSurfaceDark = Color(0xFF1D0911)
val CrimsonSurfaceVariantDark = Color(0xFF361221)
val CrimsonOutlineDark = Color(0xFF6B2240)

val CrimsonPrimaryLight = Color(0xFFE11D48)
val CrimsonSecondaryLight = Color(0xFFBE123C)
val CrimsonTertiaryLight = Color(0xFF0284C7)
val CrimsonBackgroundLight = Color(0xFFFFF1F2)
val CrimsonSurfaceLight = Color(0xFFFFFFFF)
val CrimsonSurfaceVariantLight = Color(0xFFFFE4E6)
val CrimsonOutlineLight = Color(0xFFFDA4AF)

// 6. Obsidian Stealth Palette (Pure Black OLED)
val ObsidianPrimaryDark = Color(0xFFF8FAFC)
val ObsidianSecondaryDark = Color(0xFF94A3B8)
val ObsidianTertiaryDark = Color(0xFF38BDF8)
val ObsidianBackgroundDark = Color(0xFF000000)
val ObsidianSurfaceDark = Color(0xFF09090B)
val ObsidianSurfaceVariantDark = Color(0xFF18181B)
val ObsidianOutlineDark = Color(0xFF27272A)

val ObsidianPrimaryLight = Color(0xFF0F172A)
val ObsidianSecondaryLight = Color(0xFF475569)
val ObsidianTertiaryLight = Color(0xFF0284C7)
val ObsidianBackgroundLight = Color(0xFFFFFFFF)
val ObsidianSurfaceLight = Color(0xFFF8FAFC)
val ObsidianSurfaceVariantLight = Color(0xFFE2E8F0)
val ObsidianOutlineLight = Color(0xFFCBD5E1)
