package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * Bundled Noto Sans Bengali + Noto Sans font families so Bengali UI text,
 * conjuncts (যুক্তাক্ষর), and vowel signs (কার) render accurately on every
 * Android version from 8.0 (minSdk 26) up to 200% font scale without clipping.
 */
val NotoSansBengaliFontFamily = FontFamily(
    Font(resId = R.font.noto_sans_bengali, weight = FontWeight.Normal),
    Font(resId = R.font.noto_sans_bengali, weight = FontWeight.Medium),
    Font(resId = R.font.noto_sans_bengali, weight = FontWeight.SemiBold),
    Font(resId = R.font.noto_sans_bengali, weight = FontWeight.Bold),
    Font(resId = R.font.noto_sans_bengali, weight = FontWeight.ExtraBold)
)

val NotoSansFontFamily = FontFamily(
    Font(resId = R.font.noto_sans, weight = FontWeight.Normal),
    Font(resId = R.font.noto_sans, weight = FontWeight.Medium),
    Font(resId = R.font.noto_sans, weight = FontWeight.SemiBold),
    Font(resId = R.font.noto_sans, weight = FontWeight.Bold),
    Font(resId = R.font.noto_sans, weight = FontWeight.ExtraBold)
)

private val SafeBengaliPlatformStyle = PlatformTextStyle(
    includeFontPadding = true
)

private val SafeLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Proportional,
    trim = LineHeightStyle.Trim.None
)

fun buildAppTypography(useBengaliFont: Boolean): Typography {
    val family = if (useBengaliFont) NotoSansBengaliFontFamily else NotoSansFontFamily
    return Typography(
        displayLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = 48.sp,
            lineHeight = 64.sp,
            platformStyle = SafeBengaliPlatformStyle,
            lineHeightStyle = SafeLineHeightStyle
        ),
        displayMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = 38.sp,
            lineHeight = 52.sp,
            platformStyle = SafeBengaliPlatformStyle,
            lineHeightStyle = SafeLineHeightStyle
        ),
        displaySmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            lineHeight = 44.sp,
            platformStyle = SafeBengaliPlatformStyle,
            lineHeightStyle = SafeLineHeightStyle
        ),
        headlineLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = 40.sp,
            platformStyle = SafeBengaliPlatformStyle,
            lineHeightStyle = SafeLineHeightStyle
        ),
        headlineMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 34.sp,
            platformStyle = SafeBengaliPlatformStyle,
            lineHeightStyle = SafeLineHeightStyle
        ),
        headlineSmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            lineHeight = 30.sp,
            platformStyle = SafeBengaliPlatformStyle,
            lineHeightStyle = SafeLineHeightStyle
        ),
        titleLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 30.sp,
            platformStyle = SafeBengaliPlatformStyle,
            lineHeightStyle = SafeLineHeightStyle
        ),
        titleMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 25.sp,
            letterSpacing = 0.15.sp,
            platformStyle = SafeBengaliPlatformStyle,
            lineHeightStyle = SafeLineHeightStyle
        ),
        titleSmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.1.sp,
            platformStyle = SafeBengaliPlatformStyle,
            lineHeightStyle = SafeLineHeightStyle
        ),
        bodyLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 26.sp,
            letterSpacing = 0.25.sp,
            platformStyle = SafeBengaliPlatformStyle,
            lineHeightStyle = SafeLineHeightStyle
        ),
        bodyMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.2.sp,
            platformStyle = SafeBengaliPlatformStyle,
            lineHeightStyle = SafeLineHeightStyle
        ),
        bodySmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 19.sp,
            letterSpacing = 0.2.sp,
            platformStyle = SafeBengaliPlatformStyle,
            lineHeightStyle = SafeLineHeightStyle
        ),
        labelLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.1.sp,
            platformStyle = SafeBengaliPlatformStyle,
            lineHeightStyle = SafeLineHeightStyle
        ),
        labelMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.25.sp,
            platformStyle = SafeBengaliPlatformStyle,
            lineHeightStyle = SafeLineHeightStyle
        ),
        labelSmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 17.sp,
            letterSpacing = 0.25.sp,
            platformStyle = SafeBengaliPlatformStyle,
            lineHeightStyle = SafeLineHeightStyle
        )
    )
}

val Typography = buildAppTypography(useBengaliFont = true)
