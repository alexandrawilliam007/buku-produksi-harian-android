package com.bukuproduksiharian.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkGreenPrimary = Color(0xFF14532D)
val MediumGreenSecondary = Color(0xFF166534)
val SoftGreenSurface = Color(0xFFF0FDF4)
val NeutralGrayBg = Color(0xFFF8FAFC)
val NeutralBorder = Color(0xFFE2E8F0)

private val AppColorScheme = lightColorScheme(
    primary = DarkGreenPrimary,
    onPrimary = Color.White,
    secondary = MediumGreenSecondary,
    onSecondary = Color.White,
    background = NeutralGrayBg,
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    outline = NeutralBorder
)

@Composable
fun BukuProduksiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        content = content
    )
}
