package com.damn.aisuper.layout

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import kotlinx.serialization.json.Json

fun parseLayout(jsonString: String): LayoutRoot {
    val json = Json { ignoreUnknownKeys = true }
    return json.decodeFromString<LayoutRoot>(jsonString)
}

@Composable
fun RenderWidget(
    widget: Widget,
    values: Map<String, String>,
    onValueChange: (String, String) -> Unit,
    onAction: (String) -> Unit
) {
    when (widget) {
        is ColumnWidget -> {
            Column {
                widget.children.forEach { child ->
                    RenderWidget(child, values, onValueChange, onAction)
                }
            }
        }
        is TextWidget -> {
            val displayText = if (widget.id != null && values.containsKey(widget.id)) {
                values[widget.id]!!
            } else {
                widget.text
            }
            Text(text = displayText)
        }
        is TextFieldWidget -> {
            val value = if (widget.id != null) values[widget.id] ?: "" else ""
            TextField(
                value = value,
                onValueChange = { newValue ->
                    if (widget.id != null) {
                        onValueChange(widget.id!!, newValue)
                    }
                },
                placeholder = { Text(widget.hint) }
            )
        }
        is ButtonWidget -> {
            Button(onClick = { onAction(widget.action) }) {
                Text(widget.text)
            }
        }
    }
}


