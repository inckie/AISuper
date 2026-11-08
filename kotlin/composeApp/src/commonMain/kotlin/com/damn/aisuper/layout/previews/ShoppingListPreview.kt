package com.damn.aisuper.layout.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.damn.aisuper.layout.frontend.material3.RenderWidget
import com.damn.aisuper.layout.parseLayout
import com.damn.aisuper.util.Logger
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Multiplatform preview for the Shopping List layout.
 */
@Composable
@Preview(showBackground = true)
fun RenderWidgetPreviewShoppingList() {
    val jsonString = """
{
  "layout": {
    "type": "Column",
    "fillMaxSize": true,
    "children": [
      {
        "type": "Text",
        "text": "My Shopping List"
      },
      {
        "type": "Row",
        "fillMaxWidth": true,
        "children": [
          {
            "type": "TextField",
            "id": "item_input",
            "weight": 1.0,
            "hint": "Add an item..."
          },
          {
            "type": "Button",
            "text": "Add",
            "action": "addItem"
          }
        ]
      },
      {
        "type": "Column",
        "fillMaxWidth": true,
        "weight": 1.0,
        "dynamicChildrenId": "items_list",
        "isScrollable": true
      }
    ]
  }
}
"""

    val root = parseLayout(jsonString).layout

    val itemsList = buildJsonArray {
        add(buildJsonObject {
            put("type", "Row")
            put("fillMaxWidth", true)
            put("children", buildJsonArray {
                add(buildJsonObject {
                    put("type", "Switch")
                    put("id", "item_check_1781056438534")
                    put("text", "Bread")
                    put("checked", false)
                    put("weight", 1.0)
                })
                add(buildJsonObject {
                    put("type", "Button")
                    put("text", "Remove")
                    put("action", "deleteItem")
                    put("actionArgs", buildJsonArray { add(JsonPrimitive("1781056438534")) })
                })
            })
        })
        add(buildJsonObject {
            put("type", "Row")
            put("fillMaxWidth", true)
            put("children", buildJsonArray {
                add(buildJsonObject {
                    put("type", "Switch")
                    put("id", "item_check_1781056429030")
                    put("text", "Fish Filets")
                    put("checked", false)
                    put("weight", 1.0)
                })
                add(buildJsonObject {
                    put("type", "Button")
                    put("text", "Remove")
                    put("action", "deleteItem")
                    put("actionArgs", buildJsonArray { add(JsonPrimitive("1781056429030")) })
                })
            })
        })
    }

    val values: Map<String, JsonElement> = mapOf(
        "items_list" to itemsList
    )

    RenderWidget(
        widget = root,
        values = values,
        styleSheet = PreviewThemes.byId["light"] ?: PreviewThemes.default,
        onValueChange = { id, value -> Logger.d("Preview") { "onValueChange: $id -> $value" } },
        onAction = { name, args -> Logger.d("Preview") { "onAction: $name args=$args" } },
        onModuleCommand = { module, target, command, args -> Logger.d("Preview") { "onModuleCommand: $module.$command target=$target args=$args" } }
    )
}
