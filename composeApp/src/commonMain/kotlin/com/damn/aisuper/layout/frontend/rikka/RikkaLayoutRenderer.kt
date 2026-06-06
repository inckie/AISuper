package com.damn.aisuper.layout.frontend.rikka

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
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
import zed.rainxch.rikkaui.components.ui.button.Button
import zed.rainxch.rikkaui.components.ui.input.Input
import zed.rainxch.rikkaui.components.ui.select.Select
import zed.rainxch.rikkaui.components.ui.select.SelectOption
import zed.rainxch.rikkaui.components.ui.text.Text
import zed.rainxch.rikkaui.components.ui.text.TextVariant
import zed.rainxch.rikkaui.components.ui.toggle.Toggle

private val focusRegistry: MutableMap<String, FocusRequester> = mutableMapOf()


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
                variant = TextVariant.P,
                color = textColor,
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

            Input(
                value = value,
                modifier = modifier.then(widget.layoutModifier()).applyStyleRule(style).focusRequester(focusRequester),
                onValueChange = { newValue ->
                    if (widgetId != null) {
                        onValueChange(widgetId, newValue)
                    }
                },
                placeholder = widget.hint,
                singleLine = widget.singleLine,
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ime),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        val actionName = widget.onImeAction ?: widget.imeAction
                        if (!actionName.isNullOrBlank()) onAction(actionName, emptyList())
                    },
                    onNext = {
                        val target = widget.nextFocusId
                        if (!target.isNullOrBlank()) {
                            focusRegistry[target]?.requestFocus()
                        } else {
                            val actionName = widget.onImeAction
                            if (!actionName.isNullOrBlank()) onAction(actionName, emptyList())
                        }
                    },
                    onDone = {
                        val actionName = widget.onImeAction ?: widget.imeAction
                        if (!actionName.isNullOrBlank()) onAction(actionName, emptyList())
                    }
                )
            )
        }

        is ButtonWidget -> {
            Button(
                text = widget.text,
                onClick = { onAction(widget.action, widget.actionArgs) },
                modifier = modifier.then(widget.layoutModifier()).applyStyleRule(style)
            )
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
            val textColor = parseColorOrNull(style.textColor) ?: Color.Unspecified

            Column(modifier = modifier.then(widget.layoutModifier()).applyStyleRule(style)) {
                Text("${widget.title}: $phase", color = textColor)
                Text("Position: ${position}ms", color = textColor)
                Row {
                    Button(text = "Play", onClick = { onModuleCommand("audioPlayer", widget.player, "play", emptyList()) })
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(text = "Pause", onClick = { onModuleCommand("audioPlayer", widget.player, "pause", emptyList()) })
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(text = "Stop", onClick = { onModuleCommand("audioPlayer", widget.player, "stop", emptyList()) })
                }
            }
        }

        is DropdownWidget -> {
            val options = resolveDropdownOptions(widget, values)
            val widgetId = widget.id
            val selectedValue = if (widgetId != null) values[widgetId]?.stringOrNull() ?: "" else ""
            var selected by remember(selectedValue) { mutableStateOf(selectedValue) }

            Select(
                selectedValue = selected,
                options = options.map { SelectOption(value = it.value, label = it.label) },
                placeholder = widget.hint,
                modifier = modifier.then(widget.layoutModifier()).applyStyleRule(style),
                onValueChange = { newValue ->
                    selected = newValue
                    if (widgetId != null) {
                        onValueChange(widgetId, newValue)
                    }
                    val action = widget.onChangeAction
                    if (!action.isNullOrBlank()) {
                        onAction(action, listOf(JsonPrimitive(newValue)))
                    }
                }
            )
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
                    val textColor = parseColorOrNull(style.textColor) ?: Color.Unspecified
                    Text(widget.text, color = textColor, modifier = Modifier.weight(1f))
                }

                Toggle(
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


