package com.damn.aisuper.layout.frontend.glance

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.GlanceModifier
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.damn.aisuper.layout.AudioPlayerWidget
import com.damn.aisuper.layout.ButtonWidget
import com.damn.aisuper.layout.ColumnWidget
import com.damn.aisuper.layout.DropdownWidget
import com.damn.aisuper.layout.ImageWidget
import com.damn.aisuper.layout.ProgressWidget
import com.damn.aisuper.layout.RowWidget
import com.damn.aisuper.layout.SpinnerWidget
import com.damn.aisuper.layout.SwitchWidget
import com.damn.aisuper.layout.TextFieldWidget
import com.damn.aisuper.layout.TextWidget
import com.damn.aisuper.layout.Widget
import com.damn.aisuper.layout.booleanOrNull
import com.damn.aisuper.layout.resolveDynamicWidgets
import com.damn.aisuper.layout.stringOrNull
import com.damn.aisuper.widget.KEY_ACTION
import com.damn.aisuper.widget.KEY_ARGS
import com.damn.aisuper.widget.WidgetActionCallback
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

/**
 * Glance-based layout renderer. Mirrors the Material3/Rikka RenderWidget pattern
 * but uses Glance composables so it can be used inside GlanceAppWidget.
 *
 * Unsupported interactive widgets (TextField, Dropdown, AudioPlayer) are silently skipped.
 * Buttons are rendered as tappable text rows.
 */
@OptIn(ExperimentalGlanceApi::class)
@androidx.compose.runtime.Composable
fun RenderWidget(
    widget: Widget,
    values: Map<String, JsonElement>,
    modifier: GlanceModifier = GlanceModifier
) {
    when (widget) {
        is ColumnWidget -> {
            Column(modifier = modifier.then(widget.glanceLayoutModifier())) {
                widget.children.forEach { child ->
                    RenderWidget(child, values)
                }
                if (widget.dynamicChildrenId != null) {
                    val dynamic = resolveDynamicWidgets(values[widget.dynamicChildrenId])
                    dynamic.forEach { child ->
                        RenderWidget(child, values)
                    }
                }
            }
        }

        is RowWidget -> {
            Row(modifier = modifier.then(widget.glanceLayoutModifier())) {
                widget.children.forEach { child ->
                    RenderWidget(child, values)
                }
            }
        }

        is TextWidget -> {
            val displayText = if (widget.id != null && values.containsKey(widget.id)) {
                values[widget.id]?.stringOrNull() ?: widget.text
            } else {
                widget.text
            }
            if (displayText.isNotBlank()) {
                Text(
                    text = displayText,
                    style = TextStyle(color = ColorProvider(Color.White), fontSize = 14.sp),
                    modifier = modifier.then(widget.glanceLayoutModifier())
                )
            }
        }

        is ButtonWidget -> {
            val argsJson = try {
                Json.encodeToString(JsonArray.serializer(), JsonArray(widget.actionArgs))
            } catch (_: Exception) { "[]" }

            Button(
                text = widget.text,
                onClick = actionRunCallback<WidgetActionCallback>(
                    actionParametersOf(
                        KEY_ACTION to widget.action,
                        KEY_ARGS   to argsJson
                    )
                ),
                modifier = modifier.then(widget.glanceLayoutModifier())
            )
        }

        is ImageWidget -> {
            // Glance Image needs resource-based providers; data-URI images not directly supported
            if (widget.description.isNotBlank()) {
                Text(
                    text = "[Image: ${widget.description}]",
                    style = TextStyle(color = ColorProvider(Color(0x80FFFFFF.toInt())), fontSize = 10.sp),
                    modifier = modifier
                )
            }
        }

        is SpinnerWidget -> {
            val visible = widget.visibilityId
                ?.let { values[it]?.booleanOrNull() }
                ?: true
            if (visible) {
                // Glance has no built-in spinner; show a text indicator
                Text(
                    "Loading…",
                    style = TextStyle(color = ColorProvider(Color(0xFFFFCC80)), fontSize = 11.sp),
                    modifier = modifier
                )
            }
        }

        is ProgressWidget -> {
            Text(
                "Loading…",
                style = TextStyle(color = ColorProvider(Color(0x80FFFFFF.toInt())), fontSize = 10.sp),
                modifier = modifier
            )
        }

        // Interactive widgets not renderable in home screen widget context — skip
        is TextFieldWidget, is DropdownWidget, is SwitchWidget, is AudioPlayerWidget -> Unit
    }
}

/** Translate Widget fill/size fields into GlanceModifier. */
private fun Widget.glanceLayoutModifier(): GlanceModifier {
    var m: GlanceModifier = GlanceModifier
    if (fillMaxWidth) m = m.fillMaxWidth()
    if (fillMaxSize) m = m.fillMaxSize()
    return m
}
