package com.damn.aisuper.runtime

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class XmlJsonParserTest {

	@Test
	fun parseSimpleTree() {
		val xml = """
			<MoverStations>
			  <Record>
				<StationID>DT</StationID>
				<Station>Downtown</Station>
			  </Record>
			  <Record>
				<StationID>BRI</StationID>
				<Station>Brickell</Station>
			  </Record>
			</MoverStations>
		""".trimIndent()

		val parsed = XmlJsonParser.parse(xml) as JsonObject
		val root = parsed["MoverStations"] as JsonObject
		val records = root["Record"] as JsonArray

		assertEquals(2, records.size)
		val first = records[0] as JsonObject
		assertEquals("DT", ((first["StationID"] as JsonObject)["#text"] as JsonPrimitive).content)
	}

	@Test
	fun parseAttributesAndText() {
		val xml = """
			<Root id="123" enabled="true">
			  <Title>Hello</Title>
			</Root>
		""".trimIndent()

		val parsed = XmlJsonParser.parse(xml) as JsonObject
		val root = parsed["Root"] as JsonObject
		val attrs = root["@attributes"] as JsonObject

		assertEquals("123", (attrs["id"] as JsonPrimitive).content)
		assertEquals("true", (attrs["enabled"] as JsonPrimitive).content)
		assertEquals("Hello", ((root["Title"] as JsonObject)["#text"] as JsonPrimitive).content)
	}

	@Test
	fun parseSelfClosingNodes() {
		val xml = """
			<Root>
			  <Item code="A"/>
			  <Item code="B"/>
			</Root>
		""".trimIndent()

		val parsed = XmlJsonParser.parse(xml) as JsonObject
		val root = parsed["Root"] as JsonObject
		val items = root["Item"] as JsonArray

		assertEquals(2, items.size)
		val firstAttrs = (items[0] as JsonObject)["@attributes"] as JsonObject
		assertEquals("A", (firstAttrs["code"] as JsonPrimitive).content)
	}

	@Test
	fun parseDecodesEntitiesAndCdata() {
		val xml = """
			<Root>
			  <Text>&lt;abc&amp;def&gt;</Text>
			  <Raw><![CDATA[x<y>z]]></Raw>
			</Root>
		""".trimIndent()

		val parsed = XmlJsonParser.parse(xml) as JsonObject
		val root = parsed["Root"] as JsonObject

		assertEquals("<abc&def>", ((root["Text"] as JsonObject)["#text"] as JsonPrimitive).content)
		assertEquals("x<y>z", ((root["Raw"] as JsonObject)["#text"] as JsonPrimitive).content)
		assertTrue(root.containsKey("Text"))
	}

	@Test
	fun parseUsedAsGlobalXmlParseFunction() {
		// This test simulates how xmlParse is called from JS runtime
		val xml = """
			<MoverStations>
			  <Record>
				<StationID>DT</StationID>
				<Station>Downtown</Station>
				<Latitude>25.7617</Latitude>
				<Longitude>-80.1918</Longitude>
			  </Record>
			</MoverStations>
		""".trimIndent()

		// Call parse as it would be called from JS via xmlParse() global
		val parsed = XmlJsonParser.parse(xml)

		// Verify the structure matches what JS parsing functions expect
		assertTrue(parsed is JsonObject, "Result should be a JsonObject")
		val asObj = parsed as JsonObject

		assertTrue(asObj.containsKey("MoverStations"), "Should have root element as key")
		val root = asObj["MoverStations"] as JsonObject

		assertTrue(root.containsKey("Record"), "Should have Record child element")
		val record = root["Record"] as JsonObject

		assertTrue(record.containsKey("StationID"), "Record should have StationID")
		val stationId = record["StationID"] as JsonObject
		assertEquals("DT", ((stationId)["#text"] as JsonPrimitive).content)
	}
}
