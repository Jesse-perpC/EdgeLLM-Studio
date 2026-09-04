package com.example.ui.theme

import androidx.compose.ui.graphics.Color

enum class AppThemeMode(val title: String) {
    SYSTEM("System Default"),
    LIGHT("Light Mode"),
    DARK("Dark Mode")
}

enum class AccentPalette(val title: String, val primaryPreview: Color) {
    CYBER_CYAN("Cyber Cyan", Color(0xFF06B6D4)),
    NEURAL_VIOLET("Neural Violet", Color(0xFFA855F7)),
    EMERALD_MATRIX("Emerald Matrix", Color(0xFF10B981)),
    OBSIDIAN_STEALTH("Obsidian OLED", Color(0xFFE2E8F0))
}

// Cyber Cyan Palette
val CyanPrimaryDark = Color(0xFF06B6D4)
val CyanSecondaryDark = Color(0xFF38BDF8)
val CyanTertiaryDark = Color(0xFF10B981)
val CyanBackgroundDark = Color(0xFF0B0F19)
val CyanSurfaceDark = Color(0xFF111827)
val CyanSurfaceVariantDark = Color(0xFF1F2937)

val CyanPrimaryLight = Color(0xFF0891B2)
val CyanSecondaryLight = Color(0xFF0284C7)
val CyanTertiaryLight = Color(0xFF059669)
val CyanBackgroundLight = Color(0xFFF8FAFC)
val CyanSurfaceLight = Color(0xFFFFFFFF)
val CyanSurfaceVariantLight = Color(0xFFF1F5F9)

// Neural Violet Palette
val VioletPrimaryDark = Color(0xFFA855F7)
val VioletSecondaryDark = Color(0xFFF472B6)
val VioletTertiaryDark = Color(0xFF38BDF8)
val VioletBackgroundDark = Color(0xFF0D0B16)
val VioletSurfaceDark = Color(0xFF161226)
val VioletSurfaceVariantDark = Color(0xFF261D42)

val VioletPrimaryLight = Color(0xFF7E22CE)
val VioletSecondaryLight = Color(0xFFDB2777)
val VioletTertiaryLight = Color(0xFF0284C7)
val VioletBackgroundLight = Color(0xFFFAF5FF)
val VioletSurfaceLight = Color(0xFFFFFFFF)
val VioletSurfaceVariantLight = Color(0xFFF3E8FF)

// Emerald Matrix Palette
val EmeraldPrimaryDark = Color(0xFF10B981)
val EmeraldSecondaryDark = Color(0xFF34D399)
val EmeraldTertiaryDark = Color(0xFFA3E635)
val EmeraldBackgroundDark = Color(0xFF06100C)
val EmeraldSurfaceDark = Color(0xFF0D1D17)
val EmeraldSurfaceVariantDark = Color(0xFF162E25)

val EmeraldPrimaryLight = Color(0xFF059669)
val EmeraldSecondaryLight = Color(0xFF0D9488)
val EmeraldTertiaryLight = Color(0xFF65A30D)
val EmeraldBackgroundLight = Color(0xFFF0FDF4)
val EmeraldSurfaceLight = Color(0xFFFFFFFF)
val EmeraldSurfaceVariantLight = Color(0xFFDCFCE7)

// Obsidian Stealth (Pure Black OLED)
val ObsidianPrimaryDark = Color(0xFFF1F5F9)
val ObsidianSecondaryDark = Color(0xFF94A3B8)
val ObsidianTertiaryDark = Color(0xFF38BDF8)
val ObsidianBackgroundDark = Color(0xFF000000)
val ObsidianSurfaceDark = Color(0xFF0A0A0A)
val ObsidianSurfaceVariantDark = Color(0xFF171717)

val ObsidianPrimaryLight = Color(0xFF0F172A)
val ObsidianSecondaryLight = Color(0xFF475569)
val ObsidianTertiaryLight = Color(0xFF0284C7)
val ObsidianBackgroundLight = Color(0xFFFFFFFF)
val ObsidianSurfaceLight = Color(0xFFF8FAFC)
val ObsidianSurfaceVariantLight = Color(0xFFE2E8F0)
