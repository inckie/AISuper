package com.damn.aisuper.runtime

import com.damn.aisuper.engine.AppJSEngine
import com.damn.aisuper.layout.AppletUI
import com.damn.aisuper.storage.StateStorage
import com.damn.aisuper.storage.StateStorageFactory
import com.damn.aisuper.storage.StorageContext
import com.damn.aisuper.storage.StorageScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class Applet(
    private val engineFactory: () -> AppJSEngine,
    private val resourceLoader: AppletResourceLoader
) {
    private val _currentFeature = MutableStateFlow<Feature?>(null)
    val currentFeature = _currentFeature.asStateFlow()

    // TODO: must also be a module,
    //  so we could have headless and console applets/features
    private val ui = AppletUI()
    val currentStyleSheet = ui.currentStyleSheet
    val currentFramework = ui.currentFramework

    private var manifest: AppletManifest? = null

    // State storage: both transient and persistent storages available to all scopes
    private val compositeStorage = StateStorageFactory.createComposite()
    private val appletTransientStorage: StateStorage = compositeStorage.memoryStorage
    private val appletPersistentStorage: StateStorage = compositeStorage.persistentStorage

    suspend fun loadApplet(manifestPath: String) {
        try {
            val bytes = resourceLoader.readBytes(manifestPath)
            val jsonString = bytes.decodeToString()

            val json = Json { ignoreUnknownKeys = true }
            manifest = json.decodeFromString<AppletManifest>(jsonString)

            ui.loadStyleSheets(json, manifest, resourceLoader)

            val entryFeatureId = manifest!!.entryFeature
            launchFeature(entryFeatureId)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun registerGlobalFunctions(
        engine: AppJSEngine,
        featureTransient: StateStorage,
        featurePersistent: StateStorage
    ): AppJSEngine {
        engine.registerFunction("consoleLog") { args ->
            println("[AISuper][JS][Log] ${args.joinToString(" ") { it.toString() }}")
            JsonNull
        }

        engine.registerFunction("consoleError") { args ->
            println("[AISuper][JS][Error] ${args.joinToString(" ") { it.toString() }}")
            JsonNull
        }

        engine.registerFunction("jsonParse") { args ->
            val jsonString = args.firstOrNull()?.let {
                try { it.jsonPrimitive.contentOrNull } catch (_: Exception) { null }
            } ?: return@registerFunction JsonNull
            try {
                Json.parseToJsonElement(jsonString)
            } catch (e: Exception) {
                println("[AISuper][JS][jsonParse] Failed to parse JSON: ${e.message}")
                JsonNull
            }
        }

        engine.registerFunction("xmlParse") { args ->
            val xmlString = args.firstOrNull()?.let {
                try { it.jsonPrimitive.contentOrNull } catch (_: Exception) { null }
            } ?: return@registerFunction JsonNull
            try {
                XmlJsonParser.parse(xmlString)
            } catch (e: Exception) {
                println("[AISuper][JS][xmlParse] Failed to parse XML: ${e.message}")
                JsonNull
            }
        }

        engine.registerFunction("encodeURIComponent") { args ->
            val input = args.firstOrNull()?.let {
                try { it.jsonPrimitive.contentOrNull } catch (_: Exception) { null }
            } ?: ""
            JsonPrimitive(encodeURIComponentUtf8(input))
        }

        engine.registerFunction("getFeatures") {
            JsonArray(manifest?.features?.map { (k, v) ->
                buildJsonObject {
                    put("id", JsonPrimitive(k))
                    put("name", JsonPrimitive(v.name ?: k))
                }
            } ?: emptyList())
        }

        // Register UI-related functions (themes, frameworks)
        ui.registerFunctions(engine)

        // Helper function to safely parse strings to numbers, bypassing Keight VM conversion issues
        engine.registerFunction("stringToNumber") { args ->
            val input = args.firstOrNull()?.let {
                try { it.jsonPrimitive.contentOrNull } catch (_: Exception) { null }
            } ?: ""
            
            val result = when {
                input.isBlank() -> 0.0
                else -> input.toDoubleOrNull() ?: 0.0
            }
            JsonPrimitive(result)
        }
        engine.registerSuspendFunction("launchFeature") { args ->
            val featureId = args.firstOrNull()?.let {
                try { it.jsonPrimitive.contentOrNull } catch (_: Exception) { null }
            }
            if (featureId != null) {
                launchFeature(featureId)
            }
            JsonNull
        }

        // Storage bindings are registered via extension to keep argument parsing logic in one place.
        engine.registerStorageBindings(
            featureTransient = featureTransient,
            featurePersistent = featurePersistent
        )
        return engine
    }


    suspend fun launchFeature(featureId: String) {
        val featureDef = manifest?.features?.get(featureId)
        if (featureDef != null) {
            _currentFeature.value?.close()

            val storageContext = StorageContext(appletId = manifest!!.id, featureId = featureId)

            val featureTransient = StateStorageFactory.createScoped(appletTransientStorage, storageContext, StorageScope.Feature)
            val featurePersistent = StateStorageFactory.createScoped(appletPersistentStorage, storageContext, StorageScope.Feature)

            val decoratedEngineFactory: suspend () -> AppJSEngine = {
                registerGlobalFunctions(engineFactory(), featureTransient, featurePersistent)
            }

            val feature = Feature(
                id = featureId,
                definition = featureDef,
                engineFactory = decoratedEngineFactory,
                resourceLoader = resourceLoader,
                transientBackend = appletTransientStorage,
                persistentBackend = appletPersistentStorage,
                storageContext = storageContext
            )
            feature.load()
            _currentFeature.value = feature
        } else {
            println("Feature '$featureId' not found.")
        }
    }

    suspend fun handleAction(action: String, args: List<JsonElement> = emptyList()) {
        if (action.startsWith("launch:")) {
            val featureId = action.substringAfter("launch:")
            launchFeature(featureId)
        } else {
            _currentFeature.value?.handleAction(action, args)
        }
    }

    fun updateValue(id: String, value: JsonElement) {
        _currentFeature.value?.updateValue(id, value)
    }

    fun handleModuleCommand(
        moduleType: String,
        target: String,
        command: String,
        args: List<JsonElement> = emptyList()
    ) {
        _currentFeature.value?.handleModuleCommand(moduleType, target, command, args)
    }

    fun close() {
        _currentFeature.value?.close()
    }
}

private fun encodeURIComponentUtf8(input: String): String {
    val allowed = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.!~*'()"
    val bytes = input.encodeToByteArray()
    val out = StringBuilder(bytes.size * 3)

    for (byte in bytes) {
        val intValue = byte.toInt() and 0xFF
        val charValue = intValue.toChar()
        if (allowed.indexOf(charValue) >= 0) {
            out.append(charValue)
        } else {
            out.append('%')
            val hex = intValue.toString(16).uppercase()
            if (hex.length == 1) out.append('0')
            out.append(hex)
        }
    }

    return out.toString()
}

