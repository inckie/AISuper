package com.damn.aisuper.engine

import io.github.alexzhirkevich.keight.Callable
import io.github.alexzhirkevich.keight.JSEngine
import io.github.alexzhirkevich.keight.JSRuntime
import io.github.alexzhirkevich.keight.js.js
import io.github.alexzhirkevich.keight.set
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

class KeightJSEngine : AppJSEngine {

    private val runtime = JSRuntime(Dispatchers.Default)
    private val engine = JSEngine(runtime)
    private var loadedScript: String? = null

    override suspend fun execute(
        script: String,
        functionName: String,
        args: List<JsonElement>
    ): JsonElement {
        try {
            // 1. Evaluate the script definition into the context only if changed
            if (script != loadedScript) {
                engine.evaluate(script)
                loadedScript = script
            }

            if (functionName.isEmpty()) return JsonNull

            // 2. Construct the function call with JSON-encoded arguments
            val argsString = args.joinToString(",") { jsonElementToJsLiteral(it) }
            val callString = "$functionName($argsString)"

            // 3. Evaluate the function call — use compile().invoke() to get JsAny? directly
            val script = engine.compile(callString)
            val jsResult = script.invoke()

            return jsAnyToJsonElement(jsResult, runtime)
        } catch (e: Exception) {
            e.printStackTrace()
            return JsonPrimitive("Error: ${e.message}")
        }
    }

    override suspend fun registerFunction(
        name: String,
        callback: (List<JsonElement>) -> JsonElement
    ) {
        runtime.set(name.js, Callable { args ->
            val jsonArgs = args.map { jsAnyToJsonElement(it, this) }
            val result = callback(jsonArgs)
            jsonElementToJsAny(result, this)
        })
    }

    override suspend fun registerSuspendFunction(
        name: String,
        callback: suspend (List<JsonElement>) -> JsonElement
    ) {
        runtime.set(name.js, Callable { args ->
            val deferred = runtime.async {
                val jsonArgs = args.map { jsAnyToJsonElement(it, runtime) }
                val result = callback(jsonArgs)
                jsonElementToJsAny(result, runtime)
            }
            deferred.js
        })
    }

    override fun close() {
        // Keight runtime might need cleanup if available, but for now just let GC handle it
    }

    /**
     * Convert a JsonElement to a JavaScript literal string for embedding in eval calls.
     */
    private fun jsonElementToJsLiteral(element: JsonElement): String {
        return when (element) {
            is JsonNull -> "null"
            is JsonPrimitive -> {
                if (element.isString) {
                    // Escape for JS string literal
                    "'" + element.content.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r") + "'"
                } else {
                    element.content // number or boolean literal
                }
            }
            else -> {
                // For arrays and objects, serialize as JSON and parse in JS
                "JSON.parse('${element.toString().replace("\\", "\\\\").replace("'", "\\'")}')"
            }
        }
    }
}