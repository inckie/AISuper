package com.damn.aisuper.engine

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@OptIn(ExperimentalUnsignedTypes::class)
internal fun anyToJsonElement(value: Any?): JsonElement = when (value) {
	null, Unit -> JsonNull
	is JsonElement -> value
	is String -> JsonPrimitive(value)
	is Boolean -> JsonPrimitive(value)
	is Int -> JsonPrimitive(value)
	is Long -> JsonPrimitive(value)
	is Double -> JsonPrimitive(value)
	is Float -> JsonPrimitive(value)
	is Number -> JsonPrimitive(value.toDouble())
	is ByteArray -> JsonArray(value.map { JsonPrimitive(it.toInt()) })
	is UByteArray -> JsonArray(value.map { JsonPrimitive(it.toInt()) })
	is Array<*> -> JsonArray(value.map { anyToJsonElement(it) })
	is Iterable<*> -> JsonArray(value.map { anyToJsonElement(it) })
	is Map<*, *> -> JsonObject(value.entries.associate { (k, v) ->
		(k?.toString() ?: "") to anyToJsonElement(v)
	})
	else -> JsonPrimitive(value.toString())
}

internal fun jsonElementToJsLiteral(element: JsonElement): String = when (element) {
	is JsonNull -> "null"
	is JsonPrimitive -> {
		if (element.isString) {
			"'" + element.content
				.replace("\\", "\\\\")
				.replace("'", "\\'")
				.replace("\n", "\\n")
				.replace("\r", "\\r") + "'"
		} else {
			element.content
		}
	}

	else -> "JSON.parse('${element.toString().replace("\\", "\\\\").replace("'", "\\'")}')"
}

