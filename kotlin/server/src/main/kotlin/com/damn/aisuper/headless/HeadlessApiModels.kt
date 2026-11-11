package com.damn.aisuper.headless

import com.damn.aisuper.layout.LayoutRoot
import com.damn.aisuper.layout.StyleSheet
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class HeadlessSessionSnapshot(
    val sessionId: String,
    val reason: String,
    val featureId: String = "",
    val values: Map<String, JsonElement> = emptyMap(),
    val layout: LayoutRoot? = null,
    val styleSheet: StyleSheet? = null,
    val framework: String = "Rikka"
)

@Serializable
data class CreateSessionRequest(
    val manifestPath: String
)

@Serializable
data class CreateSessionResponse(
    val id: String,
    val manifestPath: String,
    val state: HeadlessSessionSnapshot
)

@Serializable
data class ActionRequest(
    val action: String,
    val args: List<JsonElement> = emptyList()
)

@Serializable
data class SetValueRequest(
    val id: String,
    val value: JsonElement
)

@Serializable
data class ModuleCommandRequest(
    val moduleType: String,
    val target: String,
    val command: String,
    val args: List<JsonElement> = emptyList()
)

