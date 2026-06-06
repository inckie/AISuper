package com.damn.aisuper.runtime

import kotlinx.serialization.Serializable

@Serializable
data class AppletManifest(
    val id: String,
    val name: String,
    val entryFeature: String,
    /** Applet-level JS module definitions, shared across all features. */
    val jsModules: Map<String, JsModuleDefinition> = emptyMap(),
    val styles: Map<String, StyleDefinition> = emptyMap(),
    val defaultStyle: String? = null,
    val features: Map<String, FeatureDefinition>
)

@Serializable
data class JsModuleDefinition(
    val script: String,
    val name: String? = null
)

@Serializable
data class FeatureDefinition(
    val layout: String,
    val script: String,
    val name: String? = null,
    val modules: List<ModuleDefinition> = emptyList(),
    /** If true, this feature can be presented as an Android home screen widget. */
    val supportsWidget: Boolean = false
)

@Serializable
data class ModuleDefinition(
    val type: String,
    val name: String,
    /** Inline JS module script path — used when type == "jsModule". */
    val script: String? = null,
    val config: Map<String, String> = emptyMap()
)

@Serializable
data class StyleDefinition(
    val file: String,
    val name: String? = null
)
