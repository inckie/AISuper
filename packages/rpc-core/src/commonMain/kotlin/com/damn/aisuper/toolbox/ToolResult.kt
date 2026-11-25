package com.damn.aisuper.toolbox

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Result of a [Tool] invocation backed directly by [JsonElement] for lossless MCP / JS bridge transport.
 */
data class ToolResult(
    val value: JsonElement,
    val isError: Boolean = false
) {
    val isStructured: Boolean get() = value is JsonObject || value is JsonArray

    val asText: String?
        get() = (value as? JsonPrimitive)?.takeIf { it.isString }?.content

    companion object {
        fun ok(content: String = "OK"): ToolResult =
            ToolResult(JsonPrimitive(content), isError = false)

        fun json(value: JsonElement): ToolResult =
            ToolResult(value, isError = false)

        fun error(message: String): ToolResult =
            ToolResult(
                buildJsonObject { put("error", message) },
                isError = true
            )
    }
}
