package com.damn.aisuper.toolbox

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Supported JSON Schema parameter types for FluentOS Toolbox tools.
 */
enum class ParameterType(val schemaType: String) {
    STRING("string"),
    INTEGER("integer"),
    NUMBER("number"),
    BOOLEAN("boolean"),
    OBJECT("object"),
    ARRAY("array");

    companion object {
        fun fromSchemaType(raw: String?): ParameterType = when (raw?.trim()?.lowercase()) {
            "integer", "int", "long" -> INTEGER
            "number", "float", "double" -> NUMBER
            "boolean", "bool" -> BOOLEAN
            "object", "map", "record" -> OBJECT
            "array", "list" -> ARRAY
            else -> STRING
        }
    }
}

/**
 * Signature specification of a single tool argument.
 */
data class ToolParameter(
    val name: String,
    val type: ParameterType,
    val description: String,
    val required: Boolean = true,
    val enumValues: List<String>? = null,
    val itemsType: ParameterType? = null
) {
    companion object {
        fun string(
            name: String,
            description: String,
            required: Boolean = true,
            enumValues: List<String>? = null
        ) = ToolParameter(name, ParameterType.STRING, description, required, enumValues)

        fun integer(name: String, description: String, required: Boolean = true) =
            ToolParameter(name, ParameterType.INTEGER, description, required)

        fun number(name: String, description: String, required: Boolean = true) =
            ToolParameter(name, ParameterType.NUMBER, description, required)

        fun boolean(name: String, description: String, required: Boolean = true) =
            ToolParameter(name, ParameterType.BOOLEAN, description, required)

        fun obj(name: String, description: String, required: Boolean = true) =
            ToolParameter(name, ParameterType.OBJECT, description, required)

        fun array(
            name: String,
            description: String,
            required: Boolean = true,
            itemsType: ParameterType? = null
        ) = ToolParameter(name, ParameterType.ARRAY, description, required, itemsType = itemsType)
    }
}

/**
 * Type-safe accessor over the parsed JSON argument object passed to [Tool.invoke].
 */
class ToolArguments(private val raw: JsonObject) {

    val keys: Set<String> get() = raw.keys

    fun asJsonObject(): JsonObject = raw

    fun has(name: String): Boolean = raw.containsKey(name) && raw[name] !is JsonNull

    fun string(name: String): String? {
        val elem = raw[name] ?: return null
        if (elem is JsonNull) return null
        return if (elem is JsonPrimitive) elem.contentOrNull else elem.toString()
    }

    fun stringOr(name: String, default: String): String = string(name) ?: default

    fun int(name: String): Int? {
        val prim = raw[name] as? JsonPrimitive ?: return null
        if (prim is JsonNull) return null
        return prim.intOrNull
            ?: prim.doubleOrNull?.toInt()
            ?: prim.contentOrNull?.trim()?.toIntOrNull()
    }

    fun long(name: String): Long? {
        val prim = raw[name] as? JsonPrimitive ?: return null
        if (prim is JsonNull) return null
        return prim.longOrNull
            ?: prim.doubleOrNull?.toLong()
            ?: prim.contentOrNull?.trim()?.toLongOrNull()
    }

    fun double(name: String): Double? {
        val prim = raw[name] as? JsonPrimitive ?: return null
        if (prim is JsonNull) return null
        return prim.doubleOrNull
            ?: prim.contentOrNull?.trim()?.toDoubleOrNull()
    }

    fun boolean(name: String): Boolean? {
        val prim = raw[name] as? JsonPrimitive ?: return null
        if (prim is JsonNull) return null
        return prim.booleanOrNull
            ?: prim.contentOrNull?.trim()?.lowercase()?.toBooleanStrictOrNull()
    }

    fun obj(name: String): JsonObject? = raw[name] as? JsonObject

    fun array(name: String): JsonArray? = raw[name] as? JsonArray

    fun raw(name: String): JsonElement? = raw[name]

    companion object {
        val EMPTY = ToolArguments(JsonObject(emptyMap()))

        fun fromJson(obj: JsonObject?): ToolArguments = ToolArguments(obj ?: JsonObject(emptyMap()))
    }
}
