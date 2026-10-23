package com.perpcorp.edgellm.ui.theme

import androidx.compose.ui.graphics.Color

enum class AppThemeMode(val title: String, val subtitle: String) {
    DARK("Futuristic Dark", "OLED Deep Void & Quantum Accents"),
    LIGHT("Precision Light", "High-Contrast Laboratory White"),
    SYSTEM("System Adaptive", "Synchronizes with Android OS Schedule"),
}

/**
 * Signature accent systems. Each palette carries a dark-mode brand triple, a
 * tuned light-mode triple, and the three-stop gradient used by the aurora
 * background, chips, and hero surfaces.
 */
enum class AccentPalette(
    val title: String,
    val subtitle: String,
    val primaryPreview: Color,
    val secondaryPreview: Color,
    val accentGlow: Color,
    val lightPrimary: Color,
    val lightSecondary: Color,
    val lightTertiary: Color,
    val gradientStart: Color,
    val gradientMid: Color,
    val gradientEnd: Color,
) {
    CYBER_CYAN(
        title = "Quantum Cyber",
        subtitle = "Electric Cyan • Indigo Pulse • Hyper Magenta",
        primaryPreview = Color(0xFF22D3EE),
        secondaryPreview = Color(0xFF6366F1),
        accentGlow = Color(0xFFF472B6),
        lightPrimary = Color(0xFF0891B2),
        lightSecondary = Color(0xFF4F46E5),
        lightTertiary = Color(0xFFDB2777),
        gradientStart = Color(0xFF06B6D4),
        gradientMid = Color(0xFF6366F1),
        gradientEnd = Color(0xFFEC4899),
    ),
    NEURAL_VIOLET(
        title = "Neural Violet",
        subtitle = "Synthetic Synapse • Ultraviolet • Neon Rose",
        primaryPreview = Color(0xFFA855F7),
        secondaryPreview = Color(0xFFEC4899),
        accentGlow = Color(0xFF22D3EE),
        lightPrimary = Color(0xFF7C3AED),
        lightSecondary = Color(0xFFDB2777),
        lightTertiary = Color(0xFF0891B2),
        gradientStart = Color(0xFF7C3AED),
        gradientMid = Color(0xFFEC4899),
        gradientEnd = Color(0xFF22D3EE),
    ),
    EMERALD_MATRIX(
        title = "Emerald Matrix",
        subtitle = "Bio-Luminescent Green • Quantum Jade • Lime",
        primaryPreview = Color(0xFF34D399),
        secondaryPreview = Color(0xFF06B6D4),
        accentGlow = Color(0xFFA3E635),
        lightPrimary = Color(0xFF059669),
        lightSecondary = Color(0xFF0891B2),
        lightTertiary = Color(0xFF65A30D),
        gradientStart = Color(0xFF10B981),
        gradientMid = Color(0xFF06B6D4),
        gradientEnd = Color(0xFF84CC16),
    ),
    SOLAR_PLASMA(
        title = "Solar Plasma",
        subtitle = "Fusion Amber • Reactor Coral • Solar Flare",
        primaryPreview = Color(0xFFFB923C),
        secondaryPreview = Color(0xFFF43F5E),
        accentGlow = Color(0xFFFACC15),
        lightPrimary = Color(0xFFEA580C),
        lightSecondary = Color(0xFFE11D48),
        lightTertiary = Color(0xFFCA8A04),
        gradientStart = Color(0xFFF97316),
        gradientMid = Color(0xFFEF4444),
        gradientEnd = Color(0xFFFACC15),
    ),
    CRIMSON_VALKYRIE(
        title = "Crimson Valkyrie",
        subtitle = "Hyperpunk Laser Red • Cyber Ruby • Ice Blue",
        primaryPreview = Color(0xFFFB7185),
        secondaryPreview = Color(0xFFF43F5E),
        accentGlow = Color(0xFF38BDF8),
        lightPrimary = Color(0xFFE11D48),
        lightSecondary = Color(0xFFBE123C),
        lightTertiary = Color(0xFF0284C7),
        gradientStart = Color(0xFFE11D48),
        gradientMid = Color(0xFFF43F5E),
        gradientEnd = Color(0xFF38BDF8),
    ),
    OBSIDIAN_STEALTH(
        title = "Obsidian Stealth",
        subtitle = "Zero-Luminance OLED • Titanium • Cold Plasma",
        primaryPreview = Color(0xFFE2E8F0),
        secondaryPreview = Color(0xFF94A3B8),
        accentGlow = Color(0xFF38BDF8),
        lightPrimary = Color(0xFF0F172A),
        lightSecondary = Color(0xFF475569),
        lightTertiary = Color(0xFF0284C7),
        gradientStart = Color(0xFF64748B),
        gradientMid = Color(0xFF94A3B8),
        gradientEnd = Color(0xFF38BDF8),
    ),
}

// Shared deep-space neutrals. Kept almost black so neon accents carry the UI.
val VoidBlack = Color(0xFF05060E)
val VoidSurface = Color(0xFF0A0C1A)
val VoidSurfaceVariant = Color(0xFF141830)
val VoidOutline = Color(0xFF2A3252)
val VoidOutlineVariant = Color(0xFF1A2038)
val VoidTextHigh = Color(0xFFF2F5FF)
val VoidTextMid = Color(0xFFAEB9D9)

// Shared porcelain neutrals for the light theme.
val PorcelainBackground = Color(0xFFF2F5FC)
val PorcelainSurface = Color(0xFFFFFFFF)
val PorcelainSurfaceVariant = Color(0xFFE6EBF7)
val PorcelainOutline = Color(0xFFB9C3DB)
val PorcelainOutlineVariant = Color(0xFFD5DCEC)
val PorcelainTextHigh = Color(0xFF0B1020)
val PorcelainTextMid = Color(0xFF46506B)
