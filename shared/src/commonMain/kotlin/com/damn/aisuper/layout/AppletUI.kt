package com.damn.aisuper.layout

import com.damn.aisuper.engine.AppJSEngine
import com.damn.aisuper.layout.frontend.LayoutFrontend
import com.damn.aisuper.runtime.AppletResourceLoader
import com.damn.aisuper.runtime.AppletManifest
import com.damn.aisuper.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Manages UI-related state: style sheets, framework selection,
 * and registers corresponding JS engine callbacks.
 */
class AppletUI {

    private val _currentStyleId = MutableStateFlow<String?>(null)

    private val _currentStyleSheet = MutableStateFlow<StyleSheet?>(null)
    val currentStyleSheet: StateFlow<StyleSheet?> = _currentStyleSheet.asStateFlow()

    private val _currentFramework = MutableStateFlow(LayoutFrontend.Rikka.name)
    val currentFramework: StateFlow<String> = _currentFramework.asStateFlow()

    private val stylesById = linkedMapOf<String, NamedStyleSheet>()

    suspend fun loadStyleSheets(
        json: Json,
        manifest: AppletManifest?,
        resourceLoader: AppletResourceLoader
    ) {
        stylesById.clear()

        manifest?.styles?.forEach { (styleId, definition) ->
            try {
                val bytes = resourceLoader.readBytes(definition.file)
                val text = bytes.decodeToString()
                val sheet = json.decodeFromString<StyleSheet>(text)
                val displayName = definition.name ?: sheet.name ?: styleId
                stylesById[styleId] = NamedStyleSheet(styleId, displayName, sheet)
            } catch (e: Exception) {
                Logger.e("Style") { "failed to load '$styleId' from ${definition.file}: ${e.message}" }
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

    fun setCurrentTheme(styleId: String?): Boolean {
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

    fun setCurrentFramework(frameworkId: String?): Boolean {
        if (frameworkId.isNullOrBlank()) return false
        if (LayoutFrontend.entries.none { it.name == frameworkId }) return false
        _currentFramework.update { frameworkId }
        return true
    }

    /**
     * Registers UI-related JS functions on the given engine.
     */
    suspend fun registerFunctions(engine: AppJSEngine) {
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
    }
}
