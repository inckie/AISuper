package com.damn.aisuper.layout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
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
    } catch (_: Exception) {
        emptyList()
    }
}

@Composable
fun RenderWidget(
    widget: Widget,
    values: Map<String, String>,
    onValueChange: (String, String) -> Unit,
    onAction: (String) -> Unit,
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
                    RenderWidget(child, values, onValueChange, onAction, childModifier(child))
                }

                // Render dynamic children if any
                if (widget.dynamicChildrenId != null) {
                    val dynamicContent = values[widget.dynamicChildrenId]
                    if (!dynamicContent.isNullOrBlank()) {
                        val dynamicWidgets = parseWidgets(dynamicContent)
                        dynamicWidgets.forEach { child ->
                            RenderWidget(child, values, onValueChange, onAction, childModifier(child))
                        }
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
                    RenderWidget(child, values, onValueChange, onAction, childModifier(child))
                }
            }
        }
        is TextWidget -> {
            val displayText = if (widget.id != null && values.containsKey(widget.id)) {
                values[widget.id]!!
            } else {
                widget.text
            }
            Text(text = displayText, modifier = modifier.then(widget.layoutModifier()))
        }
        is TextFieldWidget -> {
            val value = if (widget.id != null) values[widget.id] ?: "" else ""
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
                onClick = { onAction(widget.action) },
                modifier = modifier.then(widget.layoutModifier())
            ) {
                Text(widget.text)
            }
        }
        is ImageWidget -> {
            AsyncImage(
                model = widget.url,
                contentDescription = widget.description,
                modifier = modifier.then(widget.layoutModifier()).fillMaxWidth().height(200.dp)
            )
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

