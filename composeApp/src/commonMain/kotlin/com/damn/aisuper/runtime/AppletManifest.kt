package com.damn.aisuper.runtime

import kotlinx.serialization.Serializable

@Serializable
data class AppletManifest(
    val id: String,
    val name: String,
    val entryFeature: String,
    val features: Map<String, FeatureDefinition>
)

@Serializable
data class FeatureDefinition(
    val layout: String,
    val script: String,
    val name: String? = null,
    val modules: List<ModuleDefinition> = emptyList()
)

@Serializable
data class ModuleDefinition(
    val type: String,
    val name: String,
    val config: Map<String, String> = emptyMap()
)
