package com.volunteerdash

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Cobalt = Color(0xFF1F4FE0)
val CobaltDark = Color(0xFF1638A8)
val CobaltBright = Color(0xFF3C6CF5)
val CobaltSoft = Color(0xFFE6EDFF)
val Coral = Color(0xFFFF6B5B)
val CoralWarm = Color(0xFFFF8E6E)
val CoralSoft = Color(0xFFFFE6E2)
val Mint = Color(0xFF2BB673)
val MintSoft = Color(0xFFE3F7EE)
val Ink = Color(0xFF0F1A3D)
val Slate = Color(0xFF5A6787)
val Bg = Color(0xFFF6F8FD)
val Line = Color(0xFFE2E7F2)
val Gold = Color(0xFFF4B740)
val Silver = Color(0xFFA9B4C8)
val Bronze = Color(0xFFD08A5A)

private fun jakarta(w: Int) = Font(
    R.font.plus_jakarta_sans,
    weight = FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w))
)

val Jakarta = FontFamily(jakarta(400), jakarta(500), jakarta(600), jakarta(700), jakarta(800))
val Serif = FontFamily(Font(R.font.dm_serif_display, FontWeight.Normal))

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = Serif, fontSize = 34.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = Serif, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = Serif, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Jakarta, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Jakarta, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Jakarta, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp),
)

@Composable
fun VolunteerDashTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Cobalt, onPrimary = Color.White, primaryContainer = CobaltSoft, onPrimaryContainer = CobaltDark,
            secondary = Coral, onSecondary = Color.White, secondaryContainer = CoralSoft, onSecondaryContainer = Color(0xFF7A2318),
            tertiary = Mint, tertiaryContainer = MintSoft,
            background = Bg, onBackground = Ink, surface = Color.White, onSurface = Ink,
            surfaceVariant = Color(0xFFF0F3FA), onSurfaceVariant = Slate, outline = Line, outlineVariant = Line,
            surfaceContainer = Color.White, surfaceContainerHigh = Color.White, surfaceContainerLow = Bg,
        ),
        typography = AppTypography,
        content = content
    )
}
