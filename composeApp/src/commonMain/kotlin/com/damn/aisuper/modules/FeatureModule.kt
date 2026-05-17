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
    suspend fun create(definition: ModuleDefinition): FeatureModule
    /** Release any resources held by this factory. */
    fun close() {}
}

interface FeatureModuleContext {
    val scope: CoroutineScope

    suspend fun registerFunction(name: String, callback: (List<JsonElement>) -> JsonElement)
    suspend fun registerSuspendFunction(name: String, callback: suspend (List<JsonElement>) -> JsonElement)

    fun updateValue(id: String, value: JsonElement)
    fun readValue(id: String): JsonElement?

    suspend fun call(functionName: String, args: List<JsonElement> = emptyList()): JsonElement
}

class FeatureModuleHost(
    private val factories: Map<String, FeatureModuleFactory>,
    private val definitions: List<ModuleDefinition>
) {
    private val modulesByName = mutableMapOf<String, FeatureModule>()

    suspend fun attach(context: FeatureModuleContext) {

        for (definition in definitions) {
            val factory = factories[definition.type] ?: continue
            val module = factory.create(definition)
            module.attach(context)
            modulesByName[definition.name] = module
        }
    }

    fun handleCommand(moduleType: String, target: String, command: String, args: List<JsonElement>): Boolean {
        val module = modulesByName[target]
        if (module is NativeCommandFeatureModule) {
            return module.handleCommand(target, command, args)
        }
        return false
    }

    fun detachAll() {
        modulesByName.values.forEach { it.detach() }
        modulesByName.clear()
    }

    fun close() {
        detachAll()
        factories.values.forEach { it.close() }
    }
}
