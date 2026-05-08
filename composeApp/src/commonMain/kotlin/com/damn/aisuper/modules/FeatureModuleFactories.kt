package com.damn.aisuper.modules

import com.damn.aisuper.modules.impl.audio.AudioPlayerFeatureModuleFactory
import com.damn.aisuper.modules.impl.geolocation.GeoIpGeolocationFeatureModuleFactory
import com.damn.aisuper.modules.impl.http.HttpFeatureModuleFactory
import com.damn.aisuper.modules.impl.mcp.McpHttpFeatureModuleFactory

fun buildFeatureModuleFactories(): Map<String, FeatureModuleFactory> {
    val factories = mutableListOf(
        HttpFeatureModuleFactory,
        AudioPlayerFeatureModuleFactory,
        McpHttpFeatureModuleFactory,
        GeoIpGeolocationFeatureModuleFactory
    )
    factories += platformFeatureModuleFactories()
    return factories.associateBy { it.type }
}

expect fun platformFeatureModuleFactories(): List<FeatureModuleFactory>

