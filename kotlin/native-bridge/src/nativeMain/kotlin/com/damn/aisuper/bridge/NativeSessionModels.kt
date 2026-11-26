package com.damn.aisuper.bridge

import com.damn.aisuper.layout.LayoutRoot
import com.damn.aisuper.layout.StyleSheet
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class NativeConfirmationModal(
    val id: String,
    val title: String,
    val message: String,
    val confirmText: String,
    val cancelText: String,
    val isDestructive: Boolean
)

@Serializable
data class NativeSessionSnapshot(
    val sessionId: String,
    val reason: String,
    val featureId: String = "",
    val values: Map<String, JsonElement> = emptyMap(),
    val layout: LayoutRoot? = null,
    val styleSheet: StyleSheet? = null,
    val framework: String = "Rikka",
    val modal: NativeConfirmationModal? = null
)
