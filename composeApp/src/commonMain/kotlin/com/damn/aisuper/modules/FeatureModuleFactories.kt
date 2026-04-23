package com.damn.aisuper.modules

fun buildFeatureModuleFactories(): Map<String, FeatureModuleFactory> {
    val factories = mutableListOf(
        HttpFeatureModuleFactory,
        AudioPlayerFeatureModuleFactory,
        McpHttpFeatureModuleFactory
    )
    factories += platformFeatureModuleFactories()
    return factories.associateBy { it.type }
}

expect fun platformFeatureModuleFactories(): List<FeatureModuleFactory>

