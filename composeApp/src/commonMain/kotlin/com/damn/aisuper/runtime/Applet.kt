package com.damn.aisuper.runtime

import com.damn.aisuper.engine.AppJSEngine
import com.damn.aisuper.layout.LayoutRoot
import com.damn.aisuper.layout.parseLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.compose.resources.ExperimentalResourceApi
import aisuper.composeapp.generated.resources.Res

class Applet(
    private val engine: AppJSEngine
) {
    private val _layoutRoot = MutableStateFlow<LayoutRoot?>(null)
    val layoutRoot = _layoutRoot.asStateFlow()

    private val _values = MutableStateFlow<Map<String, String>>(emptyMap())
    val values = _values.asStateFlow()

    private var scriptContent: String = ""

    @OptIn(ExperimentalResourceApi::class)
    suspend fun load(layoutPath: String, scriptPath: String) {
        try {
            // Register getValue function for the script to use
            engine.registerFunction("getValue") { args ->
                val key = args.firstOrNull() ?: return@registerFunction ""
                // key might assume string format, simple cleaning just in case
                val cleanKey = key.removeSurrounding("\"").removeSurrounding("'")

                // Return value from state map or empty
                _values.value[cleanKey] ?: ""
            }

            // Register setValue function for the script to use
            engine.registerFunction("setValue") { args ->
                if (args.size >= 2) {
                    val key = args[0].removeSurrounding("\"").removeSurrounding("'")
                    val value = args[1]
                    updateValue(key, value)
                }
                ""
            }

            val bytes = Res.readBytes(layoutPath)
            val jsonString = bytes.decodeToString()
            _layoutRoot.value = parseLayout(jsonString)

            val scriptBytes = Res.readBytes(scriptPath)
            scriptContent = scriptBytes.decodeToString()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateValue(id: String, value: String) {
        _values.update { it + (id to value) }
    }

    suspend fun handleAction(action: String, args: List<String> = emptyList()) {
        if (action.isNotEmpty()) {
            engine.execute(scriptContent, action, args)
        }
    }

    fun close() {
        engine.close()
    }
}
