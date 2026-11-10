package com.yogganstick.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Pink = Color(0xFFDB2777)
val PinkDeep = Color(0xFF9D174D)
val PinkMist = Color(0xFFFCE7F3)
val Ink = Color(0xFF26141F)
val Cream = Color(0xFFFFF8FB)
val PlumMuted = Color(0xFF6E5665)
val OutlinePink = Color(0xFFEDCBDD)
val EditorBackground = Color(0xFF171019)
val EditorPanel = Color(0xFF241B2A)
val EditorPanelHigh = Color(0xFF2E2335)
val EditorText = Color(0xFFF9EEF5)

private val colors = lightColorScheme(
    primary = Pink,
    onPrimary = Color.White,
    primaryContainer = PinkMist,
    onPrimaryContainer = Color(0xFF831843),
    secondary = Color(0xFF9333EA),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E8FF),
    onSecondaryContainer = Color(0xFF581C87),
    tertiary = Color(0xFF0E7490),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCFFAFE),
    onTertiaryContainer = Color(0xFF164E63),
    background = Cream,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF8EAF2),
    onSurfaceVariant = PlumMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF2F8),
    surfaceContainer = Color(0xFFFDEDF5),
    surfaceContainerHigh = Color(0xFFFBE7F1),
    surfaceContainerHighest = Color(0xFFF7DFEC),
    outline = OutlinePink,
    outlineVariant = Color(0xFFF3DFEA),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    surfaceTint = Pink,
)

private val typography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(
            fontWeight = FontWeight.Black,
            letterSpacing = (-1.2).sp,
        ),
        headlineLarge = base.headlineLarge.copy(
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.8).sp,
        ),
        headlineMedium = base.headlineMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.6).sp,
        ),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

@Composable
fun YogganstickTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colors,
        typography = typography,
        shapes = shapes,
        content = content,
    )
}
