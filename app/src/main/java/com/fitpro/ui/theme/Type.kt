package com.fitpro.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val FitProTypography = Typography(
    displayLarge  = TextStyle(fontWeight = FontWeight.W300, fontSize = 57.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontWeight = FontWeight.W300, fontSize = 45.sp),
    displaySmall  = TextStyle(fontWeight = FontWeight.W400, fontSize = 36.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.W500, fontSize = 32.sp),
    headlineMedium= TextStyle(fontWeight = FontWeight.W500, fontSize = 28.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.W500, fontSize = 24.sp),
    titleLarge    = TextStyle(fontWeight = FontWeight.W500, fontSize = 22.sp),
    titleMedium   = TextStyle(fontWeight = FontWeight.W500, fontSize = 16.sp, letterSpacing = 0.15.sp),
    titleSmall    = TextStyle(fontWeight = FontWeight.W500, fontSize = 14.sp, letterSpacing = 0.1.sp),
    bodyLarge     = TextStyle(fontWeight = FontWeight.W400, fontSize = 16.sp, letterSpacing = 0.5.sp),
    bodyMedium    = TextStyle(fontWeight = FontWeight.W400, fontSize = 14.sp, letterSpacing = 0.25.sp),
    bodySmall     = TextStyle(fontWeight = FontWeight.W400, fontSize = 12.sp, letterSpacing = 0.4.sp),
    labelLarge    = TextStyle(fontWeight = FontWeight.W500, fontSize = 14.sp, letterSpacing = 0.1.sp),
    labelMedium   = TextStyle(fontWeight = FontWeight.W500, fontSize = 12.sp, letterSpacing = 0.5.sp),
    labelSmall    = TextStyle(fontWeight = FontWeight.W500, fontSize = 11.sp, letterSpacing = 0.5.sp)
)
