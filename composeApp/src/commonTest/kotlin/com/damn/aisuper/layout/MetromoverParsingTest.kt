package com.damn.aisuper.layout

import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.math.atan2
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for the Miami Metromover JS module parsing logic.
 * Runs directly in Kotlin without JS engine to verify the algorithms work.
 */
class MetromoverParsingTest {

    private fun findBlocks(xml: String, tagName: String): List<String> {
        val escaped = escapeRegex(tagName)
        val pattern = Regex("<$escaped\\b[^>]*>([\\s\\S]*?)</$escaped>", RegexOption.IGNORE_CASE)
        return pattern.findAll(xml).map { it.groupValues[1] }.toList()
    }

    private fun readTag(xmlBlock: String, tagName: String): String? {
        val escaped = escapeRegex(tagName)
        val pattern = Regex("<$escaped\\b[^>]*>([\\s\\S]*?)</$escaped>", RegexOption.IGNORE_CASE)
        val match = pattern.find(xmlBlock)
        if (match != null) {
            val text = decodeXmlEntities(match.groupValues[1].trim())
            return if (text.isNotEmpty()) text else null
        }
        return null
    }

    private fun decodeXmlEntities(value: String): String {
        return value
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
    }

    private fun escapeRegex(value: String): String {
        return value.replace(Regex("[.*+?^$()\\[\\]|\\\\]")) { "\\$" + it.value }
    }

    private fun parseFloatSafe(value: String?): Double {
        if (value.isNullOrBlank()) return 0.0
        return try {
            value.toDouble()
        } catch (e: Exception) {
            0.0
        }
    }

    private fun parseIntSafe(value: String?): Int {
        if (value.isNullOrBlank()) return 0
        return try {
            value.toInt()
        } catch (e: Exception) {
            0
        }
    }

    // Real Miami Dade API response format
    private val STATIONS_XML = """<?xml version="1.0" encoding="utf-8"?>
<MoverStations xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <Record>
    <StationID>DT</StationID>
    <Station>Downtown</Station>
    <Latitude>25.7617</Latitude>
    <Longitude>-80.1918</Longitude>
    <Address>110 SE 6th St</Address>
    <City>Miami</City>
    <State>FL</State>
    <Zip>33131</Zip>
  </Record>
  <Record>
    <StationID>BRI</StationID>
    <Station>Brickell</Station>
    <Latitude>25.7589</Latitude>
    <Longitude>-80.1925</Longitude>
    <Address>88 SW 8th St</Address>
    <City>Miami</City>
    <State>FL</State>
    <Zip>33130</Zip>
  </Record>
</MoverStations>"""

    private val LOOPS_XML = """<?xml version="1.0" encoding="utf-8"?>
<MoverMapShapeLoops xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <Record><LoopID>OVR</LoopID></Record>
  <Record><LoopID>IRL</LoopID></Record>
  <Record><LoopID>ORL</LoopID></Record>
  <Record><LoopID>PRY</LoopID></Record>
</MoverMapShapeLoops>"""

    private val ARRIVALS_XML = """<?xml version="1.0" encoding="utf-8"?>
<MoverTracker xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <Info>
    <firstLoopID>OVR</firstLoopID>
    <firstLoopName>Omni</firstLoopName>
    <firstTime1>2 min</firstTime1>
    <firstTime2>11 min</firstTime2>
    <secondLoopID>IRL</secondLoopID>
    <secondLoopName>Brickell</secondLoopName>
    <secondTime1>5 min</secondTime1>
    <thirdLoopID>ORL</thirdLoopID>
    <thirdLoopName>Overtown</thirdLoopName>
    <thirdTime1>8 min</thirdTime1>
  </Info>
</MoverTracker>"""

    @Test
    fun testFindBlocksStations() {
        val blocks = findBlocks(STATIONS_XML, "Record")
        assertEquals(2, blocks.size, "Should find 2 Record blocks")
    }

    @Test
    fun testReadTagStationID() {
        val blocks = findBlocks(STATIONS_XML, "Record")
        assertEquals(2, blocks.size)

        val stationId = readTag(blocks[0], "StationID")
        assertEquals("DT", stationId, "First station ID should be DT")

        val stationId2 = readTag(blocks[1], "StationID")
        assertEquals("BRI", stationId2, "Second station ID should be BRI")
    }

    @Test
    fun testReadTagStation() {
        val blocks = findBlocks(STATIONS_XML, "Record")
        val station = readTag(blocks[0], "Station")
        assertEquals("Downtown", station, "First station title should be Downtown")
    }

    @Test
    fun testParseStationsXml() {
        val blocks = findBlocks(STATIONS_XML, "Record")
        val stations = mutableListOf<Map<String, Any?>>()

        for (block in blocks) {
            val stationId = readTag(block, "StationID")
            val title = readTag(block, "Station")
            val latitudeRaw = readTag(block, "Latitude")
            val longitudeRaw = readTag(block, "Longitude")

            if (!stationId.isNullOrBlank() && !title.isNullOrBlank() &&
                !latitudeRaw.isNullOrBlank() && !longitudeRaw.isNullOrBlank()) {

                stations.add(mapOf(
                    "id" to stationId,
                    "title" to title,
                    "latitude" to parseFloatSafe(latitudeRaw),
                    "longitude" to parseFloatSafe(longitudeRaw),
                    "address" to readTag(block, "Address"),
                    "city" to readTag(block, "City")
                ))
            }
        }

        assertEquals(2, stations.size, "Should parse 2 stations")
        assertEquals("DT", stations[0]["id"], "First should be DT")
        assertEquals("Downtown", stations[0]["title"])
        assertEquals(25.7617, stations[0]["latitude"])
        assertEquals(-80.1918, stations[0]["longitude"])
    }

