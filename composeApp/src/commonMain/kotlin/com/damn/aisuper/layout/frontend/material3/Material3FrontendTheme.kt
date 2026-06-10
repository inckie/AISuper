package com.damn.aisuper.layout.frontend.material3

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.damn.aisuper.layout.StyleSheet
import com.damn.aisuper.layout.parseColorOrNull

@Composable
fun Material3FrontendTheme(
    styleSheet: StyleSheet?,
    content: @Composable () -> Unit
) {
    val isDark = styleSheet?.scheme.equals("dark", ignoreCase = true)
    val accent = parseColorOrNull(styleSheet?.tokens?.accentColor)
        ?: if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)

    val onPrimary = if (accent.luminance() > 0.5f) Color.Black else Color.White
    val screenBackground = parseColorOrNull(styleSheet?.classes?.get("screen")?.backgroundColor)

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = accent,
            secondary = accent,
            tertiary = accent,
            onPrimary = onPrimary,
            background = screenBackground ?: Color(0xFF030712)
        )
    } else {
        lightColorScheme(
            primary = accent,
            secondary = accent,
            tertiary = accent,
            onPrimary = onPrimary,
            background = screenBackground ?: Color(0xFFF8FAFC)
        )
    }

    MaterialTheme(colorScheme = colorScheme) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            content = content
        )
    }
}

