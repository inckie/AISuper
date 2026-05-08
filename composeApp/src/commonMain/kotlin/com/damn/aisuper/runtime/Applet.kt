package com.damn.aisuper.runtime

import aisuper.composeapp.generated.resources.Res
import com.damn.aisuper.engine.AppJSEngine
import com.damn.aisuper.layout.frontend.LayoutFrontend
import com.damn.aisuper.layout.NamedStyleSheet
import com.damn.aisuper.layout.StyleSheet
import com.damn.aisuper.modules.impl.js.JsModuleRuntime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

    private val _currentStyleId = MutableStateFlow<String?>(null)

    private val _currentStyleSheet = MutableStateFlow<StyleSheet?>(null)
    val currentStyleSheet = _currentStyleSheet.asStateFlow()

    private val _currentFramework = MutableStateFlow<String>(LayoutFrontend.Rikka.name)
    val currentFramework = _currentFramework.asStateFlow()

    private var manifest: AppletManifest? = null

    /** Isolated JS runtimes for each declared jsModule. Keyed by module id. */
    private val jsModuleRuntimes = mutableMapOf<String, JsModuleRuntime>()
    private val stylesById = linkedMapOf<String, NamedStyleSheet>()

    @OptIn(ExperimentalResourceApi::class)
    suspend fun loadApplet(manifestPath: String) {
        try {
            val bytes = Res.readBytes(manifestPath)
            val jsonString = bytes.decodeToString()

            val json = Json { ignoreUnknownKeys = true }
            manifest = json.decodeFromString<AppletManifest>(jsonString)

            loadStyleSheets(json)

            // Build unified JS module definition map:
            // 1. Start with applet-level jsModules declarations (shared / pre-declared).
            // 2. Merge in inline jsModule entries from feature module lists (feature-inline wins on name conflict).
            jsModuleRuntimes.values.forEach { it.close() }
            jsModuleRuntimes.clear()

            val appletLevelDefs: Map<String, JsModuleDefinition> = manifest!!.jsModules

            val featureInlineDefs: Map<String, JsModuleDefinition> = manifest!!.features.values
                .flatMap { it.modules }
                .filter { it.type.equals("jsModule", ignoreCase = true) && !it.script.isNullOrBlank() }
                .distinctBy { it.name }
                .associate { it.name to JsModuleDefinition(script = it.script!!, name = it.name) }

            val allJsModuleDefs: Map<String, JsModuleDefinition> = appletLevelDefs + featureInlineDefs

            allJsModuleDefs.forEach { (moduleId, moduleDef) ->
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

    @OptIn(ExperimentalResourceApi::class)
    private suspend fun loadStyleSheets(json: Json) {
        stylesById.clear()

        manifest?.styles?.forEach { (styleId, definition) ->
            try {
                val bytes = Res.readBytes(definition.file)
                val text = bytes.decodeToString()
                val sheet = json.decodeFromString<StyleSheet>(text)
                val displayName = definition.name ?: sheet.name ?: styleId
                stylesById[styleId] = NamedStyleSheet(styleId, displayName, sheet)
            } catch (e: Exception) {
                println("[AISuper][Style] failed to load '$styleId' from ${definition.file}: ${e.message}")
            }
        }

        val preferred = manifest?.defaultStyle
        val selectedId = when {
            !preferred.isNullOrBlank() && stylesById.containsKey(preferred) -> preferred
            stylesById.isNotEmpty() -> stylesById.keys.first()
            else -> null
        }

        setCurrentTheme(selectedId)
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

        engine.registerFunction("getAvailableThemes") {
            JsonArray(stylesById.values.map { style ->
                buildJsonObject {
                    put("id", JsonPrimitive(style.id))
                    put("name", JsonPrimitive(style.name))
                }
            })
        }

        engine.registerFunction("getCurrentTheme") {
            JsonPrimitive(_currentStyleId.value ?: "")
        }

        engine.registerFunction("setCurrentTheme") { args ->
            val styleId = args.firstOrNull()?.let {
                try { it.jsonPrimitive.contentOrNull } catch (_: Exception) { null }
            }
            val changed = setCurrentTheme(styleId)
            JsonPrimitive(changed)
        }

        engine.registerFunction("getAvailableFrameworks") {
            JsonArray(
                LayoutFrontend.entries.map { frontend ->
                    buildJsonObject {
                        put("id", JsonPrimitive(frontend.name))
                        put("name", JsonPrimitive(frontend.name))
                    }
                }
            )
        }

        engine.registerFunction("getCurrentFramework") {
            JsonPrimitive(_currentFramework.value)
        }

        engine.registerFunction("setCurrentFramework") { args ->
            val frameworkId = args.firstOrNull()?.let {
                try { it.jsonPrimitive.contentOrNull } catch (_: Exception) { null }
            }
            val changed = setCurrentFramework(frameworkId)
            JsonPrimitive(changed)
        }

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
    }

    private fun setCurrentTheme(styleId: String?): Boolean {
        if (styleId.isNullOrBlank()) {
            _currentStyleId.update { null }
            _currentStyleSheet.update { null }
            return true
        }

        val style = stylesById[styleId] ?: return false
        _currentStyleId.update { styleId }
        _currentStyleSheet.update { style.sheet }
        return true
    }

    private fun setCurrentFramework(frameworkId: String?): Boolean {
        if (frameworkId.isNullOrBlank()) {
            return false
        }

        // Validate framework ID
        if (LayoutFrontend.entries.none { it.name == frameworkId }) {
            return false
        }

        _currentFramework.update { frameworkId }
        return true
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
                appletJsModuleRuntimes = jsModuleRuntimes,
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

