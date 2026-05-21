package com.example.beans.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme =
    lightColorScheme(
        primary = Color(0xFF4A6741),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFC8E6C0),
        secondary = Color(0xFF52634F),
        surface = Color(0xFFFCFDF6),
        background = Color(0xFFFCFDF6),
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = Color(0xFFA8D5A0),
        onPrimary = Color(0xFF1B3517),
        primaryContainer = Color(0xFF324F2C),
        secondary = Color(0xFFBACCB5),
        surface = Color(0xFF1A1C19),
        background = Color(0xFF1A1C19),
    )

@Composable
fun BeansTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
