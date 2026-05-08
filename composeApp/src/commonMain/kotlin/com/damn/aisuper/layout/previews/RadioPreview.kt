package com.damn.aisuper.layout.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.damn.aisuper.layout.parseLayout
import com.damn.aisuper.layout.frontend.material3.RenderWidget
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Simple preview that demonstrates how `RenderWidget` renders the radio UI.
 * It constructs a small widget tree similar to what `radio_script.js` expects
 * and supplies initial values produced by the script's `initialize()`.
 *
 * This preview lives next to `RenderWidget` so it can be used inside IDE previews
 * or invoked at runtime from a debug screen.
 */

@Composable
@Preview(showBackground = true)
fun RenderWidgetPreviewRadio() {
    // Load the layout JSON inline (multiplatform preview). This embeds the
    // current `radio_layout.json` content so previews work across targets.
    val jsonString = """
{
  "layout": {
    "type": "Column",
    "fillMaxSize": true,
    "children": [
      {
        "type": "Button",
        "text": "<< Back",
        "action": "launch:menu"
      },
      {
        "type": "Text",
        "text": "Find radio stations"
      },
      {
        "type": "Row",
        "fillMaxWidth": true,
        "children": [
          {
            "type": "TextField",
            "id": "search_query",
            "hint": "Station name, e.g. nightwave",
            "weight": 1,
            "singleLine": true,
            "imeAction": "Search",
            "onImeAction": "findStations"
          },
          {
            "type": "Button",
            "text": "Find",
            "action": "findStations"
          }
        ]
      },
      {
        "type": "Text",
        "id": "status_text",
        "text": "Type a station name and press Find"
      },
      {
        "type": "Column",
        "weight": 1,
        "isScrollable": true,
        "dynamicChildrenId": "stationList"
      },
      {
        "type": "Column",
        "dynamicChildrenId": "playerPanel"
      }
    ]
  }
}
"""

    val root = parseLayout(jsonString).layout

    // Values similar to what `initialize()` in radio_script.js sets
    val sampleStations = buildJsonArray {
        add(buildJsonObject {
            put("type", "Row")
            put("fillMaxWidth", true)
            put("children", buildJsonArray {
                add(buildJsonObject {
                    put("type", "Image")
                    put("url", "https://example.com/favicon1.png")
                    put("description", "Nightwave")
                })
                add(buildJsonObject {
                    put("type", "Column")
                    put("weight", 1)
                    put("children", buildJsonArray {
                        add(buildJsonObject { put("type", "Text"); put("text", "Nightwave") })
                        add(buildJsonObject { put("type", "Text"); put("text", "Synthwave & ambient") })
                    })
                })
                add(buildJsonObject {
                    put("type", "Button")
                    put("text", "Play")
                    put("action", "playStation")
                    put("actionArgs", buildJsonArray { add(JsonPrimitive("https://stream.example.com/nightwave.mp3")); add(JsonPrimitive("Nightwave")) })
                })
            })
        })

        add(buildJsonObject {
            put("type", "Row")
            put("fillMaxWidth", true)
            put("children", buildJsonArray {
                add(buildJsonObject {
                    put("type", "Image")
                    put("url", "https://example.com/favicon2.png")
                    put("description", "RetroFM")
                })
                add(buildJsonObject {
                    put("type", "Column")
                    put("weight", 1)
                    put("children", buildJsonArray {
                        add(buildJsonObject { put("type", "Text"); put("text", "RetroFM") })
                        add(buildJsonObject { put("type", "Text"); put("text", "Lo-Fi beats") })
                    })
                })
                add(buildJsonObject {
                    put("type", "Button")
                    put("text", "Play")
                    put("action", "playStation")
                    put("actionArgs", buildJsonArray { add(JsonPrimitive("https://stream.example.com/retrofm.mp3")); add(JsonPrimitive("RetroFM")) })
                })
            })
        })
    }

    val samplePlayerPanel = buildJsonArray {
        add(buildJsonObject { put("type", "Text"); put("text", "Now playing: Nightwave") })
        add(buildJsonObject {
            put("type", "AudioPlayer")
            put("player", "radioMain")
            put("title", "Player")
            put("fillMaxWidth", true)
        })
    }

    val values: Map<String, JsonElement> = mapOf(
        "search_query" to JsonPrimitive("nightwave"),
        "status_text" to JsonPrimitive("Type a station name and press Find"),
        "stationList" to sampleStations,
        "playerPanel" to samplePlayerPanel
    )

    RenderWidget(
        widget = root,
        values = values,
        styleSheet = PreviewThemes.byId["neon"] ?: PreviewThemes.default,
        onValueChange = { id, value -> println("[Preview] onValueChange: $id -> $value") },
        onAction = { name, args -> println("[Preview] onAction: $name args=$args") },
        onModuleCommand = { module, target, command, args -> println("[Preview] onModuleCommand: $module.$command target=$target args=$args") }
    )
}
