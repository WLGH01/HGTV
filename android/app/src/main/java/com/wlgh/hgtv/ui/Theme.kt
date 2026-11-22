package com.wlgh.hgtv.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Amber = Color(0xFFF5C518)
val Bg = Color(0xFF0E0E0E)
val Panel = Color(0xFF181818)
val Muted = Color(0xFFA8A8A8)
val Danger = Color(0xFFFF6B6B)

@Composable
fun HgTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Amber,
            onPrimary = Color.Black,
            background = Bg,
            surface = Panel,
            onBackground = Color(0xFFEDEDED),
            onSurface = Color(0xFFEDEDED)
        ),
        content = content
    )
}
