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
        var callString = ""
        try {
            // 1. Evaluate the script definition into the context only if changed
            if (script != loadedScript) {
                try {
                    engine.evaluate(script)
                } catch (e: Exception) {
                    logEngineError(
                        stage = "evaluate",
                        functionName = functionName,
                        args = args,
                        callString = null,
                        error = e
                    )
                    return JsonPrimitive("Error: ${e.message}")
                }
                loadedScript = script
            }

            if (functionName.isEmpty()) return JsonNull

            // 2. Construct the function call with JSON-encoded arguments
            val argsString = args.joinToString(",") { jsonElementToJsLiteral(it) }
            callString = "$functionName($argsString)"

            // 3. Evaluate the function call — use compile().invoke() to get JsAny? directly
            val compiled = engine.compile(callString)
            val jsResult = compiled.invoke()

            return jsAnyToJsonElement(jsResult, runtime)
        } catch (e: Exception) {
            logEngineError(
                stage = "execute",
                functionName = functionName,
                args = args,
                callString = callString,
                error = e
            )
            return JsonPrimitive("Error: ${e.message}")
        }
    }

    override suspend fun registerFunction(
        name: String,
        callback: (List<JsonElement>) -> JsonElement
    ) {
        runtime.set(name.js, Callable { args ->
            val jsonArgs = args.map { jsAnyToJsonElement(it, this) }
            try {
                val result = callback(jsonArgs)
                jsonElementToJsAny(result, this)
            } catch (e: Exception) {
                println("[AISuper][JS][CallbackError] name=$name args=${safeArgs(jsonArgs)} message=${e.message}")
                e.printStackTrace()
                null
            }
        })
    }

    override suspend fun registerSuspendFunction(
        name: String,
        callback: suspend (List<JsonElement>) -> JsonElement
    ) {
        runtime.set(name.js, Callable { args ->
            val deferred = runtime.async {
                val jsonArgs = args.map { jsAnyToJsonElement(it, runtime) }
                try {
                    val result = callback(jsonArgs)
                    jsonElementToJsAny(result, runtime)
                } catch (e: Exception) {
                    println("[AISuper][JS][SuspendCallbackError] name=$name args=${safeArgs(jsonArgs)} message=${e.message}")
                    e.printStackTrace()
                    null
                }
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

    private fun logEngineError(
        stage: String,
        functionName: String,
        args: List<JsonElement>,
        callString: String?,
        error: Throwable
    ) {
        val fn = functionName.ifBlank { "<script-load>" }
        val callPreview = if (callString.isNullOrBlank()) "<none>" else callString.take(500)
        println("[AISuper][JS][EngineError] stage=$stage function=$fn args=${safeArgs(args)} call=$callPreview message=${error.message}")
        error.printStackTrace()
    }

    private fun safeArgs(args: List<JsonElement>): String {
        return args.joinToString(prefix = "[", postfix = "]") { it.toString().replace("\n", " ").take(160) }
    }
}