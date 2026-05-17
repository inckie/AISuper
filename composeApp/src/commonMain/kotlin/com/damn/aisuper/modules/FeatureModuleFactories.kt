package com.damn.aisuper.modules

import com.damn.aisuper.engine.AppJSEngine
import com.damn.aisuper.modules.impl.audio.AudioPlayerFeatureModuleFactory
import com.damn.aisuper.modules.impl.geolocation.GeoIpGeolocationFeatureModuleFactory
import com.damn.aisuper.modules.impl.http.HttpFeatureModuleFactory
import com.damn.aisuper.modules.impl.js.JsModuleFeatureModuleFactory
import com.damn.aisuper.modules.impl.mcp.McpHttpFeatureModuleFactory
import com.damn.aisuper.runtime.ModuleDefinition

fun buildFeatureModuleFactories(
    engineFactory: suspend () -> AppJSEngine,
    moduleDefinitions: List<ModuleDefinition>
): Map<String, FeatureModuleFactory> {
    val factories = mutableListOf(
        HttpFeatureModuleFactory,
        AudioPlayerFeatureModuleFactory,
        McpHttpFeatureModuleFactory,
        GeoIpGeolocationFeatureModuleFactory,
        JsModuleFeatureModuleFactory(engineFactory, moduleDefinitions)
    )
    factories += platformFeatureModuleFactories()
    return factories.associateBy { it.type }
}

expect fun platformFeatureModuleFactories(): List<FeatureModuleFactory>
