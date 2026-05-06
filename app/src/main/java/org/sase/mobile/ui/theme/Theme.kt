package org.sase.mobile.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SaseLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF215C63),
    onPrimary = Color.White,
    secondary = Color(0xFF6B5A2E),
    tertiary = Color(0xFF7B4350),
    background = Color(0xFFFAFBF8),
    surface = Color(0xFFFAFBF8),
    surfaceVariant = Color(0xFFE0E5DF),
    onSurface = Color(0xFF1C1D1A),
)

@Composable
fun SaseMobileTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SaseLightColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
