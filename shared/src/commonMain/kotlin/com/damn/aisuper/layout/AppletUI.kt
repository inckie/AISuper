package com.damn.aisuper.layout

import com.damn.aisuper.engine.AppJSEngine
import com.damn.aisuper.layout.frontend.LayoutFrontend
import com.damn.aisuper.runtime.AppletResourceLoader
import com.damn.aisuper.runtime.AppletManifest
import com.damn.aisuper.storage.StateStorage
import com.damn.aisuper.storage.StorageScope
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
class AppletUI(private val persistentStorage: StateStorage) {

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

        // Restore saved theme
        val savedThemeId = persistentStorage.getString(StorageScope.Applet, KEY_THEME)
        val selectedId = if (!savedThemeId.isNullOrBlank() && stylesById.containsKey(savedThemeId)) {
            savedThemeId
        } else {
            if (!savedThemeId.isNullOrBlank()) {
                Logger.w("Style") { "Saved theme '$savedThemeId' no longer available, reverting to default." }
                persistentStorage.delete(StorageScope.Applet, KEY_THEME)
            }
            val preferred = manifest?.defaultStyle
            when {
                !preferred.isNullOrBlank() && stylesById.containsKey(preferred) -> preferred
                stylesById.isNotEmpty() -> stylesById.keys.first()
                else -> null
            }
        }

        applyTheme(selectedId)

        // Restore saved framework
        val savedFramework = persistentStorage.getString(StorageScope.Applet, KEY_FRAMEWORK)
        if (!savedFramework.isNullOrBlank()) {
            if (!applyFramework(savedFramework)) {
                Logger.w("UI") { "Saved framework '$savedFramework' no longer available, reverting to default." }
                persistentStorage.delete(StorageScope.Applet, KEY_FRAMEWORK)
            }
        }
    }

    /**
     * Internal apply methods that DON'T save to persistent storage to avoid redundant writes during load.
     */
    private fun applyTheme(styleId: String?): Boolean {
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

    private fun applyFramework(frameworkId: String?): Boolean {
        if (frameworkId.isNullOrBlank()) return false
        if (LayoutFrontend.entries.none { it.name == frameworkId }) return false
        _currentFramework.update { frameworkId }
        return true
    }

    suspend fun setCurrentTheme(styleId: String?): Boolean {
        val changed = applyTheme(styleId)
        if (changed) {
            if (styleId != null) {
                persistentStorage.putString(StorageScope.Applet, KEY_THEME, styleId)
            } else {
                persistentStorage.delete(StorageScope.Applet, KEY_THEME)
            }
        }
        return changed
    }

    suspend fun setCurrentFramework(frameworkId: String?): Boolean {
        val changed = applyFramework(frameworkId)
        if (changed) {
            if (frameworkId != null) {
                persistentStorage.putString(StorageScope.Applet, KEY_FRAMEWORK, frameworkId)
            } else {
                persistentStorage.delete(StorageScope.Applet, KEY_FRAMEWORK)
            }
        }
        return changed
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

        engine.registerSuspendFunction("setCurrentTheme") { args ->
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

        engine.registerSuspendFunction("setCurrentFramework") { args ->
            val frameworkId = args.firstOrNull()?.let {
                try { it.jsonPrimitive.contentOrNull } catch (_: Exception) { null }
            }
            val changed = setCurrentFramework(frameworkId)
            JsonPrimitive(changed)
        }
    }

    companion object {
        private const val KEY_THEME = "ui.theme_id"
        private const val KEY_FRAMEWORK = "ui.framework"
    }
}
