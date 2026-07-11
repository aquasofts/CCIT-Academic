package edu.ccit.webvpn.core.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val WebVpnTypography = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(
            fontSize = 34.sp,
            lineHeight = 42.sp,
            fontWeight = FontWeight.Bold,
        ),
        titleLarge = titleLarge.copy(
            fontSize = 22.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Bold,
        ),
        titleMedium = titleMedium.copy(
            fontSize = 18.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Bold,
        ),
        bodyLarge = bodyLarge.copy(
            fontSize = 17.sp,
            lineHeight = 24.sp,
        ),
        labelLarge = labelLarge.copy(
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Bold,
        ),
    )
}
