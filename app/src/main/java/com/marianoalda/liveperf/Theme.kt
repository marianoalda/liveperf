package com.marianoalda.liveperf

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Navy = Color(0xFF0E1A22)
val Panel = Color(0xFF1B3A4B)
val PanelDim = Color(0xFF152C38)
val Accent = Color(0xFFE8C547)
val AccentHot = Color(0xFFFFF0A8)
val Soft = Color(0xFFD7E6EF)
val Danger = Color(0xFFE07A5F)

@Composable
fun LiveperfTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Navy,
            surface = Panel,
            primary = Accent,
            onPrimary = Navy,
            onBackground = Soft,
            onSurface = Soft,
        ),
        content = content,
    )
}
