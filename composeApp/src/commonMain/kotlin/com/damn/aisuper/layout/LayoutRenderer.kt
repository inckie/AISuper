package com.damn.aisuper.layout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

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

/**
 * Resolve dynamic children from a JsonElement value.
 * Supports JsonArray (direct widget list) or JsonPrimitive string (legacy JSON.stringify).
 */
private fun resolveDynamicWidgets(value: JsonElement?): List<Widget> {
    if (value == null) return emptyList()
    return when (value) {
        is JsonArray -> parseWidgetsFromJsonArray(value)
        is JsonPrimitive -> {
            val str = value.contentOrNull
            if (!str.isNullOrBlank()) parseWidgets(str) else emptyList()
        }
        else -> emptyList()
    }
}

/**
 * Helper to extract a string from a JsonElement (returns null if not a string primitive).
 */
private fun JsonElement.stringOrNull(): String? {
    return try {
        this.jsonPrimitive.contentOrNull
    } catch (_: Exception) {
        null
    }
}

@Composable
fun RenderWidget(
    widget: Widget,
    values: Map<String, JsonElement>,
    onValueChange: (String, String) -> Unit,
    onAction: (String, List<JsonElement>) -> Unit,
    onModuleCommand: (String, String, String, List<JsonElement>) -> Unit,
    modifier: Modifier = Modifier
) {
    when (widget) {
        is ColumnWidget -> {
            var columnModifier = modifier.then(widget.layoutModifier())
            if (widget.isScrollable) {
                columnModifier = columnModifier.verticalScroll(rememberScrollState())
            }
            Column(modifier = columnModifier) {
                widget.children.forEach { child ->
                    RenderWidget(child, values, onValueChange, onAction, onModuleCommand, childModifier(child))
                }

                // Render dynamic children if any
                if (widget.dynamicChildrenId != null) {
                    val dynamicWidgets = resolveDynamicWidgets(values[widget.dynamicChildrenId])
                    dynamicWidgets.forEach { child ->
                        RenderWidget(child, values, onValueChange, onAction, onModuleCommand, childModifier(child))
                    }
                }
            }
        }
        is RowWidget -> {
            var rowModifier = modifier.then(widget.layoutModifier())
            if (widget.isScrollable) {
                rowModifier = rowModifier.verticalScroll(rememberScrollState())
            }
            Row(modifier = rowModifier) {
                widget.children.forEach { child ->
                    RenderWidget(child, values, onValueChange, onAction, onModuleCommand, childModifier(child))
                }
            }
        }
        is TextWidget -> {
            val displayText = if (widget.id != null && values.containsKey(widget.id)) {
                values[widget.id]?.stringOrNull() ?: widget.text
            } else {
                widget.text
            }
            Text(text = displayText, modifier = modifier.then(widget.layoutModifier()))
        }
        is TextFieldWidget -> {
            val value = if (widget.id != null) values[widget.id]?.stringOrNull() ?: "" else ""
            TextField(
                value = value,
                modifier = modifier.then(widget.layoutModifier()),
                onValueChange = { newValue ->
                    if (widget.id != null) {
                        onValueChange(widget.id, newValue)
                    }
                },
                placeholder = { Text(widget.hint) }
            )
        }
        is ButtonWidget -> {
            Button(
                onClick = { onAction(widget.action, widget.actionArgs) },
                modifier = modifier.then(widget.layoutModifier())
            ) {
                Text(widget.text)
            }
        }
        is ImageWidget -> {
            val imgModifier = if (widget.fillMaxWidth) {
                modifier.then(widget.layoutModifier()).fillMaxWidth().height(200.dp)
            } else {
                modifier.then(widget.layoutModifier()).width(64.dp).height(64.dp)
            }
            AsyncImage(
                model = widget.url,
                contentDescription = widget.description,
                modifier = imgModifier
            )
        }
        is AudioPlayerWidget -> {
            val prefix = "${widget.player}.media"
            val phase = values["$prefix.state"]?.stringOrNull() ?: "idle"
            val position = values["$prefix.positionMs"]?.stringOrNull() ?: "0"

            Column(modifier = modifier.then(widget.layoutModifier())) {
                Text("${widget.title}: $phase")
                Text("Position: ${position}ms")
                Row {
                    Button(onClick = { onModuleCommand("audioPlayer", widget.player, "play", emptyList()) }) {
                        Text("Play")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onModuleCommand("audioPlayer", widget.player, "pause", emptyList()) }) {
                        Text("Pause")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onModuleCommand("audioPlayer", widget.player, "stop", emptyList()) }) {
                        Text("Stop")
                    }
                }
            }
        }
    }
}

private fun Widget.layoutModifier(): Modifier {
    return Modifier
        .let { if (fillMaxSize) it.fillMaxSize() else it }
        .let { if (fillMaxWidth) it.fillMaxWidth() else it }
}

private fun ColumnScope.childModifier(widget: Widget): Modifier {
    return widget.weight?.let { Modifier.weight(it) } ?: Modifier
}

private fun RowScope.childModifier(widget: Widget): Modifier {
    return widget.weight?.let { Modifier.weight(it) } ?: Modifier
}

