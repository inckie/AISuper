package com.damn.aisuper.modules

import com.damn.aisuper.engine.AppJSEngine
import com.damn.aisuper.modules.impl.audio.AudioPlayerFeatureModuleFactory
import com.damn.aisuper.modules.impl.geolocation.GeoIpGeolocationFeatureModuleFactory
import com.damn.aisuper.modules.impl.http.HttpFeatureModuleFactory
import com.damn.aisuper.modules.impl.js.JsModuleFeatureModuleFactory
import com.damn.aisuper.modules.impl.mcp.McpHttpFeatureModuleFactory
import com.damn.aisuper.runtime.AppletResourceLoader
import com.damn.aisuper.runtime.ModuleDefinition

fun buildFeatureModuleFactories(
    engineFactory: suspend () -> AppJSEngine,
    resourceLoader: AppletResourceLoader,
    moduleDefinitions: List<ModuleDefinition>
): Map<String, FeatureModuleFactory> {
    val factories = mutableListOf(
        HttpFeatureModuleFactory,
        AudioPlayerFeatureModuleFactory,
        McpHttpFeatureModuleFactory,
        GeoIpGeolocationFeatureModuleFactory
    )
    factories += platformFeatureModuleFactories()

    val nativeFactoriesByType = factories.associateBy { it.type }
    val jsFactory = JsModuleFeatureModuleFactory(
        engineFactory = engineFactory,
        resourceLoader = resourceLoader,
        allDefinitions = moduleDefinitions,
        factoriesByType = nativeFactoriesByType
    )

    return (factories + jsFactory).associateBy { it.type }
}

expect fun platformFeatureModuleFactories(): List<FeatureModuleFactory>
