package com.damn.aisuper.layout.frontend.glance

import android.annotation.SuppressLint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.glance.ButtonDefaults
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.LocalContext
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.ImageProvider
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.components.FilledButton
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
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
import com.damn.aisuper.layout.StyleSheet
import com.damn.aisuper.layout.SwitchWidget
import com.damn.aisuper.layout.TextFieldWidget
import com.damn.aisuper.layout.TextWidget
import com.damn.aisuper.layout.Widget
import com.damn.aisuper.layout.booleanOrNull
import com.damn.aisuper.layout.floatOrNull
import com.damn.aisuper.layout.parseColorOrNull
import com.damn.aisuper.layout.resolveDynamicWidgets
import com.damn.aisuper.layout.resolveStyleRule
import com.damn.aisuper.layout.stringOrNull
import com.damn.aisuper.widget.KEY_ACTION
import com.damn.aisuper.widget.KEY_ARGS
import com.damn.aisuper.widget.WidgetActionCallback
import com.damn.aisuper.widget.WidgetImageCache
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

private val DEFAULT_TEXT_COLOR  = Color.White
private val DEFAULT_BG_COLOR    = Color.Transparent
private val DEFAULT_BTN_COLOR   = Color(0xFF3D5AFE.toInt())

/**
 * Glance-based layout renderer. Mirrors the Material3/Rikka RenderWidget pattern
 * but uses Glance composables so it can be used inside GlanceAppWidget.
 *
 * Scrollable Column → LazyColumn (backed by RemoteViews ListView, supports scrolling).
 * Supports [StyleSheet] for colors, font size and padding via [resolveStyleRule].
 * Interactive widgets (TextField, Dropdown, AudioPlayer) are skipped.
 */
@Suppress("DEPRECATION")
@SuppressLint("RestrictedApi")
@OptIn(ExperimentalGlanceApi::class)
@androidx.compose.runtime.Composable
fun RenderWidget(
    widget: Widget,
    values: Map<String, JsonElement>,
    styleSheet: StyleSheet? = null,
    modifier: GlanceModifier = GlanceModifier
) {
    val style = resolveStyleRule(widget, styleSheet)

    when (widget) {
        is ColumnWidget -> {
            val allChildren: List<Widget> = buildList {
                addAll(widget.children)
                if (widget.dynamicChildrenId != null) {
                    addAll(resolveDynamicWidgets(values[widget.dynamicChildrenId]))
                }
            }
            val colMod = modifier
                .then(widget.glanceLayoutModifier())
                .then(style.toGlanceModifier())
            if (widget.isScrollable) {
                LazyColumn(modifier = colMod) {
                    items(allChildren) { child ->
                        RenderWidget(child, values, styleSheet)
                    }
                }
            } else {
                Column(modifier = colMod) {
                    allChildren.forEach { child ->
                        RenderWidget(child, values, styleSheet)
                    }
                }
            }
        }

        is RowWidget -> {
            Row(modifier = modifier.then(widget.glanceLayoutModifier()).then(style.toGlanceModifier())) {
                widget.children.forEach { child ->
                    RenderWidget(child, values, styleSheet)
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
                val textColor = parseColorOrNull(style.textColor) ?: DEFAULT_TEXT_COLOR
                val fontSize = (style.fontSize ?: 14).sp
                Text(
                    text = displayText,
                    style = TextStyle(color = ColorProvider(textColor), fontSize = fontSize),
                    modifier = modifier.then(widget.glanceLayoutModifier()).then(style.toGlanceModifier())
                )
            }
        }

        is ButtonWidget -> {
            val argsJson = try {
                Json.encodeToString(JsonArray.serializer(), JsonArray(widget.actionArgs))
            } catch (_: Exception) { "[]" }
            val bgColor = parseColorOrNull(style.containerColor ?: style.backgroundColor) ?: DEFAULT_BTN_COLOR
            val fgColor = parseColorOrNull(style.textColor) ?: Color.White
            FilledButton(
                text = widget.text,
                onClick = actionRunCallback<WidgetActionCallback>(
                    actionParametersOf(
                        KEY_ACTION to widget.action,
                        KEY_ARGS   to argsJson
                    )
                ),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = ColorProvider(bgColor),
                    contentColor = ColorProvider(fgColor)
                ),
                modifier = modifier.then(widget.glanceLayoutModifier()).then(style.toGlanceModifier())
            )
        }

        is ImageWidget -> {
            val url = widget.url.takeIf { it.isNotBlank() }
            val context = LocalContext.current
            if (url != null) {
                Image(
                    provider = ImageProvider(WidgetImageCache.contentUri(context, url)),
                    contentDescription = widget.description.takeIf { it.isNotBlank() },
                    contentScale = ContentScale.Fit,
                    modifier = modifier.then(widget.glanceLayoutModifier())
                )
            } else if (widget.description.isNotBlank()) {
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
                val textColor = parseColorOrNull(style.textColor) ?: Color(0xFFFFCC80.toInt())
                Text(
                    "Loading…",
                    style = TextStyle(color = ColorProvider(textColor), fontSize = 11.sp),
                    modifier = modifier
                )
            }
        }

        is ProgressWidget -> {
            val progress = widget.progressId?.let { values[it]?.floatOrNull() } ?: widget.progress
            val label = if (progress != null) "${"%.0f".format(progress * 100)}%" else "…"
            val textColor = parseColorOrNull(style.textColor) ?: Color(0x80FFFFFF.toInt())
            Text(
                "▶ $label",
                style = TextStyle(color = ColorProvider(textColor), fontSize = 10.sp),
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

/** Map [com.damn.aisuper.layout.StyleRule] properties into a GlanceModifier (padding, background). */
private fun com.damn.aisuper.layout.StyleRule.toGlanceModifier(): GlanceModifier {
    var m: GlanceModifier = GlanceModifier

    // Background color
    val bg = parseColorOrNull(backgroundColor)
    if (bg != null) m = m.background(ColorProvider(bg))

    // Padding
    val pad = padding
    val padH = paddingHorizontal
    val padV = paddingVertical
    m = when {
        pad != null -> m.padding(pad.dp)
        padH != null || padV != null ->
            m.padding(horizontal = (padH ?: 0).dp, vertical = (padV ?: 0).dp)
        else -> m
    }
    return m
}

// dp helpers for GlanceModifier.padding
private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())
