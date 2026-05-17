package com.damn.aisuper.engine.quickjs

import com.damn.aisuper.engine.AppJSEngine
import com.damn.aisuper.engine.anyToJsonElement
import com.damn.aisuper.engine.jsonElementToJsLiteral
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.JsObject
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

class QuickJsKtEngine : AppJSEngine {

    private val quickJs = QuickJs.create(Dispatchers.Default)
    private var loadedScript: String? = null

    override suspend fun loadScript(script: String) {
        if (loadedScript != null) {
            if (loadedScript == script) return
            throw IllegalStateException("JS script is already loaded in this engine instance")
        }

        try {
            quickJs.evaluate<Unit>(script)
            loadedScript = script
        } catch (e: Exception) {
            logEngineError("evaluate", "", emptyList(), null, e)
            throw e
        }
    }

    override suspend fun callFunction(functionName: String, args: List<JsonElement>): JsonElement {
        var callString = ""
        try {
            if (loadedScript == null) {
                return JsonPrimitive("Error: Script is not loaded")
            }
            if (functionName.isEmpty()) return JsonNull

            val argsString = args.joinToString(",") { jsonElementToJsLiteral(it) }
            callString = "$functionName($argsString)"
            // Wrap in an async IIFE so Promise-returning functions are properly awaited.
            val awaitableCall = "await (async function(){ return $callString; })()"
            val result = quickJs.evaluate<Any?>(awaitableCall)
            return anyToJsonElement(result)
        } catch (e: Exception) {
            logEngineError("execute", functionName, args, callString, e)
            return JsonPrimitive("Error: ${e.message}")
        }
    }

    override suspend fun registerFunction(name: String, callback: (List<JsonElement>) -> JsonElement) {
        quickJs.function<Any?>(name) { args ->
            val jsonArgs = args.map { anyToJsonElement(it) }
            try {
                jsonElementToQuickJsAny(callback(jsonArgs))
            } catch (e: Exception) {
                println("[AISuper][JS][CallbackError] name=$name args=${safeArgs(jsonArgs)} message=${e.message}")
                e.printStackTrace()
                null
            }
        }
    }

    override suspend fun registerSuspendFunction(name: String, callback: suspend (List<JsonElement>) -> JsonElement) {
        quickJs.asyncFunction<Any?>(name) { args ->
            val jsonArgs = args.map { anyToJsonElement(it) }
            try {
                jsonElementToQuickJsAny(callback(jsonArgs))
            } catch (e: Exception) {
                println("[AISuper][JS][SuspendCallbackError] name=$name args=${safeArgs(jsonArgs)} message=${e.message}")
                e.printStackTrace()
                null
            }
        }
    }

    override fun close() {
        quickJs.close()
    }

    /**
     * Convert a [JsonElement] to a QuickJS-native value.
     *
     * Critically, [JsonObject] must become [JsObject] (not a plain Map) so that QuickJS
     * recognises it as a JavaScript object and correctly populates its properties.
     * A plain Kotlin Map passed back to QuickJS results in null property values because
     * QuickJS doesn't know how to reflect over an arbitrary Map.
     */
    private fun jsonElementToQuickJsAny(element: JsonElement): Any? = when (element) {
        is JsonNull -> null
        is JsonPrimitive -> when {
            element.isString -> element.content
            element.booleanOrNull != null -> element.booleanOrNull
            element.longOrNull != null -> element.longOrNull
            element.doubleOrNull != null -> element.doubleOrNull
            else -> element.content
        }
        is JsonArray -> element.map { jsonElementToQuickJsAny(it) }
        is JsonObject -> JsObject(element.mapValues { (_, v) -> jsonElementToQuickJsAny(v) })
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

    private fun safeArgs(args: List<JsonElement>): String =
        args.joinToString(prefix = "[", postfix = "]") { it.toString().replace("\n", " ").take(160) }
}

