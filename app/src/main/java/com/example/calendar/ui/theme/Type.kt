package com.example.calendar.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.calendar.R

/** Geist Sans. Размеры и трекинг — шкала календаря iOS. */
val GeistSans = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
    Font(R.font.geist_bold, FontWeight.Bold),
)

private fun ios(
    size: Int,
    line: Int,
    weight: FontWeight,
    tracking: Float,
) = TextStyle(
    fontFamily = GeistSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

val MacTypography = Typography(
    displayLarge = ios(34, 41, FontWeight.Bold, 0.37f),
    displayMedium = ios(28, 34, FontWeight.Bold, 0.36f),
    displaySmall = ios(22, 28, FontWeight.Bold, 0.35f),
    headlineLarge = ios(28, 34, FontWeight.SemiBold, 0.36f),
    headlineMedium = ios(22, 28, FontWeight.Bold, 0.35f),
    headlineSmall = ios(20, 25, FontWeight.Normal, 0.38f),
    titleLarge = ios(22, 28, FontWeight.SemiBold, 0.26f),
    titleMedium = ios(17, 22, FontWeight.SemiBold, -0.41f),
    titleSmall = ios(20, 25, FontWeight.Normal, 0.19f),
    bodyLarge = ios(17, 22, FontWeight.Normal, -0.41f),
    bodyMedium = ios(15, 20, FontWeight.Normal, -0.23f),
    bodySmall = ios(13, 18, FontWeight.Normal, -0.08f),
    labelLarge = ios(17, 22, FontWeight.SemiBold, -0.41f),
    labelMedium = ios(12, 16, FontWeight.Medium, 0f),
    labelSmall = ios(10, 12, FontWeight.Medium, 0.12f),
)
