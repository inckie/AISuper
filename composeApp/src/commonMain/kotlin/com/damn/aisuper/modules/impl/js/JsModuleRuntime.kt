package com.damn.aisuper.modules.impl.js

import aisuper.composeapp.generated.resources.Res
import com.damn.aisuper.engine.AppJSEngine
import com.damn.aisuper.modules.FeatureModule
import com.damn.aisuper.modules.FeatureModuleContext
import com.damn.aisuper.modules.FeatureModuleFactory
import com.damn.aisuper.runtime.JsModuleDefinition
import com.damn.aisuper.runtime.ModuleDefinition
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * Isolated JS module runtime with its own VM.
 *
 * Lifetime: created and loaded at Applet scope; attached/detached per Feature.
 *
 * Script contract:
 *   registerExports("moduleName", ["fn1", "fn2", ...])
 */
class JsModuleRuntime(
    val id: String,
    private val definition: JsModuleDefinition,
    engineFactory: () -> AppJSEngine
) : FeatureModule {
    private val engine: AppJSEngine = engineFactory()
    private var isScriptLoaded: Boolean = false

    /** Exported function names declared by the module via registerExports(). */
    val exports: MutableList<String> = mutableListOf()

    private var currentImport: ModuleDefinition? = null
    private var currentNativeTypes: Set<String> = emptySet()
    private var hostInvoker: (suspend (String, List<JsonElement>) -> JsonElement)? = null
    private val bridgedHostFunctions = mutableSetOf<String>()

    /**
     * Prepare per-feature import context before attach().
     * Called by [JsModuleFeatureModuleFactory] when building the module for a feature.
     */
    fun configureForFeature(importDefinition: ModuleDefinition, nativeModuleDefinitions: List<ModuleDefinition>) {
        currentImport = importDefinition
        currentNativeTypes = nativeModuleDefinitions.map { it.type.lowercase() }.toSet()
    }

    @OptIn(ExperimentalResourceApi::class)
    suspend fun load(registerGlobalFunctions: suspend (AppJSEngine) -> Unit) {
        // Provide global helpers (httpGet, jsonParse, encodeURIComponent, etc.)
        registerGlobalFunctions(engine)

        // Register the export declaration callback
        engine.registerFunction("registerExports") { args ->
            // args[0] = module name (ignored, we already know it as 'id')
            // args[1] = JsonArray of function name strings
            if (args.size >= 2) {
                try {
                    val array = args[1].jsonArray
                    for (elem in array) {
                        val name = elem.jsonPrimitive.contentOrNull
                        if (!name.isNullOrBlank()) exports.add(name)
                    }
                } catch (e: Exception) {
                    println("[AISuper][JsModule][$id] registerExports parse error: ${e.message}")
                }
            }
            JsonNull
        }

        val bytes = Res.readBytes(definition.script)
        val scriptContent = bytes.decodeToString()

        // Evaluate script — this will trigger registerExports() call
        engine.loadScript(scriptContent)
        isScriptLoaded = true

        println("[AISuper][JsModule][$id] loaded, exports: $exports")
    }

    override suspend fun attach(context: FeatureModuleContext) {
        hostInvoker = { functionName, args -> context.invokeScript(functionName, args) }

        val functionsToBridge = resolveHostFunctionsForCurrentFeature()
        for (hostFunction in functionsToBridge) {
            if (!bridgedHostFunctions.add(hostFunction)) continue
            engine.registerSuspendFunction(hostFunction) { args ->
                val invoker = hostInvoker ?: return@registerSuspendFunction JsonNull
                invoker(hostFunction, args)
            }
        }

        if (functionsToBridge.isNotEmpty()) {
            println("[AISuper][JsModule][${context.hashCode()}] bridged host functions for '$id': $functionsToBridge")
        }

        for (exportedFn in exports) {
            val proxyName = "${id}_${exportedFn}"
            context.registerSuspendFunction(proxyName) { args ->
                callFunction(exportedFn, args)
            }
            println("[AISuper][JsModule] registered proxy '$proxyName' -> module '$id'")
        }
    }

    override fun detach() {
        hostInvoker = null
        currentImport = null
        currentNativeTypes = emptySet()
    }

    /**
     * Call a named function inside this module's isolated VM.
     */
    suspend fun callFunction(functionName: String, args: List<JsonElement>): JsonElement {
        if (!isScriptLoaded) return JsonNull
        return engine.callFunction(functionName, args)
    }

    fun close() {
        isScriptLoaded = false
        engine.close()
    }

    private fun resolveHostFunctionsForCurrentFeature(): Set<String> {
        val names = linkedSetOf<String>()

        if ("http" in currentNativeTypes) {
            names.add("httpGet")
        }

        val configValue = currentImport?.config?.get("hostFunctions")
        if (!configValue.isNullOrBlank()) {
            configValue
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { names.add(it) }
        }

        return names
    }
}

/**
 * Factory for [JsModuleRuntime] modules.
 *
 * Holds applet-level runtimes (already loaded). For each feature that declares a jsModule import,
 * locates the corresponding runtime, configures it per-feature context, and returns it directly
 * for attachment via [FeatureModuleHost] — unified with all other module types.
 *
 * [nativeModuleDefinitions] are provided so the runtime can bridge native host functions
 * (e.g. httpGet bridging when an http module is also present in the feature).
 */
class JsModuleFeatureModuleFactory(
    private val appletRuntimes: Map<String, JsModuleRuntime>,
    private val nativeModuleDefinitions: List<ModuleDefinition>
) : FeatureModuleFactory {
    override val type: String = "jsModule"

    override fun create(definition: ModuleDefinition): FeatureModule {
        val runtime = appletRuntimes[definition.name]
            ?: error("[AISuper][JsModule] Runtime '${definition.name}' not found in applet runtimes")
        runtime.configureForFeature(definition, nativeModuleDefinitions)
        return runtime
    }
}

