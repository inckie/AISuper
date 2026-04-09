package com.damn.aisuper.runtime

import aisuper.composeapp.generated.resources.Res
import com.damn.aisuper.engine.AppJSEngine
import com.damn.aisuper.layout.LayoutRoot
import com.damn.aisuper.layout.parseLayout
import com.damn.aisuper.modules.HttpComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.ExperimentalResourceApi

class Feature(
    val id: String,
    private val definition: FeatureDefinition,
    private val engine: AppJSEngine
) {
    private val _layoutRoot = MutableStateFlow<LayoutRoot?>(null)
    val layoutRoot = _layoutRoot.asStateFlow()

    private val _values = MutableStateFlow<Map<String, JsonElement>>(emptyMap())
    val values = _values.asStateFlow()

    private var scriptContent: String = ""

    @OptIn(ExperimentalResourceApi::class)
    suspend fun load() {
        try {
            // Register getValue function for the script to use (SYNC)
            engine.registerFunction("getValue") { args ->
                val key = args.firstOrNull()?.jsonPrimitiveContentOrNull() ?: return@registerFunction JsonNull
                // Return value from state map or empty string
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

            // Register httpGet function (ASYNC)
            engine.registerSuspendFunction("httpGet") { args ->
                val url = args.firstOrNull()?.jsonPrimitiveContentOrNull() ?: return@registerSuspendFunction JsonPrimitive("")
                JsonPrimitive(HttpComponent.get(url))
            }

            // Layout
            val bytes = Res.readBytes(definition.layout)
            val jsonString = bytes.decodeToString()
            _layoutRoot.value = parseLayout(jsonString)

            // Script
            val scriptBytes = Res.readBytes(definition.script)
            scriptContent = scriptBytes.decodeToString()

            // Initial execution to load functions
            engine.execute(scriptContent, "", emptyList())

            // Call initialize if present
            try {
                engine.execute(scriptContent, "initialize", emptyList())
            } catch (e: Exception) {
                // Ignore if initialize is missing or fails
                println("Initialize failed or missing: ${e.message}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateValue(id: String, value: JsonElement) {
        _values.update { it + (id to value) }
    }

    suspend fun handleAction(action: String, args: List<JsonElement> = emptyList()) {
        if (action.isNotEmpty()) {
            engine.execute(scriptContent, action, args)
        }
    }

    fun close() {
        engine.close()
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

