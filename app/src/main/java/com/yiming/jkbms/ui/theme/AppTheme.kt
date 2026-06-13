package com.yiming.jkbms.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.yiming.jkbms.R
import com.yiming.jkbms.model.ThemeMode

@Composable
fun JkBmsTheme(
    useAppFont: Boolean,
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val typography = buildTypography(useAppFont = useAppFont)
    val colors = if (dark) {
        darkColorScheme(
            primary = Color(0xFF7FC8FF),
            secondary = Color(0xFF7ADBCF),
            tertiary = Color(0xFF8FD98B),
            background = Color(0xFF101418),
            surface = Color(0xFF171C22),
            surfaceVariant = Color(0xFF222A33),
            onBackground = Color(0xFFE7EDF3),
            onSurface = Color(0xFFE7EDF3)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF1E6A9E),
            secondary = Color(0xFF2A9D8F),
            tertiary = Color(0xFF2E7D32),
            background = Color(0xFFF2F5F7),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFE7EDF4),
            onBackground = Color(0xFF1B232C),
            onSurface = Color(0xFF1B232C)
        )
    }
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}

private fun buildTypography(useAppFont: Boolean, base: Typography = Typography()): Typography {
    val customFamily = if (useAppFont) {
        FontFamily(Font(R.font.consolas_bold, weight = FontWeight.Bold))
    } else {
        FontFamily.Default
    }
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = customFamily, fontWeight = FontWeight.Bold),
        displayMedium = base.displayMedium.copy(fontFamily = customFamily, fontWeight = FontWeight.Bold),
        displaySmall = base.displaySmall.copy(fontFamily = customFamily, fontWeight = FontWeight.Bold),
        headlineLarge = base.headlineLarge.copy(fontFamily = customFamily, fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontFamily = customFamily, fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontFamily = customFamily, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontFamily = customFamily, fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontFamily = customFamily, fontWeight = FontWeight.Bold),
        titleSmall = base.titleSmall.copy(fontFamily = customFamily, fontWeight = FontWeight.Bold),
        bodyLarge = base.bodyLarge.copy(fontFamily = customFamily, fontWeight = FontWeight.Bold),
        bodyMedium = base.bodyMedium.copy(fontFamily = customFamily, fontWeight = FontWeight.Bold),
        bodySmall = base.bodySmall.copy(fontFamily = customFamily, fontWeight = FontWeight.Bold),
        labelLarge = base.labelLarge.copy(fontFamily = customFamily, fontWeight = FontWeight.Bold),
        labelMedium = base.labelMedium.copy(fontFamily = customFamily, fontWeight = FontWeight.Bold),
        labelSmall = base.labelSmall.copy(fontFamily = customFamily, fontWeight = FontWeight.Bold)
    )
}
