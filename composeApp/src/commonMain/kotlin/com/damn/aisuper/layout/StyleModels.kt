package com.damn.aisuper.layout

import kotlinx.serialization.Serializable

@Serializable
data class StyleSheet(
    val name: String? = null,
    val scheme: String = "light",
    val tokens: StyleTokens = StyleTokens(),
    val defaults: Map<String, StyleRule> = emptyMap(),
    val classes: Map<String, StyleRule> = emptyMap()
)

@Serializable
data class StyleTokens(
    val accentColor: String? = null,
    val destructiveColor: String? = null,
    val values: Map<String, String> = emptyMap()
)

@Serializable
data class StyleRule(
    val textColor: String? = null,
    val backgroundColor: String? = null,
    val containerColor: String? = null,
    val padding: Int? = null,
    val paddingHorizontal: Int? = null,
    val paddingVertical: Int? = null,
    val cornerRadius: Int? = null,
    val fontSize: Int? = null,
    val textAlign: String? = null // "left", "center", "right", "justify"
) {
    fun mergedWith(other: StyleRule?): StyleRule {
        if (other == null) return this
        return StyleRule(
            textColor = other.textColor ?: textColor,
            backgroundColor = other.backgroundColor ?: backgroundColor,
            containerColor = other.containerColor ?: containerColor,
            padding = other.padding ?: padding,
            paddingHorizontal = other.paddingHorizontal ?: paddingHorizontal,
            paddingVertical = other.paddingVertical ?: paddingVertical,
            cornerRadius = other.cornerRadius ?: cornerRadius,
            fontSize = other.fontSize ?: fontSize,
            textAlign = other.textAlign ?: textAlign
        )
    }
}

data class NamedStyleSheet(
    val id: String,
    val name: String,
    val sheet: StyleSheet
)

