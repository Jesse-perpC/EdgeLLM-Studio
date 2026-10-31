package com.perpcorp.edgellm.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * Runtime gradient tokens derived from the active [AccentPalette]. Screens can
 * read these through [LocalEdgeGradients] to paint hero surfaces, glow rings,
 * and the animated aurora without hardcoding colors.
 */
data class EdgeGradientTheme(
    val accentA: Color,
    val accentB: Color,
    val accentC: Color,
    val brandGradient: Brush,
    val auroraStops: List<Color>,
)

val LocalEdgeGradients = staticCompositionLocalOf {
    EdgeGradientTheme(
        accentA = Color(0xFF6FE7FF),
        accentB = Color(0xFF9F8CFF),
        accentC = Color(0xFFFFC978),
        brandGradient = Brush.linearGradient(
            listOf(Color(0xFF4FD8FF), Color(0xFF8E7BFF), Color(0xFFFFD08A)),
        ),
        auroraStops = listOf(Color(0xFF4FD8FF), Color(0xFF8E7BFF), Color(0xFFFFD08A)),
    )
}

private fun createColorScheme(palette: AccentPalette, isDark: Boolean): ColorScheme {
    val primary = if (isDark) palette.primaryPreview else palette.lightPrimary
    val secondary = if (isDark) palette.secondaryPreview else palette.lightSecondary
    val tertiary = if (isDark) palette.accentGlow else palette.lightTertiary

    return if (isDark) {
        darkColorScheme(
            primary = primary,
            onPrimary = Color(0xFF03101A),
            primaryContainer = lerp(VoidSurface, primary, 0.22f),
            onPrimaryContainer = lerp(primary, Color.White, 0.62f),
            inversePrimary = palette.lightPrimary,
            secondary = secondary,
            onSecondary = Color(0xFF03101A),
            secondaryContainer = lerp(VoidSurface, secondary, 0.22f),
            onSecondaryContainer = lerp(secondary, Color.White, 0.62f),
            tertiary = tertiary,
            onTertiary = Color(0xFF03101A),
            tertiaryContainer = lerp(VoidSurface, tertiary, 0.18f),
            onTertiaryContainer = lerp(tertiary, Color.White, 0.62f),
            background = VoidBlack,
            onBackground = VoidTextHigh,
            surface = VoidSurface,
            onSurface = VoidTextHigh,
            surfaceVariant = VoidSurfaceVariant,
            onSurfaceVariant = VoidTextMid,
            surfaceTint = primary,
            inverseSurface = Color(0xFFE9EEFC),
            inverseOnSurface = VoidSurface,
            error = Color(0xFFFF6B81),
            onError = Color(0xFF2A0409),
            errorContainer = Color(0xFF3A0D16),
            onErrorContainer = Color(0xFFFFC2CB),
            outline = VoidOutline,
            outlineVariant = VoidOutlineVariant,
            scrim = Color.Black,
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = lerp(PorcelainSurface, primary, 0.15f),
            onPrimaryContainer = lerp(primary, Color.Black, 0.45f),
            inversePrimary = palette.primaryPreview,
            secondary = secondary,
            onSecondary = Color.White,
            secondaryContainer = lerp(PorcelainSurface, secondary, 0.14f),
            onSecondaryContainer = lerp(secondary, Color.Black, 0.45f),
            tertiary = tertiary,
            onTertiary = Color.White,
            tertiaryContainer = lerp(PorcelainSurface, tertiary, 0.14f),
            onTertiaryContainer = lerp(tertiary, Color.Black, 0.45f),
            background = PorcelainBackground,
            onBackground = PorcelainTextHigh,
            surface = PorcelainSurface,
            onSurface = PorcelainTextHigh,
            surfaceVariant = PorcelainSurfaceVariant,
            onSurfaceVariant = PorcelainTextMid,
            surfaceTint = primary,
            inverseSurface = PorcelainTextHigh,
            inverseOnSurface = PorcelainBackground,
            error = Color(0xFFD7263D),
            onError = Color.White,
            errorContainer = Color(0xFFFFD9DE),
            onErrorContainer = Color(0xFF4A0410),
            outline = PorcelainOutline,
            outlineVariant = PorcelainOutlineVariant,
            scrim = Color.Black,
        )
    }
}

