package com.damn.aisuper.runtime

import aisuper.composeapp.generated.resources.Res
import com.damn.aisuper.engine.AppJSEngine
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
import org.jetbrains.compose.resources.ExperimentalResourceApi

class Applet(
    private val engineFactory: () -> AppJSEngine
) {
    private val _currentFeature = MutableStateFlow<Feature?>(null)
    val currentFeature = _currentFeature.asStateFlow()

    private var manifest: AppletManifest? = null

    /** Isolated JS runtimes for each declared jsModule. Keyed by module id. */
    private val jsModuleRuntimes = mutableMapOf<String, JsModuleRuntime>()

    @OptIn(ExperimentalResourceApi::class)
    suspend fun loadApplet(manifestPath: String) {
        try {
            val bytes = Res.readBytes(manifestPath)
            val jsonString = bytes.decodeToString()

            val json = Json { ignoreUnknownKeys = true }
            manifest = json.decodeFromString<AppletManifest>(jsonString)

            // Load isolated JS module VMs
            jsModuleRuntimes.values.forEach { it.close() }
            jsModuleRuntimes.clear()
            manifest!!.jsModules.forEach { (moduleId, moduleDef) ->
                val runtime = JsModuleRuntime(moduleId, moduleDef, engineFactory)
                runtime.load(::registerGlobalFunctions)
                jsModuleRuntimes[moduleId] = runtime
            }

            val entryFeatureId = manifest!!.entryFeature
            launchFeature(entryFeatureId)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun registerGlobalFunctions(engine: AppJSEngine) {
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

        // launchFeature is suspending because it loads resources, so use registerSuspendFunction
        engine.registerSuspendFunction("launchFeature") { args ->
            val featureId = args.firstOrNull()?.let {
                try { it.jsonPrimitive.contentOrNull } catch (_: Exception) { null }
            }
            if (featureId != null) {
                launchFeature(featureId)
            }
            JsonNull
        }
    }

    suspend fun launchFeature(featureId: String) {
        val featureDef = manifest?.features?.get(featureId)
        if (featureDef != null) {
            // Unload previous
            _currentFeature.value?.close()

            // Create new engine for this feature
            val engine = engineFactory()
            registerGlobalFunctions(engine)

            val feature = Feature(
                id = featureId,
                definition = featureDef,
                jsModuleRuntimes = jsModuleRuntimes,
                engine = engine
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
        jsModuleRuntimes.values.forEach { it.close() }
        jsModuleRuntimes.clear()
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

