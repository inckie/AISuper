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
    val name: String? = null
)
