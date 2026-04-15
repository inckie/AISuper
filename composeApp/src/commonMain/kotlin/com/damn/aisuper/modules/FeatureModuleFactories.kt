package com.damn.aisuper.modules

fun buildFeatureModuleFactories(): Map<String, FeatureModuleFactory> {
    val factories = mutableListOf(
        HttpFeatureModuleFactory,
        AudioPlayerFeatureModuleFactory
    )
    factories += platformFeatureModuleFactories()
    return factories.associateBy { it.type }
}

expect fun platformFeatureModuleFactories(): List<FeatureModuleFactory>

