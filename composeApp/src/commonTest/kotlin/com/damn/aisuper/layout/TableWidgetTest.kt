package com.damn.aisuper.layout

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TableWidgetTest {

    @Test
    fun testParseTableWithAllProperties() {
        val json = """
            {
              "layout": {
                "type": "Table",
                "id": "test_table",
                "fillMaxWidth": true,
                "isScrollable": true,
                "scrollDirection": "horizontal",
                "columns": [
                  { "width": 80 },
                  { "weight": 2.0 },
                  { "weight": 3.0 }
                ],
                "children": [
                  {
                    "type": "TableRow",
                    "children": [
                      { "type": "Text", "text": "Cell 0,0" },
                      { "type": "Text", "text": "Cell 0,1" },
                      { "type": "Text", "text": "Cell 0,2" }
                    ]
                  },
                  {
                    "type": "TableRow",
                    "children": [
                      { "type": "Text", "text": "Cell 1,0" },
                      { "type": "Text", "text": "Cell 1,1" },
                      { "type": "Text", "text": "Cell 1,2" }
                    ]
                  }
                ]
              }
            }
        """.trimIndent()

        val root = parseLayout(json)
        val table = assertIs<TableWidget>(root.layout)

        assertEquals("test_table", table.id)
        assertTrue(table.fillMaxWidth)
        assertTrue(table.isScrollable)
        assertEquals("horizontal", table.scrollDirection)
        assertEquals(3, table.columns.size)
        assertEquals(80, table.columns[0].width)
        assertNull(table.columns[0].weight)
        assertEquals(2.0f, table.columns[1].weight)
        assertEquals(3.0f, table.columns[2].weight)

        assertEquals(2, table.children.size)
        val row0 = assertIs<TableRowWidget>(table.children[0])
        assertEquals(3, row0.children.size)
        assertEquals("Cell 0,0", assertIs<TextWidget>(row0.children[0]).text)

        assertEquals("Table", table.typeKey())
        assertEquals("TableRow", row0.typeKey())
    }

    @Test
    fun testParseTableWithDefaults() {
        val json = """
            {
              "layout": {
                "type": "Table"
              }
            }
        """.trimIndent()

        val root = parseLayout(json)
        val table = assertIs<TableWidget>(root.layout)

        assertNull(table.id)
        assertEquals(false, table.fillMaxWidth)
        assertEquals(false, table.isScrollable)
        assertNull(table.scrollDirection)
        assertTrue(table.columns.isEmpty())
        assertTrue(table.children.isEmpty())
        assertTrue(table.rows.isEmpty())
        assertTrue(table.effectiveRows.isEmpty())
    }

    @Test
    fun testScrollableTableInsideScrollableColumn() {
        val json = """
            {
              "layout": {
                "type": "Column",
                "isScrollable": true,
                "fillMaxSize": true,
                "children": [
                  {
                    "type": "Text",
                    "text": "Header"
                  },
                  {
                    "type": "Table",
                    "isScrollable": true,
                    "columns": [
                      { "width": 72 },
                      { "width": 72 }
                    ],
                    "rows": [
                      {
                        "type": "TableRow",
                        "children": [
                          { "type": "Text", "id": "c0", "text": "A" },
                          { "type": "Text", "id": "c1", "text": "B" }
                        ]
                      }
                    ]
                  }
                ]
              }
            }
        """.trimIndent()

        val root = parseLayout(json)
        val column = assertIs<ColumnWidget>(root.layout)
        assertTrue(column.isScrollable)

        val table = assertIs<TableWidget>(column.children[1])
        assertTrue(table.isScrollable)
        assertEquals(2, table.columns.size)
        assertEquals(1, table.effectiveRows.size)
        val row = assertIs<TableRowWidget>(table.effectiveRows[0])
        assertEquals(2, row.children.size)
    }

    @Test
    fun testTableWithDynamicRows() {
        val json = """
            {
              "layout": {
                "type": "Table",
                "dynamicChildrenId": "dynamic_rows"
              }
            }
        """.trimIndent()

        val root = parseLayout(json)
        val table = assertIs<TableWidget>(root.layout)
        assertEquals("dynamic_rows", table.dynamicChildrenId)
        assertEquals("dynamic_rows", table.effectiveDynamicId)
    }

    @Test
    fun testWeatherDashboardTablesParsing() {
        val json = """
            {
              "layout": {
                "type": "Column",
                "classes": ["screen"],
                "fillMaxSize": true,
                "isScrollable": true,
                "children": [
                  {
                    "type": "Text",
                    "text": "Hourly Forecast"
                  },
                  {
                    "type": "Table",
                    "isScrollable": true,
                    "columns": [
                      { "width": 72 }, { "width": 72 }, { "width": 72 }, { "width": 72 },
                      { "width": 72 }, { "width": 72 }, { "width": 72 }, { "width": 72 }
                    ],
                    "children": [
                      {
                        "type": "TableRow",
                        "children": [
                          { "type": "Text", "id": "h0_time", "text": "--" },
                          { "type": "Text", "id": "h1_time", "text": "--" },
                          { "type": "Text", "id": "h2_time", "text": "--" },
                          { "type": "Text", "id": "h3_time", "text": "--" },
                          { "type": "Text", "id": "h4_time", "text": "--" },
                          { "type": "Text", "id": "h5_time", "text": "--" },
                          { "type": "Text", "id": "h6_time", "text": "--" },
                          { "type": "Text", "id": "h7_time", "text": "--" }
                        ]
                      }
                    ]
                  },
                  {
                    "type": "Text",
                    "text": "5-Day Forecast"
                  },
                  {
                    "type": "Table",
                    "fillMaxWidth": true,
                    "columns": [
                      { "weight": 5.0 },
                      { "weight": 2.0 },
                      { "weight": 5.0 },
                      { "weight": 3.0 }
                    ],
                    "children": [
                      {
                        "type": "TableRow",
                        "children": [
                          { "type": "Text", "id": "d0_day", "text": "--" },
                          { "type": "Text", "id": "d0_emoji", "text": "--" },
                          { "type": "Text", "id": "d0_rain", "text": "--" },
                          { "type": "Text", "id": "d0_temp", "text": "--" }
                        ]
                      }
                    ]
                  }
                ]
              }
            }
        """.trimIndent()

        val root = parseLayout(json)
        val column = assertIs<ColumnWidget>(root.layout)
        assertTrue(column.isScrollable)

        val hourlyTable = assertIs<TableWidget>(column.children[1])
        assertTrue(hourlyTable.isScrollable)
        assertEquals(8, hourlyTable.columns.size)
        assertEquals(72, hourlyTable.columns[0].width)

        val dailyTable = assertIs<TableWidget>(column.children[3])
        assertTrue(dailyTable.fillMaxWidth)
        assertEquals(4, dailyTable.columns.size)
        assertEquals(5f, dailyTable.columns[0].weight)
        assertEquals(2f, dailyTable.columns[1].weight)
    }
}
