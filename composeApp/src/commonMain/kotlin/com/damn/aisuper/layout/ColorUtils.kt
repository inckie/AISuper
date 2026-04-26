package com.damn.aisuper.layout

import androidx.compose.ui.graphics.Color

/**
 * Parse a hex color string to a Compose Color.
 * Supports 6-digit RGB (#RRGGBB) and 8-digit ARGB (#AARRGGBB) formats.
 * Returns null if the input is blank or invalid.
 */
fun parseColorOrNull(raw: String?): Color? {
    if (raw.isNullOrBlank()) return null
    val hex = raw.removePrefix("#")
    return try {
        when (hex.length) {
            6 -> {
                val rgb = hex.toLong(16)
                val r = ((rgb shr 16) and 0xFF).toInt()
                val g = ((rgb shr 8) and 0xFF).toInt()
                val b = (rgb and 0xFF).toInt()
                Color(red = r, green = g, blue = b, alpha = 255)
            }

            8 -> {
                val argb = hex.toLong(16)
                val a = ((argb shr 24) and 0xFF).toInt()
                val r = ((argb shr 16) and 0xFF).toInt()
                val g = ((argb shr 8) and 0xFF).toInt()
                val b = (argb and 0xFF).toInt()
                Color(red = r, green = g, blue = b, alpha = a)
            }

            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