@Composable
fun MyApplicationTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    accentPalette: AccentPalette = AccentPalette.AETHER_ION,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val colorScheme = remember(accentPalette, isDark) { createColorScheme(accentPalette, isDark) }
    val gradients = remember(accentPalette, isDark) {
        val a = if (isDark) accentPalette.primaryPreview else accentPalette.lightPrimary
        val b = if (isDark) accentPalette.secondaryPreview else accentPalette.lightSecondary
        val c = if (isDark) accentPalette.accentGlow else accentPalette.lightTertiary
        EdgeGradientTheme(
            accentA = a,
            accentB = b,
            accentC = c,
            brandGradient = Brush.linearGradient(
                listOf(accentPalette.gradientStart, accentPalette.gradientMid, accentPalette.gradientEnd),
            ),
            auroraStops = listOf(
                accentPalette.gradientStart,
                accentPalette.gradientMid,
                accentPalette.gradientEnd,
            ),
        )
    }

    CompositionLocalProvider(LocalEdgeGradients provides gradients) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content,
        )
    }
}

/**
 * Ambient animated aurora. Draws a slow-moving neon nebula behind content; the
 * animation is intentionally subtle so text contrast is preserved.
 */
@Composable
fun EdgeAuroraCanvas(modifier: Modifier = Modifier) {
    val gradients = LocalEdgeGradients.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val transition = rememberInfiniteTransition(label = "edge-aether")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 26000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "aether-drift",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "aether-pulse",
    )
    val scan by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "aether-scan",
    )

    val glowA = gradients.accentA.copy(alpha = if (isDark) 0.18f else 0.11f)
    val glowB = gradients.accentB.copy(alpha = if (isDark) 0.15f else 0.09f)
    val base = MaterialTheme.colorScheme.background
    val washTop = if (isDark) lerp(base, gradients.accentB, 0.07f) else lerp(base, gradients.accentA, 0.05f)
    val washBottom = if (isDark) lerp(base, gradients.accentC, 0.04f) else lerp(base, gradients.accentB, 0.03f)
    val gridColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color(0xFF0B1020).copy(alpha = 0.045f)
    val arcColor = gradients.accentA.copy(alpha = if (isDark) 0.18f else 0.13f)

    Box(
        modifier = modifier.drawBehind {
            drawRect(Brush.verticalGradient(listOf(washTop, base, washBottom)))

            // Soft ion haze anchors the composition without washing out text.
            val hazeRadius = size.maxDimension * (0.6f + pulse * 0.16f)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(glowA, Color.Transparent),
                    center = Offset(size.width * (0.12f + drift * 0.10f), size.height * 0.14f),
                    radius = hazeRadius,
                ),
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(glowB, Color.Transparent),
                    center = Offset(size.width * (0.88f - drift * 0.12f), size.height * 0.42f),
                    radius = hazeRadius * 1.05f,
                ),
            )

            // Orbital telemetry arcs in the upper corner.
            val arcCenter = Offset(size.width * 0.94f, size.height * 0.05f)
            val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 20f), drift * 48f)
            for (ring in 1..3) {
                drawCircle(
                    color = arcColor.copy(alpha = arcColor.alpha / ring),
                    radius = size.minDimension * (0.10f + ring * 0.07f),
                    center = arcCenter,
                    style = Stroke(width = 1.2f, pathEffect = dash),
                )
            }

            // Perspective lattice floor.
            val horizon = size.height * 0.64f
            val vanishX = size.width * (0.5f + (drift - 0.5f) * 0.08f)
            for (lane in -7..7) {
                drawLine(
                    color = gridColor,
                    start = Offset(vanishX + lane * size.width * 0.012f, horizon),
                    end = Offset(size.width * 0.5f + lane * size.width * 0.17f, size.height),
                    strokeWidth = 1f,
                )
            }
            var lineY = horizon + size.height * 0.008f
            var lineGap = size.height * 0.008f
            while (lineY < size.height) {
                drawLine(gridColor, Offset(0f, lineY), Offset(size.width, lineY), strokeWidth = 1f)
                lineY += lineGap
                lineGap *= 1.3f
            }

            // Slow vertical scan band, like a sensor sweep.
            val scanCenter = size.height * (0.1f + scan * 0.85f)
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        gradients.accentA.copy(alpha = if (isDark) 0.055f else 0.045f),
                        Color.Transparent,
                    ),
                    startY = scanCenter - 90f,
                    endY = scanCenter + 90f,
                ),
                topLeft = Offset(0f, (scanCenter - 90f).coerceAtLeast(0f)),
                size = Size(size.width, 180f),
            )

            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        gradients.accentA.copy(alpha = 0.45f),
                        gradients.accentB.copy(alpha = 0.35f),
                        Color.Transparent,
                    ),
                ),
                topLeft = Offset.Zero,
                size = Size(size.width, 1.5f),
            )
        },
    )
}

/**
 * Convenience wrapper that paints the aurora and hosts content above it.
 */
@Composable
fun EdgeAuroraBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        EdgeAuroraCanvas(modifier = Modifier.matchParentSize())
        content()
    }
}
