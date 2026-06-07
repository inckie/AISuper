package com.damn.aisuper.layout.frontend.material3

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.damn.aisuper.layout.AudioPlayerWidget
import com.damn.aisuper.layout.ButtonWidget
import com.damn.aisuper.layout.ColumnWidget
import com.damn.aisuper.layout.DropdownWidget
import com.damn.aisuper.layout.ImageWidget
import com.damn.aisuper.layout.ProgressWidget
import com.damn.aisuper.layout.RowWidget
import com.damn.aisuper.layout.SpinnerWidget
import com.damn.aisuper.layout.StyleSheet
import com.damn.aisuper.layout.SwitchWidget
import com.damn.aisuper.layout.TextFieldWidget
import com.damn.aisuper.layout.TextWidget
import com.damn.aisuper.layout.Widget
import com.damn.aisuper.layout.applyStyleRule
import com.damn.aisuper.layout.booleanOrNull
import com.damn.aisuper.layout.childModifier
import com.damn.aisuper.layout.floatOrNull
import com.damn.aisuper.layout.layoutModifier
import com.damn.aisuper.layout.parseColorOrNull
import com.damn.aisuper.layout.resolveDropdownOptions
import com.damn.aisuper.layout.resolveDynamicWidgets
import com.damn.aisuper.layout.resolveStyleRule
import com.damn.aisuper.layout.stringOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

private val focusRegistry: MutableMap<String, FocusRequester> = mutableMapOf()


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
                    RenderWidget(child, values, styleSheet, onValueChange, onAction, onModuleCommand, childModifier(child))
                }

                if (widget.dynamicChildrenId != null) {
                    val dynamicWidgets = resolveDynamicWidgets(values[widget.dynamicChildrenId])
                    dynamicWidgets.forEach { child ->
                        RenderWidget(child, values, styleSheet, onValueChange, onAction, onModuleCommand, childModifier(child))
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
                    RenderWidget(child, values, styleSheet, onValueChange, onAction, onModuleCommand, childModifier(child))
                }
            }
        }

        is TextWidget -> {
            val widgetId = widget.id
            val displayText = if (widgetId != null && values.containsKey(widgetId)) {
                values[widgetId]?.stringOrNull() ?: widget.text
            } else {
                widget.text
            }

            val textColor = parseColorOrNull(style.textColor) ?: Color.Unspecified
            val fontSize = style.fontSize?.sp ?: TextUnit.Unspecified
            val textAlign = when ((style.textAlign ?: "").lowercase()) {
                "center" -> TextAlign.Center
                "right" -> TextAlign.Right
                "end" -> TextAlign.End
                "start" -> TextAlign.Start
                "justify" -> TextAlign.Justify
                else -> null
            }

            Text(
                text = displayText,
                color = textColor,
                fontSize = fontSize,
                textAlign = textAlign,
                modifier = modifier.then(widget.layoutModifier()).applyStyleRule(style)
            )
        }

        is TextFieldWidget -> {
            val widgetId = widget.id
            val value = if (widgetId != null) values[widgetId]?.stringOrNull() ?: "" else ""
            val focusRequester = remember { FocusRequester() }
            if (widgetId != null) {
                focusRegistry[widgetId] = focusRequester
            }

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

            var passwordVisible by remember { mutableStateOf(false) }

            val visualTransformation = if (widget.password && !passwordVisible) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            }

            val trailingIcon: @Composable (() -> Unit)? = if (widget.password) {
                {
                    androidx.compose.material3.TextButton(onClick = { passwordVisible = !passwordVisible }) {
                        Text(if (passwordVisible) "Hide" else "Show")
                    }
                }
            } else null

            TextField(
                value = value,
                singleLine = widget.singleLine,
                modifier = modifier.then(widget.layoutModifier()).applyStyleRule(style).focusRequester(focusRequester),
                onValueChange = { newValue ->
                    if (widgetId != null) {
                        onValueChange(widgetId, newValue)
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
                visualTransformation = visualTransformation,
                trailingIcon = trailingIcon,
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
            AsyncImage(model = imageModel, contentDescription = widget.description, modifier = imgModifier.applyStyleRule(style))
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
                    Button(onClick = { onModuleCommand("audioPlayer", widget.player, "play", emptyList()) }) {
                        Text("Play")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(onClick = { onModuleCommand("audioPlayer", widget.player, "pause", emptyList()) }) {
                        Text("Pause")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(onClick = { onModuleCommand("audioPlayer", widget.player, "stop", emptyList()) }) {
                        Text("Stop")
                    }
                }
            }
        }

        is DropdownWidget -> {
            val options = resolveDropdownOptions(widget, values)
            val widgetId = widget.id
            val selectedValue = if (widgetId != null) values[widgetId]?.stringOrNull() ?: "" else ""
            val selectedLabel = options.firstOrNull { it.value == selectedValue }?.label ?: selectedValue
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
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                )

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    containerColor = containerColor ?: Color.Unspecified
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                if (textColor != null) Text(option.label, color = textColor) else Text(option.label)
                            },
                            onClick = {
                                expanded = false
                                if (widgetId != null) {
                                    onValueChange(widgetId, option.value)
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

        is SwitchWidget -> {
            val widgetId = widget.id
            val checked = if (widgetId != null) {
                values[widgetId]?.booleanOrNull() ?: widget.checked
            } else {
                widget.checked
            }

            Row(modifier = modifier.then(widget.layoutModifier()).applyStyleRule(style)) {
                if (widget.text.isNotBlank()) {
                    val textColor = parseColorOrNull(style.textColor)
                    if (textColor != null) {
                        Text(widget.text, color = textColor, modifier = Modifier.weight(1f))
                    } else {
                        Text(widget.text, modifier = Modifier.weight(1f))
                    }
                }

                Switch(
                    checked = checked,
                    onCheckedChange = { newValue ->
                        if (widgetId != null) {
                            onValueChange(widgetId, newValue.toString())
                        }
                    }
                )
            }
        }

        is SpinnerWidget -> {
            val visible = widget.visibilityId
                ?.let { values[it]?.booleanOrNull() }
                ?: true

            if (visible) {
                CircularProgressIndicator(
                    modifier = modifier.then(widget.layoutModifier()).applyStyleRule(style)
                )
            }
        }

        is ProgressWidget -> {
            val resolvedProgress = widget.progressId
                ?.let { values[it]?.floatOrNull() }
                ?: widget.progress
            val indicatorModifier = modifier.then(widget.layoutModifier()).applyStyleRule(style)

            if (widget.indeterminate || resolvedProgress == null) {
                LinearProgressIndicator(modifier = indicatorModifier)
            } else {
                val clamped = resolvedProgress.coerceIn(0f, 1f)
                LinearProgressIndicator(progress = { clamped }, modifier = indicatorModifier)
            }
        }
    }
}

