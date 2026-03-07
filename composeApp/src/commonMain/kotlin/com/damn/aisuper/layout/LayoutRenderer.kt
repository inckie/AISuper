package com.damn.aisuper.layout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

fun parseLayout(jsonString: String): LayoutRoot {
    val json = Json { ignoreUnknownKeys = true }
    return json.decodeFromString<LayoutRoot>(jsonString)
}

fun parseWidgets(jsonString: String): List<Widget> {
    val json = Json { ignoreUnknownKeys = true }
    return try {
        json.decodeFromString<List<Widget>>(jsonString)
    } catch (e: Exception) {
        emptyList()
    }
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
            val modifier = if (widget.isScrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier
            Column(modifier = modifier) {
                widget.children.forEach { child ->
                    RenderWidget(child, values, onValueChange, onAction)
                }

                // Render dynamic children if any
                if (widget.dynamicChildrenId != null) {
                    val dynamicContent = values[widget.dynamicChildrenId]
                    if (!dynamicContent.isNullOrBlank()) {
                        val dynamicWidgets = parseWidgets(dynamicContent)
                        dynamicWidgets.forEach { child ->
                            RenderWidget(child, values, onValueChange, onAction)
                        }
                    }
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
                        onValueChange(widget.id, newValue)
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
        is ImageWidget -> {
            AsyncImage(
                model = widget.url,
                contentDescription = widget.description,
                modifier = Modifier.fillMaxWidth().height(200.dp)
            )
        }
    }
}
