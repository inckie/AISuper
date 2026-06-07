package com.damn.aisuper.layout

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive

private val sharedJsonParser = Json { ignoreUnknownKeys = true }

fun parseWidgets(jsonString: String): List<Widget> {
    return try {
        sharedJsonParser.decodeFromString<List<Widget>>(jsonString)
    } catch (_: Exception) {
        emptyList()
    }
}

fun parseWidgetsFromJsonArray(array: JsonArray): List<Widget> {
    return try {
        array.mapNotNull { element ->
            try {
                sharedJsonParser.decodeFromJsonElement(Widget.serializer(), element)
            } catch (_: Exception) {
                null
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun parseDropdownOptionsFromJsonArray(array: JsonArray): List<DropdownOption> =
    array.mapNotNull { element ->
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
                            val parsed = sharedJsonParser.parseToJsonElement(str)
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

fun resolveText(textOrId: String, values: Map<String, JsonElement>): String {
    val rawValue = textOrId
    if (rawValue.startsWith("{") && rawValue.endsWith("}")) {
        val key = rawValue.substring(1, rawValue.length - 1)
        val valueElement = values[key]
        if (valueElement != null) {
            return valueElement.stringOrNull() ?: valueElement.toString()
        }
    }
    return rawValue
}

fun Widget.typeKey(): String = when (this) {
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

fun resolveStyleRule(widget: Widget, styleSheet: StyleSheet?): StyleRule {
    if (styleSheet == null) return StyleRule()

    var merged = StyleRule().mergedWith(styleSheet.defaults[widget.typeKey()])
    for (name in widget.classes) {
        merged = merged.mergedWith(styleSheet.classes[name])
    }
    return applyTokenFallbacks(widget, merged, styleSheet.tokens)
}

fun applyTokenFallbacks(widget: Widget, rule: StyleRule, tokens: StyleTokens): StyleRule {
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
