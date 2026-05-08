package com.damn.aisuper.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive

private val renderJsonParser = Json { ignoreUnknownKeys = true }

fun parseDropdownOptionsFromJsonArray(array: JsonArray): List<DropdownOption> {
    return array.mapNotNull { element ->
        when (element) {
            is JsonPrimitive -> {
                val content = element.contentOrNull
                if (content.isNullOrBlank()) null else DropdownOption(content, content)
            }

            is JsonObject -> {
                val value = element["value"]?.stringOrNull()
                val label = element["label"]?.stringOrNull() ?: value
                if (value.isNullOrBlank() || label.isNullOrBlank()) null else DropdownOption(value, label)
            }

            else -> null
        }
    }
}

fun resolveDynamicWidgets(value: JsonElement?): List<Widget> {
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

fun resolveDropdownOptions(widget: DropdownWidget, values: Map<String, JsonElement>): List<DropdownOption> {
    val optionsFromValues = widget.optionsValueId
        ?.let { values[it] }
        ?.let {
            when (it) {
                is JsonArray -> parseDropdownOptionsFromJsonArray(it)
                is JsonPrimitive -> {
                    val str = it.contentOrNull
                    if (str.isNullOrBlank()) emptyList() else {
                        try {
                            val parsed = renderJsonParser.parseToJsonElement(str)
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

    return optionsFromValues.ifEmpty { widget.options }
}

fun JsonElement.stringOrNull(): String? {
    return try {
        this.jsonPrimitive.contentOrNull
    } catch (_: Exception) {
        null
    }
}

fun JsonElement.booleanOrNull(): Boolean? {
    return try {
        val primitive = this.jsonPrimitive
        primitive.booleanOrNull
            ?: primitive.contentOrNull?.trim()?.lowercase()?.let {
                when (it) {
                    "true" -> true
                    "false" -> false
                    else -> null
                }
            }
    } catch (_: Exception) {
        null
    }
}

fun JsonElement.floatOrNull(): Float? {
    return try {
        val primitive = this.jsonPrimitive
        primitive.floatOrNull ?: primitive.contentOrNull?.trim()?.toFloatOrNull()
    } catch (_: Exception) {
        null
    }
}

fun Widget.typeKey(): String {
    return when (this) {
        is ColumnWidget -> "Column"
        is RowWidget -> "Row"
        is TextWidget -> "Text"
        is TextFieldWidget -> "TextField"
        is ButtonWidget -> "Button"
        is ImageWidget -> "Image"
        is AudioPlayerWidget -> "AudioPlayer"
        is DropdownWidget -> "Dropdown"
        is SwitchWidget -> "Switch"
        is SpinnerWidget -> "Spinner"
        is ProgressWidget -> "Progress"
    }
}

fun resolveStyleRule(widget: Widget, styleSheet: StyleSheet?): StyleRule {
    if (styleSheet == null) return StyleRule()

    var merged = StyleRule().mergedWith(styleSheet.defaults[widget.typeKey()])
    for (name in widget.classes) {
        merged = merged.mergedWith(styleSheet.classes[name])
    }
    return applyTokenFallbacks(widget, merged, styleSheet.tokens)
}

private fun applyTokenFallbacks(widget: Widget, rule: StyleRule, tokens: StyleTokens): StyleRule {
    val needsActionColors = widget is ButtonWidget || widget is DropdownWidget || widget is SwitchWidget
    if (!needsActionColors) return rule

    if (!rule.containerColor.isNullOrBlank() && !rule.textColor.isNullOrBlank()) return rule

    val hasDestructiveClass = widget.classes.any {
        it.equals("destructive", ignoreCase = true) ||
            it.equals("danger", ignoreCase = true) ||
            it.equals("delete", ignoreCase = true)
    }

    val containerFromToken = if (hasDestructiveClass) {
        tokens.destructiveColor ?: tokens.values["destructiveColor"]
    } else {
        tokens.accentColor ?: tokens.values["accentColor"]
    }

    return if (containerFromToken.isNullOrBlank()) {
        rule
    } else {
        rule.copy(containerColor = rule.containerColor ?: containerFromToken)
    }
}

fun Modifier.applyStyleRule(style: StyleRule): Modifier {
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
        padH != null || padV != null -> next.padding(horizontal = (padH ?: 0).dp, vertical = (padV ?: 0).dp)
        else -> next
    }

    return next
}

fun Widget.layoutModifier(): Modifier {
    return Modifier
        .let { if (fillMaxSize) it.fillMaxSize() else it }
        .let { if (fillMaxWidth) it.fillMaxWidth() else it }
}

fun ColumnScope.childModifier(widget: Widget): Modifier {
    return widget.weight?.let { Modifier.weight(it) } ?: Modifier
}

fun RowScope.childModifier(widget: Widget): Modifier {
    return widget.weight?.let { Modifier.weight(it) } ?: Modifier
}

