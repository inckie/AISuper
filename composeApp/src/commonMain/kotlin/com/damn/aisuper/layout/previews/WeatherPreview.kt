package com.damn.aisuper.layout.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.damn.aisuper.layout.frontend.material3.RenderWidget
import com.damn.aisuper.layout.parseLayout
import com.damn.aisuper.util.Logger
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Multiplatform preview for the Weather Dashboard layout.
 * Demonstrates a complex layout with hourly and daily forecast tables.
 */
@Composable
@Preview(showBackground = true)
fun RenderWidgetPreviewWeather() {
    val jsonString = """
{
  "layout": {
    "type": "Column",
    "classes": ["screen"],
    "fillMaxSize": true,
    "isScrollable": true,
    "children": [
      {
        "type": "Row",
        "fillMaxWidth": true,
        "children": [
          {
            "type": "Text",
            "text": "Weather Dashboard",
            "classes": ["section_title"],
            "weight": 1
          },
          {
            "type": "Dropdown",
            "id": "unit_selection",
            "hint": "Units",
            "onChangeAction": "onUnitsChanged",
            "options": [
              { "value": "metric", "label": "Metric" },
              { "value": "imperial", "label": "Imperial" }
            ],
            "weight": 1
          }
        ]
      },
      {
        "type": "Text",
        "id": "location_name",
        "text": "San Francisco",
        "classes": ["header_text"]
      },
      {
        "type": "Text",
        "id": "current_temp",
        "text": "--",
        "align": "center"
      },
      {
        "type": "Text",
        "id": "current_condition",
        "text": "--",
        "align": "center"
      },
      {
        "type": "Text",
        "text": "Hourly Forecast",
        "classes": ["section_title"]
      },
      {
        "type": "Table",
        "isScrollable": true,
        "columns": [
          { "width": 80 },
          { "width": 80 },
          { "width": 80 },
          { "width": 80 },
          { "width": 80 },
          { "width": 80 },
          { "width": 80 },
          { "width": 80 }
        ],
        "children": [
          {
            "type": "TableRow",
            "children": [
              { "type": "Text", "id": "h0_time", "text": "--", "classes": ["hourly_cell", "text-center"] },
              { "type": "Text", "id": "h1_time", "text": "--", "classes": ["hourly_cell", "hourly_cell_alt", "text-center"] },
              { "type": "Text", "id": "h2_time", "text": "--", "classes": ["hourly_cell", "text-center"] },
              { "type": "Text", "id": "h3_time", "text": "--", "classes": ["hourly_cell", "hourly_cell_alt", "text-center"] },
              { "type": "Text", "id": "h4_time", "text": "--", "classes": ["hourly_cell", "text-center"] },
              { "type": "Text", "id": "h5_time", "text": "--", "classes": ["hourly_cell", "hourly_cell_alt", "text-center"] },
              { "type": "Text", "id": "h6_time", "text": "--", "classes": ["hourly_cell", "text-center"] },
              { "type": "Text", "id": "h7_time", "text": "--", "classes": ["hourly_cell", "hourly_cell_alt", "text-center"] }
            ]
          },
          {
            "type": "TableRow",
            "children": [
              { "type": "Text", "id": "h0_emoji", "text": "--", "classes": ["hourly_cell", "text-center"] },
              { "type": "Text", "id": "h1_emoji", "text": "--", "classes": ["hourly_cell", "hourly_cell_alt", "text-center"] },
              { "type": "Text", "id": "h2_emoji", "text": "--", "classes": ["hourly_cell", "text-center"] },
              { "type": "Text", "id": "h3_emoji", "text": "--", "classes": ["hourly_cell", "hourly_cell_alt", "text-center"] },
              { "type": "Text", "id": "h4_emoji", "text": "--", "classes": ["hourly_cell", "text-center"] },
              { "type": "Text", "id": "h5_emoji", "text": "--", "classes": ["hourly_cell", "hourly_cell_alt", "text-center"] },
              { "type": "Text", "id": "h6_emoji", "text": "--", "classes": ["hourly_cell", "text-center"] },
              { "type": "Text", "id": "h7_emoji", "text": "--", "classes": ["hourly_cell", "hourly_cell_alt", "text-center"] }
            ]
          },
          {
            "type": "TableRow",
            "children": [
              { "type": "Text", "id": "h0_temp", "text": "--", "classes": ["hourly_cell", "text-center"] },
              { "type": "Text", "id": "h1_temp", "text": "--", "classes": ["hourly_cell", "hourly_cell_alt", "text-center"] },
              { "type": "Text", "id": "h2_temp", "text": "--", "classes": ["hourly_cell", "text-center"] },
              { "type": "Text", "id": "h3_temp", "text": "--", "classes": ["hourly_cell", "hourly_cell_alt", "text-center"] },
              { "type": "Text", "id": "h4_temp", "text": "--", "classes": ["hourly_cell", "text-center"] },
              { "type": "Text", "id": "h5_temp", "text": "--", "classes": ["hourly_cell", "hourly_cell_alt", "text-center"] },
              { "type": "Text", "id": "h6_temp", "text": "--", "classes": ["hourly_cell", "text-center"] },
              { "type": "Text", "id": "h7_temp", "text": "--", "classes": ["hourly_cell", "hourly_cell_alt", "text-center"] }
            ]
          }
        ]
      },
      {
        "type": "Text",
        "text": "5-Day Forecast",
        "classes": ["section_title"]
      },
      {
        "type": "Table",
        "fillMaxWidth": true,
        "columns": [
          { "weight": 5 },
          { "weight": 2 },
          { "weight": 3 }
        ],
        "children": [
          {
            "type": "TableRow",
            "classes": ["daily_row"],
            "children": [
              { "type": "Text", "id": "d0_day", "text": "--" },
              { "type": "Text", "id": "d0_emoji", "text": "--", "classes": ["text-center"] },
              { "type": "Text", "id": "d0_temp", "text": "--", "classes": ["text-right"] }
            ]
          },
          {
            "type": "TableRow",
            "classes": ["daily_row", "daily_row_alt"],
            "children": [
              { "type": "Text", "id": "d1_day", "text": "--" },
              { "type": "Text", "id": "d1_emoji", "text": "--", "classes": ["text-center"] },
              { "type": "Text", "id": "d1_temp", "text": "--", "classes": ["text-right"] }
            ]
          },
          {
            "type": "TableRow",
            "classes": ["daily_row"],
            "children": [
              { "type": "Text", "id": "d2_day", "text": "--" },
              { "type": "Text", "id": "d2_emoji", "text": "--", "classes": ["text-center"] },
              { "type": "Text", "id": "d2_temp", "text": "--", "classes": ["text-right"] }
            ]
          },
          {
            "type": "TableRow",
            "classes": ["daily_row", "daily_row_alt"],
            "children": [
              { "type": "Text", "id": "d3_day", "text": "--" },
              { "type": "Text", "id": "d3_emoji", "text": "--", "classes": ["text-center"] },
              { "type": "Text", "id": "d3_temp", "text": "--", "classes": ["text-right"] }
            ]
          },
          {
            "type": "TableRow",
            "classes": ["daily_row"],
            "children": [
              { "type": "Text", "id": "d4_day", "text": "--" },
              { "type": "Text", "id": "d4_emoji", "text": "--", "classes": ["text-center"] },
              { "type": "Text", "id": "d4_temp", "text": "--", "classes": ["text-right"] }
            ]
          }
        ]
      }
    ]
  }
}
"""

    val root = parseLayout(jsonString).layout

    val values: Map<String, JsonElement> = mapOf(
        "unit_selection" to JsonPrimitive("metric"),
        "location_name" to JsonPrimitive("My Location"),
        "current_temp" to JsonPrimitive("⛅ 26°C"),
        "current_condition" to JsonPrimitive("Partly Cloudy"),
        "h0_time" to JsonPrimitive("00:00"),
        "h0_emoji" to JsonPrimitive("⛅"),
        "h0_temp" to JsonPrimitive("26°"),
        "h1_time" to JsonPrimitive("03:00"),
        "h1_emoji" to JsonPrimitive("⛅"),
        "h1_temp" to JsonPrimitive("25°"),
        "h2_time" to JsonPrimitive("06:00"),
        "h2_emoji" to JsonPrimitive("☀️"),
        "h2_temp" to JsonPrimitive("24°"),
        "h3_time" to JsonPrimitive("09:00"),
        "h3_emoji" to JsonPrimitive("☀️"),
        "h3_temp" to JsonPrimitive("29°"),
        "h4_time" to JsonPrimitive("12:00"),
        "h4_emoji" to JsonPrimitive("⛅"),
        "h4_temp" to JsonPrimitive("30°"),
        "h5_time" to JsonPrimitive("15:00"),
        "h5_emoji" to JsonPrimitive("☀️"),
        "h5_temp" to JsonPrimitive("31°"),
        "h6_time" to JsonPrimitive("18:00"),
        "h6_emoji" to JsonPrimitive("☀️"),
        "h6_temp" to JsonPrimitive("30°"),
        "h7_time" to JsonPrimitive("21:00"),
        "h7_emoji" to JsonPrimitive("☀️"),
        "h7_temp" to JsonPrimitive("27°"),
        "d0_day" to JsonPrimitive("Monday"),
        "d0_emoji" to JsonPrimitive("⛅"),
        "d0_temp" to JsonPrimitive("24°C - 32°C"),
        "d1_day" to JsonPrimitive("Tuesday"),
        "d1_emoji" to JsonPrimitive("⛅"),
        "d1_temp" to JsonPrimitive("25°C - 30°C"),
        "d2_day" to JsonPrimitive("Wednesday"),
        "d2_emoji" to JsonPrimitive("⛅"),
        "d2_temp" to JsonPrimitive("25°C - 30°C"),
        "d3_day" to JsonPrimitive("Thursday"),
        "d3_emoji" to JsonPrimitive("⛅"),
        "d3_temp" to JsonPrimitive("27°C - 30°C"),
        "d4_day" to JsonPrimitive("Friday"),
        "d4_emoji" to JsonPrimitive("⛅"),
        "d4_temp" to JsonPrimitive("28°C - 30°C")
    )

    RenderWidget(
        widget = root,
        values = values,
        styleSheet = PreviewThemes.light,
        onValueChange = { id, value -> Logger.d("Preview") { "onValueChange: $id -> $value" } },
        onAction = { name, args -> Logger.d("Preview") { "onAction: $name args=$args" } },
        onModuleCommand = { module, target, command, args -> Logger.d("Preview") { "onModuleCommand: $module.$command target=$target args=$args" } }
    )
}
