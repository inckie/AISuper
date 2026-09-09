package com.damn.aisuper.layout

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SliderWidgetTest {

    @Test
    fun testParseSliderWithAllProperties() {
        val json = """
            {
              "layout": {
                "type": "Slider",
                "id": "volume_slider",
                "min": 10.0,
                "max": 80.0,
                "step": 5.0,
                "value": 35.0,
                "fillMaxWidth": true,
                "onChangeAction": "onVolumeChange",
                "actionArgs": ["volume_slider"]
              }
            }
        """.trimIndent()

        val root = parseLayout(json)
        val slider = assertIs<SliderWidget>(root.layout)

        assertEquals("volume_slider", slider.id)
        assertEquals(10f, slider.min)
        assertEquals(80f, slider.max)
        assertEquals(5f, slider.step)
        assertEquals(35f, slider.value)
        assertTrue(slider.fillMaxWidth)
        assertEquals("onVolumeChange", slider.onChangeAction)
        assertEquals(listOf(JsonPrimitive("volume_slider")), slider.actionArgs)
        assertEquals("Slider", slider.typeKey())
    }

    @Test
    fun testParseSliderWithDefaults() {
        val json = """
            {
              "layout": {
                "type": "Slider",
                "id": "simple_slider"
              }
            }
        """.trimIndent()

        val root = parseLayout(json)
        val slider = assertIs<SliderWidget>(root.layout)

        assertEquals("simple_slider", slider.id)
        assertEquals(0f, slider.min)
        assertEquals(100f, slider.max)
        assertNull(slider.step)
        assertNull(slider.value)
        assertEquals(emptyList(), slider.actionArgs)
    }

    @Test
    fun testSliderInsideColumnLayout() {
        val json = """
            {
              "layout": {
                "type": "Column",
                "children": [
                  { "type": "Text", "text": "Adjust Setting:" },
                  {
                    "type": "Slider",
                    "id": "brightness",
                    "min": 0,
                    "max": 1,
                    "step": 0.1,
                    "value": 0.5
                  }
                ]
              }
            }
        """.trimIndent()

        val root = parseLayout(json)
        val column = assertIs<ColumnWidget>(root.layout)
        assertEquals(2, column.children.size)

        val slider = assertIs<SliderWidget>(column.children[1])
        assertEquals("brightness", slider.id)
        assertEquals(0f, slider.min)
        assertEquals(1f, slider.max)
        assertEquals(0.1f, slider.step)
        assertEquals(0.5f, slider.value)
    }
}
