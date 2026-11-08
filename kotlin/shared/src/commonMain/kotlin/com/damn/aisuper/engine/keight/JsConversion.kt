package com.damn.aisuper.engine.keight

import com.damn.aisuper.util.Logger
import io.github.alexzhirkevich.keight.Callable
import io.github.alexzhirkevich.keight.ScriptRuntime
import io.github.alexzhirkevich.keight.Wrapper
import io.github.alexzhirkevich.keight.js.JsAny
import io.github.alexzhirkevich.keight.js.JsObject
import io.github.alexzhirkevich.keight.js.Undefined
import io.github.alexzhirkevich.keight.js.js
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.collections.iterator

/**
 * Convert a keight JsAny? to a kotlinx.serialization JsonElement.
 * Only JSON-compatible types are supported (strings, numbers, booleans, arrays, objects, null/undefined).
 * Functions and other JS-specific types are ignored (mapped to JsonNull).
 */
internal suspend fun jsAnyToJsonElement(value: JsAny?, runtime: ScriptRuntime): JsonElement {
    return when (value) {
        null, is Undefined -> JsonNull

        // Callable (functions) — not representable in JSON
        is Callable -> JsonNull

        // JS Promise — Keight may expose it as Job + Wrapper(Job/Deferred).
        is Job -> {
            val resolved: JsAny? = try {
                val deferred = when (value) {
                    is Deferred<*> -> value
                    is Wrapper<*> if value.value is Deferred<*> -> value.value as Deferred<*>
                    else -> null
                }

                if (deferred != null) {
                    @Suppress("UNCHECKED_CAST")
                    deferred.await() as JsAny?
                } else {
                    // Non-deferred Job: wait for completion, no resolved payload available.
                    value.join()
                    null
                }
            } catch (e: Exception) {
                Logger.e("JS", "PromiseError", throwable = e) { "Promise rejected: ${e.message}" }
                return JsonPrimitive("Error: Promise rejected: ${e.message}")
            }
            jsAnyToJsonElement(resolved, runtime)
        }

        // Unwrap wrappers to get the Kotlin value
        is Wrapper<*> -> {
            when (val unwrapped = value.value) {
                is String -> JsonPrimitive(unwrapped)
                is Boolean -> JsonPrimitive(unwrapped)
                is Long -> JsonPrimitive(unwrapped)
                is Int -> JsonPrimitive(unwrapped)
                is Double -> JsonPrimitive(unwrapped)
                is Float -> JsonPrimitive(unwrapped)
                is Number -> JsonPrimitive(unwrapped.toDouble())
                // Wrapper<MutableList<JsAny?>> — JS arrays
                is MutableList<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    val list = unwrapped as List<JsAny?>
                    JsonArray(list.map { jsAnyToJsonElement(it, runtime) })
                }
                // Nested wrapper
                is Wrapper<*> -> jsAnyToJsonElement(value as JsAny, runtime)
                else -> JsonPrimitive(unwrapped.toString())
            }
        }

        // JsObject (plain objects with properties) — convert to JsonObject
        is JsObject -> {
            val keys = value.keys(runtime, excludeSymbols = true, excludeNonEnumerables = true)

            // Special case: if this object has only "#text" key, unwrap it
            if (keys.size == 1) {
                val singleKey = keys[0]
                if (singleKey?.toString() == "#text") {
                    val textValue = value.get(singleKey, runtime)
                    return jsAnyToJsonElement(textValue, runtime)
                }
            }

            val map = mutableMapOf<String, JsonElement>()
            for (key in keys) {
                val keyStr = key?.toString() ?: continue
                val propValue = value.get(key, runtime)


                map[keyStr] = jsAnyToJsonElement(propValue, runtime)
            }
            JsonObject(map)
        }

        else -> {
            Logger.w("JS", "Conversion") { "Unknown type: ${value.toString().take(100)}" }
            JsonPrimitive(value.toString())
        }
    }
}

/**
 * Convert a kotlinx.serialization JsonElement to a keight JsAny.
 */
internal fun jsonElementToJsAny(element: JsonElement, runtime: ScriptRuntime): JsAny? {
    return when (element) {
        is JsonNull -> null
        is JsonPrimitive -> {
            if (element.isString) {
                element.content.js
            } else {
                // Try boolean
                element.content.toBooleanStrictOrNull()?.let { return it.js }
                // Try number
                element.content.toLongOrNull()?.let { return it.js }
                element.content.toDoubleOrNull()?.let { return it.js }
                // Fallback to string
                element.content.js
            }
        }
        is JsonArray -> {
            val list = element.map { jsonElementToJsAny(it, runtime) }.toMutableList()
            list.js
        }
        is JsonObject -> {
            val map = mutableMapOf<JsAny?, JsAny?>()
            for ((key, value) in element) {
                map[key.js] = jsonElementToJsAny(value, runtime)
            }
            runtime.makeObject(map)
        }
    }
}
