package com.damn.aisuper.runtime

import com.damn.aisuper.engine.AppJSEngine
import com.damn.aisuper.layout.AppletUI
import com.damn.aisuper.storage.StateStorage
import com.damn.aisuper.storage.StateStorageFactory
import com.damn.aisuper.storage.StorageContext
import com.damn.aisuper.storage.StorageScope
import com.damn.aisuper.util.Logger
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import com.damn.aisuper.modal.DefaultModalHostState
import com.damn.aisuper.modal.ModalHostState

/**
 * Core runtime Applet definition.
 * 
 * @wk-id applet-core
 * @wk-tags kotlin, runtime, lifecycle
 * @wk-categories applet-lifecycle
 *
 * Manages the lifecycle and feature modules for a dynamically loaded Applet.
 * See [[applet-lifecycle]] for loading mechanisms.
 */
class Applet(
    private val engineFactory: () -> AppJSEngine,
    private val resourceLoader: AppletResourceLoader
) {
    private val _currentFeature = MutableStateFlow<Feature?>(null)
    val currentFeature = _currentFeature.asStateFlow()

    // State storage: both transient and persistent storages available to all scopes
    private val compositeStorage = StateStorageFactory.createComposite()
    val appletTransientStorage: StateStorage = compositeStorage.memoryStorage
    val appletPersistentStorage: StateStorage = compositeStorage.persistentStorage

    // TODO: must also be a module,
    //  so we could have headless and console applets/features
    private val ui = AppletUI(appletPersistentStorage)
    val currentStyleSheet = ui.currentStyleSheet
    val currentFramework = ui.currentFramework

    val modalHost: ModalHostState = DefaultModalHostState()

    private var manifest: AppletManifest? = null

    suspend fun loadApplet(
        manifestPath: String,
        entryFeatureId: String? = null) {
        try {
            _currentFeature.value?.close()
            _currentFeature.value = null

            val bytes = resourceLoader.readBytes(manifestPath)
            val jsonString = bytes.decodeToString()

            val json = Json { ignoreUnknownKeys = true }
            manifest = json.decodeFromString<AppletManifest>(jsonString)

            ui.loadStyleSheets(json, manifest, resourceLoader)

            val featureToLaunch = entryFeatureId ?: manifest!!.entryFeature
            launchFeature(featureToLaunch)

        } catch (e: Exception) {
            Logger.e("Runtime", "Applet", throwable = e) { "Failed to load manifest $manifestPath" }
            throw e
        }
    }

    private suspend fun registerGlobalFunctions(
        engine: AppJSEngine,
        featureTransient: StateStorage,
        featurePersistent: StateStorage
    ): AppJSEngine {
        engine.registerFunction("consoleLog") { args ->
            Logger.i("JS", "Log") { args.joinToString(" ") { it.toString() } }
            JsonNull
        }

        engine.registerFunction("consoleError") { args ->
            Logger.e("JS", "Error") { args.joinToString(" ") { it.toString() } }
            JsonNull
        }

        // Register console object shim using rest parameters (supported by QuickJS/Keight)
        // We define it as a global object on globalThis to ensure it's available everywhere.
        // Simplified version: just bind to native functions if they exist.
        engine.evaluate("""
            (function() {
                var g = (typeof globalThis !== 'undefined' ? globalThis : typeof self !== 'undefined' ? self : typeof window !== 'undefined' ? window : {});
                g.console = {
                    log: function() { if (typeof consoleLog === 'function') consoleLog.apply(null, arguments); },
                    error: function() { if (typeof consoleError === 'function') consoleError.apply(null, arguments); },
                    warn: function() { if (typeof consoleLog === 'function') consoleLog.apply(null, arguments); },
                    info: function() { if (typeof consoleLog === 'function') consoleLog.apply(null, arguments); },
                    debug: function() { if (typeof consoleLog === 'function') consoleLog.apply(null, arguments); }
                };
            })();
        """.trimIndent())

        engine.registerFunction("jsonParse") { args ->
            val jsonString = args.firstOrNull()?.let {
                try { it.jsonPrimitive.contentOrNull } catch (_: Exception) { null }
            } ?: return@registerFunction JsonNull
            try {
                Json.parseToJsonElement(jsonString)
            } catch (e: Exception) {
                Logger.e("JS", "jsonParse") { "Failed to parse JSON: ${e.message}" }
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
                Logger.e("JS", "xmlParse") { "Failed to parse XML: ${e.message}" }
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

        engine.registerSuspendFunction("launchFeature") { args ->
            val featureId = args.firstOrNull()?.let {
                try { it.jsonPrimitive.contentOrNull } catch (_: Exception) { null }
            }
            if (featureId != null) {
                launchFeature(featureId)
            }
            JsonNull
        }

        val confirmHandler: suspend (List<JsonElement>) -> JsonElement = { args ->
            val firstArg = args.firstOrNull()
            val title: String
            val message: String
            val confirmText: String
            val cancelText: String
            val isDestructive: Boolean

            if (firstArg is kotlinx.serialization.json.JsonObject) {
                title = firstArg["title"]?.jsonPrimitive?.contentOrNull ?: "Confirmation"
                message = firstArg["message"]?.jsonPrimitive?.contentOrNull ?: ""
                confirmText = firstArg["confirmText"]?.jsonPrimitive?.contentOrNull ?: "Confirm"
                cancelText = firstArg["cancelText"]?.jsonPrimitive?.contentOrNull ?: "Cancel"
                isDestructive = firstArg["isDestructive"]?.jsonPrimitive?.booleanOrNull ?: false
            } else {
                message = firstArg?.jsonPrimitive?.contentOrNull ?: ""
                title = args.getOrNull(1)?.jsonPrimitive?.contentOrNull ?: "Confirmation"
                confirmText = args.getOrNull(2)?.jsonPrimitive?.contentOrNull ?: "Confirm"
                cancelText = args.getOrNull(3)?.jsonPrimitive?.contentOrNull ?: "Cancel"
                isDestructive = args.getOrNull(4)?.jsonPrimitive?.booleanOrNull ?: false
            }

            val result = modalHost.showConfirmation(
                title = title,
                message = message,
                confirmText = confirmText,
                cancelText = cancelText,
                isDestructive = isDestructive
            )
            JsonPrimitive(result)
        }

        engine.registerSuspendFunction("confirm", confirmHandler)
        engine.registerSuspendFunction("showConfirmation", confirmHandler)
        engine.registerSuspendFunction("confirmation", confirmHandler)

        // Storage bindings are registered via extension to keep argument parsing logic in one place.
        engine.registerStorageBindings(
            featureTransient = featureTransient,
            featurePersistent = featurePersistent
        )
        return engine
    }


    suspend fun launchFeature(featureId: String): Unit = withContext(NonCancellable) {
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
            _currentFeature.value = feature
            feature.load()
        } else {
            Logger.w("Runtime") { "Feature '$featureId' not found." }
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
        modalHost.dismissCurrent()
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
