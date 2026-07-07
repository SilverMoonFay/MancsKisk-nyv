package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = SleekPurpleContainer,
    secondary = SleekGreenContainer,
    tertiary = SleekBlueContainer,
    background = Color(0xFF1B1816),                 // Deep Dark Coffee Black
    surface = Color(0xFF2D2621),                    // Charcoal-Brown surface
    onPrimary = SleekPurpleOnContainer,
    onSecondary = Color(0xFF1B1816),
    onBackground = SleekBackground,                 // Beige text on dark background
    onSurface = SleekBackground                     // Beige text on dark surfaces
)

private val LightColorScheme = lightColorScheme(
    primary = SleekPurple,                          // Espresso Brown (Primary actions/headers)
    secondary = SleekGreen,                         // Chestnut Brown
    tertiary = SleekBlue,                           // Golden Bronze Accent
    background = SleekBackground,                   // Gorgeous Warm Beige Base
    surface = Color(0xFFFFFDF9),                    // Ultra-clean Bright Cream White for cards/surfaces
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = SleekTextPrimary,                // Deep black-brown text (Ultimate Legibility)
    onSurface = SleekTextPrimary                    // Deep black-brown text (Ultimate Legibility)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Set to false to enforce our beautiful custom pet theme!
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
