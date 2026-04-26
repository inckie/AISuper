package com.damn.aisuper.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private val jsonParser = Json { ignoreUnknownKeys = true }

// Simple registry for focus requesters by widget id. This allows IME Next to move focus to
// the target field identified by `nextFocusId`.
private val focusRegistry: MutableMap<String, FocusRequester> = mutableMapOf()

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

private fun parseDropdownOptionsFromJsonArray(array: JsonArray): List<DropdownOption> {
    return array.mapNotNull { element ->
        when (element) {
            is JsonPrimitive -> {
                val content = element.contentOrNull
                if (content.isNullOrBlank()) null else DropdownOption(
                    value = content,
                    label = content
                )
            }

            is JsonObject -> {
                val value = element["value"]?.stringOrNull()
                val label = element["label"]?.stringOrNull() ?: value
                if (value.isNullOrBlank() || label.isNullOrBlank()) null else DropdownOption(
                    value = value,
                    label = label
                )
            }

            else -> null
        }
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

private fun resolveDropdownOptions(
    widget: DropdownWidget,
    values: Map<String, JsonElement>
): List<DropdownOption> {
    val optionsFromValues = widget.optionsValueId
        ?.let { values[it] }
        ?.let {
            when (it) {
                is JsonArray -> parseDropdownOptionsFromJsonArray(it)
                is JsonPrimitive -> {
                    val str = it.contentOrNull
                    if (str.isNullOrBlank()) emptyList() else {
                        try {
                            val parsed = jsonParser.parseToJsonElement(str)
                            if (parsed is JsonArray) parseDropdownOptionsFromJsonArray(parsed) else emptyList()
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                }

                else -> emptyList()
            }
        }
        ?: emptyList()

    return if (optionsFromValues.isNotEmpty()) optionsFromValues else widget.options
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



private fun Widget.typeKey(): String {
    return when (this) {
        is ColumnWidget -> "Column"
        is RowWidget -> "Row"
        is TextWidget -> "Text"
        is TextFieldWidget -> "TextField"
        is ButtonWidget -> "Button"
        is ImageWidget -> "Image"
        is AudioPlayerWidget -> "AudioPlayer"
        is DropdownWidget -> "Dropdown"
    }
}

private fun resolveStyleRule(widget: Widget, styleSheet: StyleSheet?): StyleRule {
    if (styleSheet == null) return StyleRule()

    var merged = StyleRule().mergedWith(styleSheet.defaults[widget.typeKey()])
    for (name in widget.classes) {
        merged = merged.mergedWith(styleSheet.classes[name])
    }
    return merged
}

private fun Modifier.applyStyleRule(style: StyleRule): Modifier {
    var next = this

    val radius = (style.cornerRadius ?: 0).dp
    val hasBackground = !style.backgroundColor.isNullOrBlank()
    val backgroundColor = parseColorOrNull(style.backgroundColor)
    if (hasBackground && backgroundColor != null) {
        if (style.cornerRadius != null) {
            next = next.clip(RoundedCornerShape(radius))
        }
        next = next.background(backgroundColor)
    } else if (style.cornerRadius != null) {
        next = next.clip(RoundedCornerShape(radius))
    }

    val paddingAll = style.padding
    val padH = style.paddingHorizontal
    val padV = style.paddingVertical
    next = when {
        paddingAll != null -> next.padding(paddingAll.dp)
        padH != null || padV != null -> next.padding(
            horizontal = (padH ?: 0).dp,
            vertical = (padV ?: 0).dp
        )

        else -> next
    }

    return next
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenderWidget(
    widget: Widget,
    values: Map<String, JsonElement>,
    styleSheet: StyleSheet? = null,
    onValueChange: (String, String) -> Unit,
    onAction: (String, List<JsonElement>) -> Unit,
    onModuleCommand: (String, String, String, List<JsonElement>) -> Unit,
    modifier: Modifier = Modifier
) {
    val style = resolveStyleRule(widget, styleSheet)

    when (widget) {
        is ColumnWidget -> {
            var columnModifier = modifier.then(widget.layoutModifier()).applyStyleRule(style)
            if (widget.isScrollable) {
                columnModifier = columnModifier.verticalScroll(rememberScrollState())
            }
            Column(modifier = columnModifier) {
                widget.children.forEach { child ->
                    RenderWidget(
                        child,
                        values,
                        styleSheet,
                        onValueChange,
                        onAction,
                        onModuleCommand,
                        childModifier(child)
                    )
                }

                // Render dynamic children if any
                if (widget.dynamicChildrenId != null) {
                    val dynamicWidgets = resolveDynamicWidgets(values[widget.dynamicChildrenId])
                    dynamicWidgets.forEach { child ->
                        RenderWidget(
                            child,
                            values,
                            styleSheet,
                            onValueChange,
                            onAction,
                            onModuleCommand,
                            childModifier(child)
                        )
                    }
                }
            }
        }

        is RowWidget -> {
            var rowModifier = modifier.then(widget.layoutModifier()).applyStyleRule(style)
            if (widget.isScrollable) {
                rowModifier = rowModifier.verticalScroll(rememberScrollState())
            }
            Row(modifier = rowModifier) {
                widget.children.forEach { child ->
                    RenderWidget(
                        child,
                        values,
                        styleSheet,
                        onValueChange,
                        onAction,
                        onModuleCommand,
                        childModifier(child)
                    )
                }
            }
        }

        is TextWidget -> {
            val displayText = if (widget.id != null && values.containsKey(widget.id)) {
                values[widget.id]?.stringOrNull() ?: widget.text
            } else {
                widget.text
            }
            val textColor = parseColorOrNull(style.textColor)
            if (textColor != null) {
                Text(
                    text = displayText,
                    color = textColor,
                    modifier = modifier.then(widget.layoutModifier()).applyStyleRule(style)
                )
            } else {
                Text(
                    text = displayText,
                    modifier = modifier.then(widget.layoutModifier()).applyStyleRule(style)
                )
            }
        }

        is TextFieldWidget -> {
            val value = if (widget.id != null) values[widget.id]?.stringOrNull() ?: "" else ""
            // Create focus requester for this field and register it if we have an id
            val focusRequester = remember { FocusRequester() }
            if (widget.id != null) {
                focusRegistry[widget.id] = focusRequester
            }

            // Map string action to ImeAction
            val ime = when ((widget.imeAction ?: "").lowercase()) {
                "search" -> ImeAction.Search
                "next" -> ImeAction.Next
                "done" -> ImeAction.Done
                else -> ImeAction.Default
            }

            val textColor = parseColorOrNull(style.textColor)
            val containerColor = parseColorOrNull(style.containerColor ?: style.backgroundColor)
            val colors = if (textColor != null || containerColor != null) {
                TextFieldDefaults.colors(
                    focusedTextColor = textColor ?: Color.Unspecified,
                    unfocusedTextColor = textColor ?: Color.Unspecified,
                    disabledTextColor = textColor ?: Color.Unspecified,
                    focusedContainerColor = containerColor ?: Color.Unspecified,
                    unfocusedContainerColor = containerColor ?: Color.Unspecified,
                    disabledContainerColor = containerColor ?: Color.Unspecified
                )
            } else {
                TextFieldDefaults.colors()
            }

            TextField(
                value = value,
                singleLine = widget.singleLine,
                modifier = modifier.then(widget.layoutModifier()).applyStyleRule(style)
                    .focusRequester(focusRequester),
                onValueChange = { newValue ->
                    if (widget.id != null) {
                        onValueChange(widget.id, newValue)
                    }
                },
                placeholder = { Text(widget.hint) },
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ime),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        val actionName = widget.onImeAction ?: widget.imeAction
                        if (!actionName.isNullOrBlank()) {
                            onAction(actionName, emptyList())
                        }
                    },
                    onNext = {
                        // Prefer explicit next focus id
                        val target = widget.nextFocusId
                        if (!target.isNullOrBlank()) {
                            focusRegistry[target]?.requestFocus()
                        } else {
                            val actionName = widget.onImeAction
                            if (!actionName.isNullOrBlank()) {
                                onAction(actionName, emptyList())
                            }
                        }
                    },
                    onDone = {
                        val actionName = widget.onImeAction ?: widget.imeAction
                        if (!actionName.isNullOrBlank()) {
                            onAction(actionName, emptyList())
                        }
                    }
                ),
                colors = colors
            )
        }

        is ButtonWidget -> {
            val textColor = parseColorOrNull(style.textColor)
            val containerColor = parseColorOrNull(style.containerColor ?: style.backgroundColor)
            val buttonColors = if (textColor != null || containerColor != null) {
                ButtonDefaults.buttonColors(
                    containerColor = containerColor ?: Color.Unspecified,
                    contentColor = textColor ?: Color.Unspecified
                )
            } else {
                ButtonDefaults.buttonColors()
            }

            Button(
                onClick = { onAction(widget.action, widget.actionArgs) },
                modifier = modifier.then(widget.layoutModifier()).applyStyleRule(style),
                colors = buttonColors
            ) {
                Text(widget.text)
            }
        }

        is ImageWidget -> {
            val imageModel = widget.data?.takeIf { it.isNotBlank() } ?: widget.url
            val imgModifier = if (widget.fillMaxWidth) {
                modifier.then(widget.layoutModifier()).fillMaxWidth().height(200.dp)
            } else {
                modifier.then(widget.layoutModifier()).width(64.dp).height(64.dp)
            }
            AsyncImage(
                model = imageModel,
                contentDescription = widget.description,
                modifier = imgModifier.applyStyleRule(style)
            )
        }

        is AudioPlayerWidget -> {
            val prefix = "${widget.player}.media"
            val phase = values["$prefix.state"]?.stringOrNull() ?: "idle"
            val position = values["$prefix.positionMs"]?.stringOrNull() ?: "0"
            val textColor = parseColorOrNull(style.textColor)

            Column(modifier = modifier.then(widget.layoutModifier()).applyStyleRule(style)) {
                if (textColor != null) {
                    Text("${widget.title}: $phase", color = textColor)
                    Text("Position: ${position}ms", color = textColor)
                } else {
                    Text("${widget.title}: $phase")
                    Text("Position: ${position}ms")
                }
                Row {
                    Button(onClick = {
                        onModuleCommand(
                            "audioPlayer",
                            widget.player,
                            "play",
                            emptyList()
                        )
                    }) {
                        Text("Play")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(onClick = {
                        onModuleCommand(
                            "audioPlayer",
                            widget.player,
                            "pause",
                            emptyList()
                        )
                    }) {
                        Text("Pause")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(onClick = {
                        onModuleCommand(
                            "audioPlayer",
                            widget.player,
                            "stop",
                            emptyList()
                        )
                    }) {
                        Text("Stop")
                    }
                }
            }
        }

        is DropdownWidget -> {
            val options = resolveDropdownOptions(widget, values)
            val selectedValue = if (widget.id != null) {
                values[widget.id]?.stringOrNull() ?: ""
            } else {
                ""
            }
            val selectedLabel =
                options.firstOrNull { it.value == selectedValue }?.label ?: selectedValue

            var expanded by remember { mutableStateOf(false) }

            val textColor = parseColorOrNull(style.textColor)
            val containerColor = parseColorOrNull(style.containerColor ?: style.backgroundColor)
            val textFieldColors = if (textColor != null || containerColor != null) {
                TextFieldDefaults.colors(
                    focusedTextColor = textColor ?: Color.Unspecified,
                    unfocusedTextColor = textColor ?: Color.Unspecified,
                    disabledTextColor = textColor ?: Color.Unspecified,
                    focusedContainerColor = containerColor ?: Color.Unspecified,
                    unfocusedContainerColor = containerColor ?: Color.Unspecified,
                    disabledContainerColor = containerColor ?: Color.Unspecified
                )
            } else {
                TextFieldDefaults.colors()
            }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = modifier.then(widget.layoutModifier()).applyStyleRule(style)
            ) {
                TextField(
                    value = selectedLabel.ifBlank { widget.hint },
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    colors = textFieldColors,
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    }
                )

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    containerColor = containerColor ?: Color.Unspecified
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                if (textColor != null) {
                                    Text(option.label, color = textColor)
                                } else {
                                    Text(option.label)
                                }
                            },
                            onClick = {
                                expanded = false
                                if (widget.id != null) {
                                    onValueChange(widget.id, option.value)
                                }
                                val action = widget.onChangeAction
                                if (!action.isNullOrBlank()) {
                                    onAction(action, listOf(JsonPrimitive(option.value)))
                                }
                            }
                        )
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
