package com.damn.aisuper.layout

import kotlinx.serialization.Serializable

@Serializable
data class StyleSheet(
    val name: String? = null,
    val defaults: Map<String, StyleRule> = emptyMap(),
    val classes: Map<String, StyleRule> = emptyMap()
)

@Serializable
data class StyleRule(
    val textColor: String? = null,
    val backgroundColor: String? = null,
    val containerColor: String? = null,
    val padding: Int? = null,
    val paddingHorizontal: Int? = null,
    val paddingVertical: Int? = null,
    val cornerRadius: Int? = null
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
            cornerRadius = other.cornerRadius ?: cornerRadius
        )
    }
}

data class NamedStyleSheet(
    val id: String,
    val name: String,
    val sheet: StyleSheet
)

