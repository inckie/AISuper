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

    // For MVP, we can still load a specific feature directly if needed,
    // but primary entry point is loading an applet manifest.

    @OptIn(ExperimentalResourceApi::class)
    suspend fun loadApplet(manifestPath: String) {
        try {
            val bytes = Res.readBytes(manifestPath)
            val jsonString = bytes.decodeToString()

            val json = Json { ignoreUnknownKeys = true }
            manifest = json.decodeFromString<AppletManifest>(jsonString)

            val entryFeatureId = manifest!!.entryFeature
            launchFeature(entryFeatureId)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun registerGlobalFunctions(engine: AppJSEngine) {
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

            val feature = Feature(featureId, featureDef, engine)
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

    fun close() {
        _currentFeature.value?.close()
    }
}
