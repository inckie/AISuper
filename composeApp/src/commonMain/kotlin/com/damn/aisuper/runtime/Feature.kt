package com.damn.aisuper.runtime

import aisuper.composeapp.generated.resources.Res
import com.damn.aisuper.engine.AppJSEngine
import com.damn.aisuper.layout.LayoutRoot
import com.damn.aisuper.layout.parseLayout
import com.damn.aisuper.modules.FeatureModuleContext
import com.damn.aisuper.modules.FeatureModuleHost
import com.damn.aisuper.modules.buildFeatureModuleFactories
import com.damn.aisuper.modules.impl.js.JsModuleFeatureModuleFactory
import com.damn.aisuper.modules.impl.js.JsModuleRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.ExperimentalResourceApi

class Feature(
    val id: String,
    private val definition: FeatureDefinition,
    appletJsModuleRuntimes: Map<String, JsModuleRuntime>,
    private val engine: AppJSEngine
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _layoutRoot = MutableStateFlow<LayoutRoot?>(null)
    val layoutRoot = _layoutRoot.asStateFlow()

    private val _values = MutableStateFlow<Map<String, JsonElement>>(emptyMap())
    val values = _values.asStateFlow()

    private var scriptContent: String = ""

    private val nativeModuleDefinitions = definition.modules.filterNot {
        it.type.equals("js", ignoreCase = true) || it.type.equals("jsModule", ignoreCase = true)
    }

    private val moduleHost = FeatureModuleHost(
        factories = buildFeatureModuleFactories() + mapOf(
            "jsModule" to JsModuleFeatureModuleFactory(appletJsModuleRuntimes, nativeModuleDefinitions)
        ),
        definitions = definition.modules
    )

    @OptIn(ExperimentalResourceApi::class)
    suspend fun load() {
        try {
            // Register getValue function for the script to use (SYNC)
            engine.registerFunction("getValue") { args ->
                val key = args.firstOrNull()?.jsonPrimitiveContentOrNull() ?: return@registerFunction JsonNull
                _values.value[key] ?: JsonPrimitive("")
            }

            // Register setValue function for the script to use (SYNC)
            engine.registerFunction("setValue") { args ->
                if (args.size >= 2) {
                    val key = args[0].jsonPrimitiveContentOrNull() ?: return@registerFunction JsonNull
                    val value = args[1]
                    updateValue(key, value)
                }
                JsonNull
            }

            // Layout
            val bytes = Res.readBytes(definition.layout)
            val jsonString = bytes.decodeToString()
            _layoutRoot.value = parseLayout(jsonString)

            // Script
            val scriptBytes = Res.readBytes(definition.script)
            scriptContent = scriptBytes.decodeToString()

            val moduleContext = object : FeatureModuleContext {
                override val scope: CoroutineScope = this@Feature.scope

                override suspend fun registerFunction(
                    name: String,
                    callback: (List<JsonElement>) -> JsonElement
                ) {
                    engine.registerFunction(name, callback)
                }

                override suspend fun registerSuspendFunction(
                    name: String,
                    callback: suspend (List<JsonElement>) -> JsonElement
                ) {
                    engine.registerSuspendFunction(name, callback)
                }

                override fun updateValue(id: String, value: JsonElement) {
                    this@Feature.updateValue(id, value)
                }

                override fun readValue(id: String): JsonElement? {
                    return values.value[id]
                }

                override suspend fun invokeScript(functionName: String, args: List<JsonElement>): JsonElement {
                    if (scriptContent.isBlank() || functionName.isBlank()) return JsonNull
                    return engine.execute(scriptContent, functionName, args)
                }
            }

            moduleHost.attach(moduleContext)

            // Initial execution to load functions
            engine.execute(scriptContent, "", emptyList())

            // Call initialize if present
            try {
                engine.execute(scriptContent, "initialize", emptyList())
            } catch (e: Exception) {
                println("[AISuper][JS][InitializeError] feature=$id message=${e.message}")
                e.printStackTrace()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun close() {
        moduleHost.detachAll()
        scope.cancel()
        engine.close()
    }

    fun updateValue(id: String, value: JsonElement) {
        _values.update { it + (id to value) }
    }

    suspend fun handleAction(action: String, args: List<JsonElement> = emptyList()) {
        if (action.isNotEmpty()) {
            engine.execute(scriptContent, action, args)
        }
    }

    fun handleModuleCommand(moduleType: String, target: String, command: String, args: List<JsonElement> = emptyList()) {
        moduleHost.handleCommand(moduleType, target, command, args)
    }
}

/**
 * Helper to safely extract string content from a JsonElement that is expected to be a primitive.
 */
private fun JsonElement.jsonPrimitiveContentOrNull(): String? {
    return try {
        this.jsonPrimitive.contentOrNull
    } catch (_: Exception) {
        null
    }
}
