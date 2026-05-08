package com.damn.aisuper.layout.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.damn.aisuper.layout.parseLayout
import com.damn.aisuper.layout.material3.RenderWidget
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Multiplatform preview for the `form_layout.json` layout.
 * Embeds layout JSON and provides sample `formResult` data so the
 * dynamic area shows submission results in the preview.
 */
@Composable
@Preview(showBackground = true)
fun RenderWidgetPreviewForm() {
    val jsonString = """
{
  "layout": {
    "type": "Column",
    "fillMaxSize": true,
    "children": [
      {
        "type": "Row",
        "fillMaxWidth": true,
        "children": [
          { "type": "Button", "text": "<< Back", "action": "launch:menu" },
          { "type": "Text", "text": "Form Demo", "weight": 1 }
        ]
      },
      { "type": "TextField", "id": "first_field", "hint": "First value", "singleLine": true, "imeAction": "Next", "nextFocusId": "second_field" },
      { "type": "TextField", "id": "second_field", "hint": "Second value", "singleLine": true, "imeAction": "Done", "onImeAction": "submitForm" },
      { "type": "Row", "fillMaxWidth": true, "children": [ { "type": "Button", "text": "Submit", "action": "submitForm" } ] },
      { "type": "Column", "dynamicChildrenId": "formResult", "fillMaxWidth": true, "isScrollable": true, "weight": 1 }
    ]
  }
}
"""

    // Parse the embedded layout JSON. Do not catch parsing errors — allow preview to fail if JSON is invalid.
    val root = parseLayout(jsonString).layout

    // Sample submission result to show in preview
    val sampleResult = buildJsonArray {
        add(buildJsonObject { put("type", "Text"); put("text", "Form submitted") })
        add(buildJsonObject {
            put("type", "Column")
            put("children", buildJsonArray {
                add(buildJsonObject { put("type", "Text"); put("text", "First: Alice") })
                add(buildJsonObject { put("type", "Text"); put("text", "Second: Bob") })
            })
        })
    }

    val values: Map<String, JsonElement> = mapOf(
        "first_field" to JsonPrimitive("Alice"),
        "second_field" to JsonPrimitive("Bob"),
        "formResult" to sampleResult
    )

    RenderWidget(
        widget = root,
        values = values,
        styleSheet = PreviewThemes.byId["light"] ?: PreviewThemes.default,
        onValueChange = { id, value -> println("[Preview] onValueChange: $id -> $value") },
        onAction = { name, args -> println("[Preview] onAction: $name args=$args") },
        onModuleCommand = { module, target, command, args -> println("[Preview] onModuleCommand: $module.$command target=$target args=$args") }
    )
}
