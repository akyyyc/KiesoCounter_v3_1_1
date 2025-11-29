package com.example.kiesocounter_v3_1_1.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Alapértelmezett Typography (scale = 1.0)
private val BaseTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

// Scaled Typography (használja a LocalFontScale-t)
val Typography: Typography
    @Composable
    @ReadOnlyComposable
    get() {
        val scale = LocalFontScale.current
        return Typography(
            bodyLarge = BaseTypography.bodyLarge.copy(fontSize = BaseTypography.bodyLarge.fontSize * scale),
            bodyMedium = BaseTypography.bodyMedium.copy(fontSize = BaseTypography.bodyMedium.fontSize * scale),
            bodySmall = BaseTypography.bodySmall.copy(fontSize = BaseTypography.bodySmall.fontSize * scale),
            titleLarge = BaseTypography.titleLarge.copy(fontSize = BaseTypography.titleLarge.fontSize * scale),
            titleMedium = BaseTypography.titleMedium.copy(fontSize = BaseTypography.titleMedium.fontSize * scale),
            titleSmall = BaseTypography.titleSmall.copy(fontSize = BaseTypography.titleSmall.fontSize * scale),
            headlineLarge = BaseTypography.headlineLarge.copy(fontSize = BaseTypography.headlineLarge.fontSize * scale),
            headlineMedium = BaseTypography.headlineMedium.copy(fontSize = BaseTypography.headlineMedium.fontSize * scale),
            headlineSmall = BaseTypography.headlineSmall.copy(fontSize = BaseTypography.headlineSmall.fontSize * scale),
            labelLarge = BaseTypography.labelLarge.copy(fontSize = BaseTypography.labelLarge.fontSize * scale),
            labelMedium = BaseTypography.labelMedium.copy(fontSize = BaseTypography.labelMedium.fontSize * scale),
            labelSmall = BaseTypography.labelSmall.copy(fontSize = BaseTypography.labelSmall.fontSize * scale)
        )
    }