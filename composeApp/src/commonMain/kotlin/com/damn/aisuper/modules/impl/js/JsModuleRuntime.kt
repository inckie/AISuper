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
    private val engine: AppJSEngine
) : FeatureModule {
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
    suspend fun load() {
        // Register the export declaration callback
        engine.registerFunction("registerExports") { args ->
            println("[AISuper][JsModule][$id] registerExports called args=${safeArgs(args)}")
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
        hostInvoker = { functionName, args -> context.call(functionName, args) }

        val functionsToBridge = resolveHostFunctionsForCurrentFeature()
        for (hostFunction in functionsToBridge) {
            if (!bridgedHostFunctions.add(hostFunction)) continue
            engine.registerSuspendFunction(hostFunction) { args ->
                val invoker = hostInvoker ?: return@registerSuspendFunction JsonNull
                println("[AISuper][JsModule][$id] host bridge -> $hostFunction args=${safeArgs(args)}")
                val result = invoker(hostFunction, args)
                println("[AISuper][JsModule][$id] host bridge <- $hostFunction result=${safe(result)}")
                result
            }
        }

        if (functionsToBridge.isNotEmpty()) {
            println("[AISuper][JsModule][${context.hashCode()}] bridged host functions for '$id': $functionsToBridge")
        }

        for (exportedFn in exports) {
            val proxyName = "${id}_${exportedFn}"
            context.registerSuspendFunction(proxyName) { args ->
                println("[AISuper][JsModule][$id] proxy -> $proxyName args=${safeArgs(args)}")
                val result = callFunction(exportedFn, args)
                println("[AISuper][JsModule][$id] proxy <- $proxyName result=${safe(result)}")
                result
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
        println("[AISuper][JsModule][$id] vm call -> $functionName args=${safeArgs(args)}")
        val result = engine.callFunction(functionName, args)
        println("[AISuper][JsModule][$id] vm call <- $functionName result=${safe(result)}")
        return result
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

    private fun safeArgs(args: List<JsonElement>): String {
        return args.joinToString(prefix = "[", postfix = "]") { safe(it) }
    }

    private fun safe(value: Any?): String {
        return value?.toString()?.replace("\n", " ")?.take(220) ?: "null"
    }
}

/**
 * Factory for [JsModuleRuntime] modules.
 *
 * Owns the full lifecycle of JS module runtimes: creates engines, loads scripts,
 * and caches runtimes — all on demand inside [create].
 * This keeps all JS-module-specific logic out of Feature.
 */
class JsModuleFeatureModuleFactory(
    private val engineFactory: suspend () -> AppJSEngine,
    private val allDefinitions: List<ModuleDefinition>
) : FeatureModuleFactory {
    override val type: String = "jsModule"

    private val runtimes = mutableMapOf<String, JsModuleRuntime>()

    override suspend fun create(definition: ModuleDefinition): FeatureModule {
        val runtime = runtimes.getOrPut(definition.name) {
            val script = definition.script
                ?: error("[AISuper][JsModule] No script for module '${definition.name}'")
            val jsDef = JsModuleDefinition(script = script, name = definition.name)
            JsModuleRuntime(
                id = definition.name,
                definition = jsDef,
                engine = engineFactory()
            ).also { it.load() }
        }
        val nativeModuleDefinitions = allDefinitions.filter {
            !it.type.equals("jsModule", ignoreCase = true) && !it.type.equals("js", ignoreCase = true)
        }
        runtime.configureForFeature(definition, nativeModuleDefinitions)
        return runtime
    }

    override fun close() {
        runtimes.values.forEach { it.close() }
        runtimes.clear()
    }
}

