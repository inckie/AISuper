package com.damn.aisuper.layout

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

private val jsonParser = Json { ignoreUnknownKeys = true }

fun parseLayout(jsonString: String): LayoutRoot {
    return jsonParser.decodeFromString<LayoutRoot>(jsonString)
}

fun parseWidgets(jsonString: String): List<Widget> {
    return try {
        jsonParser.decodeFromString<List<Widget>>(jsonString)
    } catch (_: Exception) {
        emptyList()
    }
}

/**
 * Decode a list of Widgets from a JsonArray of widget JsonElements.
 */
fun parseWidgetsFromJsonArray(array: JsonArray): List<Widget> {
    return try {
        array.mapNotNull { element ->
            try {
                jsonParser.decodeFromJsonElement(Widget.serializer(), element)
            } catch (_: Exception) {
                null
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}