    @Test
    fun testParseLoopsXml() {
        val blocks = findBlocks(LOOPS_XML, "Record")
        val loops = mutableListOf<String>()

        for (block in blocks) {
            val loopId = readTag(block, "LoopID")
            if (!loopId.isNullOrBlank()) {
                loops.add(loopId)
            }
        }

        assertEquals(4, loops.size, "Should parse 4 loops")
        assertEquals(listOf("OVR", "IRL", "ORL", "PRY"), loops)
    }

    @Test
    fun testParseArrivalsXml() {
        val blocks = findBlocks(ARRIVALS_XML, "Info")
        assertEquals(1, blocks.size, "Should find 1 Info block")

        val infoBlock = blocks[0]
        val prefixes = listOf("first", "second", "third", "forth", "fifth")
        val arrivals = mutableListOf<Map<String, Any?>>()

        for (prefix in prefixes) {
            val loopId = readTag(infoBlock, "${prefix}LoopID")
            if (loopId == null) break

            val loopName = readTag(infoBlock, "${prefix}LoopName")
            if (loopName == null || loopName == "*****") break

            val time1 = readTag(infoBlock, "${prefix}Time1")
            val time2 = readTag(infoBlock, "${prefix}Time2")
            val times = listOfNotNull(time1, time2)

            if (times.isNotEmpty()) {
                arrivals.add(mapOf(
                    "loopId" to loopId,
                    "loopName" to loopName,
                    "arrivals" to times
                ))
            }
        }

        assertEquals(3, arrivals.size, "Should parse 3 arrival groups")
        assertEquals("OVR", arrivals[0]["loopId"])
        assertEquals("Omni", arrivals[0]["loopName"])
        assertEquals(listOf("2 min", "11 min"), arrivals[0]["arrivals"])
    }

    @Test
    fun testParseFloatSafe() {
        assertEquals(25.7617, parseFloatSafe("25.7617"))
        assertEquals(0.0, parseFloatSafe(""))
        assertEquals(0.0, parseFloatSafe(null))
        assertEquals(0.0, parseFloatSafe("invalid"))
    }

    @Test
    fun testParseIntSafe() {
        assertEquals(101, parseIntSafe("101"))
        assertEquals(0, parseIntSafe(""))
        assertEquals(0, parseIntSafe(null))
        assertEquals(0, parseIntSafe("invalid"))
    }

    @Test
    fun testRegexWithNamespace() {
        val xmlWithNamespace = """<Record xmlns="http://example.com">
            <StationID xmlns:xsd="http://www.w3.org/2001/XMLSchema">DT</StationID>
            <Station>Downtown</Station>
        </Record>"""

        val stationId = readTag(xmlWithNamespace, "StationID")
        assertEquals("DT", stationId, "Should handle namespace attributes")
    }

    @Test
    fun testEmptyFieldsSkipped() {
        val xmlWithEmpty = """<Record>
            <StationID>DT</StationID>
            <Station>Downtown</Station>
            <Latitude></Latitude>
            <Longitude>-80.1918</Longitude>
        </Record>"""

        val stationId = readTag(xmlWithEmpty, "StationID")
        val latitude = readTag(xmlWithEmpty, "Latitude")
        val longitude = readTag(xmlWithEmpty, "Longitude")

        assertEquals("DT", stationId)
        assertEquals(null, latitude, "Empty Latitude should return null")
        assertEquals("-80.1918", longitude)
    }

    @Test
    fun testCdataNotHandled() {
        val xmlWithCdata = """<Record>
            <StationID><![CDATA[DT]]></StationID>
            <Station>Downtown</Station>
        </Record>"""

        val stationId = readTag(xmlWithCdata, "StationID")
        // CDATA content is not extracted - this will be null or contain CDATA markers
        println("CDATA test result: $stationId")
        // This demonstrates the potential issue - CDATA wrappers would break parsing
    }

    @Test
    fun testCaseSensitivity() {
        val xmlLowercase = """<Record>
            <stationid>DT</stationid>
            <station>Downtown</station>
        </Record>"""

        val stationId = readTag(xmlLowercase, "StationID")
        assertEquals("DT", stationId, "Should handle case insensitivity with IGNORE_CASE flag")
    }

    @Test
    fun testHaversineDistance() {
        // Distance from Downtown (25.7617, -80.1918) to Brickell (25.7589, -80.1925)
        val lat1 = 25.7617
        val lon1 = -80.1918
        val lat2 = 25.7589
        val lon2 = -80.1925

        val distance = haversineKm(lat1, lon1, lat2, lon2)

        // Distance should be approximately 0.5 km
        assertTrue(distance > 0.3 && distance < 1.0, "Distance should be ~0.5 km, got $distance")
    }

    private fun toRadians(value: Double): Double = value * PI / 180

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusKm = 6371.0
        val lat1Rad = toRadians(lat1)
        val lon1Rad = toRadians(lon1)
        val lat2Rad = toRadians(lat2)
        val lon2Rad = toRadians(lon2)

        val dLat = lat2Rad - lat1Rad
        val dLon = lon2Rad - lon1Rad

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadiusKm * c
    }
}

