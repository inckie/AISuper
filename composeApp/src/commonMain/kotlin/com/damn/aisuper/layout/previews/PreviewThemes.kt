package com.damn.aisuper.layout.previews

import com.damn.aisuper.layout.StyleSheet
import kotlinx.serialization.json.Json

/**
 * Preview-local theme bundle to keep previews self-contained across targets.
 * JSON mirrors files under composeResources/files/styles.
 */
object PreviewThemes {
    private val json = Json { ignoreUnknownKeys = true }

    val light: StyleSheet = json.decodeFromString(
        """
{
  "name": "Light",
  "defaults": {
    "Column": { "padding": 8 },
    "Row": { "paddingVertical": 4 },
    "Text": { "textColor": "#1F2937" },
    "TextField": { "containerColor": "#F3F4F6", "textColor": "#111827", "cornerRadius": 12, "paddingVertical": 2 },
    "Button": { "containerColor": "#2563EB", "textColor": "#FFFFFF", "cornerRadius": 12, "paddingVertical": 2 },
    "Dropdown": { "containerColor": "#FFFFFF", "textColor": "#111827", "cornerRadius": 12 },
    "AudioPlayer": { "backgroundColor": "#E5E7EB", "cornerRadius": 14, "padding": 10 }
  },
  "classes": {
    "screen": { "backgroundColor": "#F8FAFC", "padding": 12 },
    "header_text": { "textColor": "#0F172A", "paddingVertical": 6 },
    "section_title": { "textColor": "#334155", "paddingVertical": 6 },
    "menu_button": { "containerColor": "#1D4ED8", "textColor": "#FFFFFF", "cornerRadius": 14, "paddingVertical": 4 },
    "back_button": { "containerColor": "#64748B", "textColor": "#FFFFFF" },
    "theme_dropdown": { "containerColor": "#FFFFFF", "textColor": "#0F172A", "cornerRadius": 12 }
  }
}
        """.trimIndent()
    )

    val dark: StyleSheet = json.decodeFromString(
        """
{
  "name": "Dark",
  "defaults": {
    "Column": { "padding": 8 },
    "Row": { "paddingVertical": 4 },
    "Text": { "textColor": "#E5E7EB" },
    "TextField": { "containerColor": "#1F2937", "textColor": "#F9FAFB", "cornerRadius": 12 },
    "Button": { "containerColor": "#4F46E5", "textColor": "#F9FAFB", "cornerRadius": 12 },
    "Dropdown": { "containerColor": "#111827", "textColor": "#F9FAFB", "cornerRadius": 12 },
    "AudioPlayer": { "backgroundColor": "#111827", "cornerRadius": 14, "padding": 10 }
  },
  "classes": {
    "screen": { "backgroundColor": "#030712", "padding": 12 },
    "header_text": { "textColor": "#FFFFFF", "paddingVertical": 6 },
    "section_title": { "textColor": "#93C5FD", "paddingVertical": 6 },
    "menu_button": { "containerColor": "#1D4ED8", "textColor": "#E0E7FF", "cornerRadius": 14 },
    "back_button": { "containerColor": "#334155", "textColor": "#E2E8F0" },
    "theme_dropdown": { "containerColor": "#111827", "textColor": "#E5E7EB", "cornerRadius": 12 }
  }
}
        """.trimIndent()
    )

    val pink: StyleSheet = json.decodeFromString(
        """
{
  "name": "Pink",
  "defaults": {
    "Column": { "padding": 8 },
    "Row": { "paddingVertical": 4 },
    "Text": { "textColor": "#831843" },
    "TextField": { "containerColor": "#FCE7F3", "textColor": "#831843", "cornerRadius": 16 },
    "Button": { "containerColor": "#EC4899", "textColor": "#FFFFFF", "cornerRadius": 16 },
    "Dropdown": { "containerColor": "#FDF2F8", "textColor": "#9D174D", "cornerRadius": 14 },
    "AudioPlayer": { "backgroundColor": "#FBCFE8", "cornerRadius": 16, "padding": 10 }
  },
  "classes": {
    "screen": { "backgroundColor": "#FFF1F7", "padding": 12 },
    "header_text": { "textColor": "#9D174D", "paddingVertical": 6 },
    "section_title": { "textColor": "#BE185D", "paddingVertical": 6 },
    "menu_button": { "containerColor": "#DB2777", "textColor": "#FFF7FB", "cornerRadius": 18 },
    "back_button": { "containerColor": "#F472B6", "textColor": "#4A044E" },
    "theme_dropdown": { "containerColor": "#FDF2F8", "textColor": "#9D174D", "cornerRadius": 14 }
  }
}
        """.trimIndent()
    )

    val blueOrange: StyleSheet = json.decodeFromString(
        """
{
  "name": "Blue / Orange",
  "defaults": {
    "Column": { "padding": 8 },
    "Row": { "paddingVertical": 4 },
    "Text": { "textColor": "#0C4A6E" },
    "TextField": { "containerColor": "#E0F2FE", "textColor": "#0C4A6E", "cornerRadius": 12 },
    "Button": { "containerColor": "#F97316", "textColor": "#FFFFFF", "cornerRadius": 12 },
    "Dropdown": { "containerColor": "#E0F2FE", "textColor": "#075985", "cornerRadius": 12 },
    "AudioPlayer": { "backgroundColor": "#BAE6FD", "cornerRadius": 14, "padding": 10 }
  },
  "classes": {
    "screen": { "backgroundColor": "#F0F9FF", "padding": 12 },
    "header_text": { "textColor": "#0C4A6E", "paddingVertical": 6 },
    "section_title": { "textColor": "#EA580C", "paddingVertical": 6 },
    "menu_button": { "containerColor": "#FB923C", "textColor": "#082F49", "cornerRadius": 14 },
    "back_button": { "containerColor": "#0EA5E9", "textColor": "#FFFFFF" },
    "theme_dropdown": { "containerColor": "#E0F2FE", "textColor": "#075985", "cornerRadius": 12 }
  }
}
        """.trimIndent()
    )

    val neon: StyleSheet = json.decodeFromString(
        """
{
  "name": "Neon",
  "defaults": {
    "Column": { "padding": 8 },
    "Row": { "paddingVertical": 4 },
    "Text": { "textColor": "#67E8F9" },
    "TextField": { "containerColor": "#111827", "textColor": "#A7F3D0", "cornerRadius": 10 },
    "Button": { "containerColor": "#22D3EE", "textColor": "#0F172A", "cornerRadius": 10 },
    "Dropdown": { "containerColor": "#0F172A", "textColor": "#22D3EE", "cornerRadius": 10 },
    "AudioPlayer": { "backgroundColor": "#020617", "cornerRadius": 12, "padding": 10 }
  },
  "classes": {
    "screen": { "backgroundColor": "#000000", "padding": 12 },
    "header_text": { "textColor": "#A3E635", "paddingVertical": 6 },
    "section_title": { "textColor": "#F472B6", "paddingVertical": 6 },
    "menu_button": { "containerColor": "#A3E635", "textColor": "#052E16", "cornerRadius": 12 },
    "back_button": { "containerColor": "#F472B6", "textColor": "#111827" },
    "theme_dropdown": { "containerColor": "#020617", "textColor": "#22D3EE", "cornerRadius": 10 }
  }
}
        """.trimIndent()
    )

    val byId: Map<String, StyleSheet> = mapOf(
        "light" to light,
        "dark" to dark,
        "pink" to pink,
        "blueOrange" to blueOrange,
        "neon" to neon
    )

    val default: StyleSheet = light
}

