package com.damn.aisuper.modules.impl.js

import com.damn.aisuper.engine.AppJSEngine
import com.damn.aisuper.modules.FeatureModule
import com.damn.aisuper.modules.FeatureModuleContext
import com.damn.aisuper.modules.FeatureModuleFactory
import com.damn.aisuper.runtime.AppletResourceLoader
import com.damn.aisuper.runtime.JsModuleDefinition
import com.damn.aisuper.runtime.ModuleDefinition
import com.damn.aisuper.util.Logger
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

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
    private val engine: AppJSEngine,
    private val resourceLoader: AppletResourceLoader
) : FeatureModule {
    private var isScriptLoaded: Boolean = false

    /** Exported function names declared by the module via registerExports(). */
    val exports: MutableList<String> = mutableListOf()

    private var currentImport: ModuleDefinition? = null
    private var currentDeclaredHostFunctions: Set<String> = emptySet()
    private var hostInvoker: (suspend (String, List<JsonElement>) -> JsonElement)? = null
    private val bridgedHostFunctions = mutableSetOf<String>()

    /**
     * Prepare per-feature import context before attach().
     * Called by [JsModuleFeatureModuleFactory] when building the module for a feature.
     */
    fun configureForFeature(importDefinition: ModuleDefinition, declaredHostFunctions: Set<String>) {
        currentImport = importDefinition
        currentDeclaredHostFunctions = declaredHostFunctions
    }

    suspend fun load() {
        // Register the export declaration callback
        engine.registerFunction("registerExports") { args ->
            Logger.i("JsModule", id) { "registerExports called args=${safeArgs(args)}" }
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
                    Logger.e("JsModule", id) { "registerExports parse error: ${e.message}" }
                }
            }
            JsonNull
        }

        val bytes = resourceLoader.readBytes(definition.script)
        val scriptContent = bytes.decodeToString()

        // Evaluate script — this will trigger registerExports() call
        engine.loadScript(scriptContent)
        isScriptLoaded = true

        Logger.i("JsModule", id) { "loaded, exports: $exports" }
    }

    override suspend fun attach(context: FeatureModuleContext) {
        hostInvoker = { functionName, args -> context.call(functionName, args) }

        val functionsToBridge = resolveHostFunctionsForCurrentFeature()
        for (hostFunction in functionsToBridge) {
            if (!bridgedHostFunctions.add(hostFunction)) continue
            engine.registerSuspendFunction(hostFunction) { args ->
                val invoker = hostInvoker ?: return@registerSuspendFunction JsonNull
                Logger.i("JsModule", id) { "host bridge -> $hostFunction args=${safeArgs(args)}" }
                val result = invoker(hostFunction, args)
                Logger.i("JsModule", id) { "host bridge <- $hostFunction result=${safe(result)}" }
                result
            }
        }

        if (functionsToBridge.isNotEmpty()) {
            Logger.i("JsModule", "Feature:${context.hashCode()}", id) { "bridged host functions: $functionsToBridge" }
        }

        for (exportedFn in exports) {
            val proxyName = "${id}_${exportedFn}"
            context.registerSuspendFunction(proxyName) { args ->
                Logger.i("JsModule", id) { "proxy -> $proxyName args=${safeArgs(args)}" }
                val result = callFunction(exportedFn, args)
                Logger.i("JsModule", id) { "proxy <- $proxyName result=${safe(result)}" }
                result
            }
            Logger.i("JsModule", id) { "registered proxy '$proxyName' -> module '$id'" }
        }
    }

    override fun detach() {
        hostInvoker = null
        currentImport = null
        currentDeclaredHostFunctions = emptySet()
    }

    /**
     * Call a named function inside this module's isolated VM.
     */
    suspend fun callFunction(functionName: String, args: List<JsonElement>): JsonElement {
        if (!isScriptLoaded) return JsonNull
        Logger.i("JsModule", id) { "vm call -> $functionName args=${safeArgs(args)}" }
        val result = engine.callFunction(functionName, args)
        Logger.i("JsModule", id) { "vm call <- $functionName result=${safe(result)}" }
        return result
    }

    fun close() {
        isScriptLoaded = false
        engine.close()
    }

    private fun resolveHostFunctionsForCurrentFeature(): Set<String> {
        val names = linkedSetOf<String>()

        // Module factories declare host-callable names; bridge all declared names.
        names.addAll(currentDeclaredHostFunctions)

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
    private val resourceLoader: AppletResourceLoader,
    private val allDefinitions: List<ModuleDefinition>,
    private val factoriesByType: Map<String, FeatureModuleFactory>
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
                engine = engineFactory(),
                resourceLoader = resourceLoader
            ).also { it.load() }
        }
        val nativeModuleDefinitions = allDefinitions.filter {
            !it.type.equals("jsModule", ignoreCase = true) && !it.type.equals("js", ignoreCase = true)
        }
        val declaredHostFunctions = nativeModuleDefinitions
            .flatMap { moduleDef ->
                factoriesByType.entries
                    .firstOrNull { (type, _) -> type.equals(moduleDef.type, ignoreCase = true) }
                    ?.value
                    ?.exposedFunctions
                    .orEmpty()
            }
            .toSet()

        runtime.configureForFeature(definition, declaredHostFunctions)
        return runtime
    }

    override fun close() {
        runtimes.values.forEach { it.close() }
        runtimes.clear()
    }
}
