package com.damn.aisuper.runtime

import com.damn.aisuper.engine.AppJSEngine
import com.damn.aisuper.layout.LayoutRoot
import com.damn.aisuper.layout.parseLayout
import com.damn.aisuper.modules.FeatureModuleContext
import com.damn.aisuper.modules.FeatureModuleHost
import com.damn.aisuper.modules.buildFeatureModuleFactories
import com.damn.aisuper.storage.StateStorage
import com.damn.aisuper.storage.StateStorageFactory
import com.damn.aisuper.storage.StorageContext
import com.damn.aisuper.storage.StorageScope
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

class Feature(
    val id: String,
    private val definition: FeatureDefinition,
    private val engineFactory: suspend () -> AppJSEngine,
    private val resourceLoader: AppletResourceLoader,
    /** Raw transient backend shared across the applet. */
    private val transientBackend: StateStorage,
    /** Raw persistent backend shared across the applet. */
    private val persistentBackend: StateStorage,
    /** Context carrying appletId + featureId (no moduleName at feature level). */
    private val storageContext: StorageContext
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Feature-script-level storage: can access Applet + Feature scopes
    val featureTransientStorage: StateStorage = StateStorageFactory.createScoped(
        baseStorage = transientBackend,
        context = storageContext,
        maxAccessibleScope = StorageScope.Feature
    )
    val featurePersistentStorage: StateStorage = StateStorageFactory.createScoped(
        baseStorage = persistentBackend,
        context = storageContext,
        maxAccessibleScope = StorageScope.Feature
    )

    private fun moduleStorage(moduleName: String, persistent: Boolean): StateStorage {
        val backend = if (persistent) persistentBackend else transientBackend
        return StateStorageFactory.createScoped(
            baseStorage = backend,
            context = storageContext.copy(moduleName = moduleName),
            maxAccessibleScope = StorageScope.ModuleGlobal // modules can use all scopes
        )
    }

    private val _layoutRoot = MutableStateFlow<LayoutRoot?>(null)
    val layoutRoot = _layoutRoot.asStateFlow()

    private val _values = MutableStateFlow<Map<String, JsonElement>>(emptyMap())
    val values = _values.asStateFlow()

    private lateinit var engine: AppJSEngine

    private var moduleHost: FeatureModuleHost? = null

    suspend fun load() {
        try {
            engine = engineFactory()

            val factories = buildFeatureModuleFactories(engineFactory, resourceLoader, definition.modules)

            moduleHost = FeatureModuleHost(
                factories = factories,
                definitions = definition.modules
            )

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
            val bytes = resourceLoader.readBytes(definition.layout)
            val jsonString = bytes.decodeToString()
            _layoutRoot.value = parseLayout(jsonString)

            // Script
            val scriptBytes = resourceLoader.readBytes(definition.script)
            val scriptContent = scriptBytes.decodeToString()
            engine.loadScript(scriptContent)

            val syncHostFunctions = mutableMapOf<String, (List<JsonElement>) -> JsonElement>()
            val suspendHostFunctions = mutableMapOf<String, suspend (List<JsonElement>) -> JsonElement>()

            // Feature-script context (no module name — cannot access Module/ModuleGlobal scopes)
            val featureScriptContext = object : FeatureModuleContext {
                override val scope: CoroutineScope = this@Feature.scope
                override val storage: StateStorage = featureTransientStorage
                override val persistentStorage: StateStorage = featurePersistentStorage

                override suspend fun registerFunction(
                    name: String,
                    callback: (List<JsonElement>) -> JsonElement
                ) {
                    syncHostFunctions[name] = callback
                    engine.registerFunction(name, callback)
                }

                override suspend fun registerSuspendFunction(
                    name: String,
                    callback: suspend (List<JsonElement>) -> JsonElement
                ) {
                    suspendHostFunctions[name] = callback
                    engine.registerSuspendFunction(name, callback)
                }

                override fun updateValue(id: String, value: JsonElement) =
                    this@Feature.updateValue(id, value)

                override fun readValue(id: String): JsonElement? = values.value[id]

                override suspend fun call(functionName: String, args: List<JsonElement>): JsonElement {
                    if (functionName.isBlank()) return JsonNull
                    suspendHostFunctions[functionName]?.let { return it(args) }
                    syncHostFunctions[functionName]?.let { return it(args) }
                    return engine.callFunction(functionName, args)
                }
            }

            // Each module gets its own context with a per-module namespaced storage
            moduleHost?.attach { definition ->
                object : FeatureModuleContext by featureScriptContext {
                    override val storage: StateStorage = moduleStorage(definition.name, persistent = false)
                    override val persistentStorage: StateStorage = moduleStorage(definition.name, persistent = true)
                }
            }

            // Call initialize if present
            try {
                engine.callFunction("initialize", emptyList())
            } catch (e: Exception) {
                println("[AISuper][JS][InitializeError] feature=$id message=${e.message}")
                e.printStackTrace()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun close() {
        moduleHost?.close()
        moduleHost = null


        scope.cancel()
        if (this::engine.isInitialized) {
            engine.close()
        }
    }

    fun updateValue(id: String, value: JsonElement) {
        _values.update { it + (id to value) }
    }

    suspend fun handleAction(action: String, args: List<JsonElement> = emptyList()) {
        if (action.isEmpty()) return
        engine.callFunction(action, args)
    }

    fun handleModuleCommand(moduleType: String, target: String, command: String, args: List<JsonElement> = emptyList()) {
        moduleHost?.handleCommand(moduleType, target, command, args)
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
