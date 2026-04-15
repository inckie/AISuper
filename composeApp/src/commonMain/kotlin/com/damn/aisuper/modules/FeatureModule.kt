package com.damn.aisuper.modules

import com.damn.aisuper.runtime.ModuleDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonElement

interface FeatureModule {
    suspend fun attach(context: FeatureModuleContext)
    fun detach()
}

interface NativeCommandFeatureModule {
    fun handleCommand(target: String, command: String, args: List<JsonElement>): Boolean
}

interface FeatureModuleFactory {
    val type: String
    fun create(definitions: List<ModuleDefinition>): FeatureModule
}

interface FeatureModuleContext {
    val scope: CoroutineScope

    suspend fun registerFunction(name: String, callback: (List<JsonElement>) -> JsonElement)
    suspend fun registerSuspendFunction(name: String, callback: suspend (List<JsonElement>) -> JsonElement)

    fun updateValue(id: String, value: JsonElement)
    fun readValue(id: String): JsonElement?

    suspend fun invokeScript(functionName: String, args: List<JsonElement> = emptyList()): JsonElement
}

class FeatureModuleHost(
    private val factories: Map<String, FeatureModuleFactory>,
    definitions: List<ModuleDefinition>
) {
    private val definitionsByType = definitions.groupBy { it.type }
    private val modulesByType = mutableMapOf<String, FeatureModule>()

    suspend fun attach(context: FeatureModuleContext) {
        for ((type, typedDefinitions) in definitionsByType) {
            val factory = factories[type] ?: continue
            val module = factory.create(typedDefinitions)
            module.attach(context)
            modulesByType[type] = module
        }
    }

    fun handleCommand(moduleType: String, target: String, command: String, args: List<JsonElement>): Boolean {
        val module = modulesByType[moduleType]
        if (module is NativeCommandFeatureModule) {
            return module.handleCommand(target, command, args)
        }
        return false
    }

    fun detachAll() {
        modulesByType.values.forEach { it.detach() }
        modulesByType.clear()
    }
}

