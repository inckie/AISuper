import assert from "node:assert";
import { test } from "node:test";

// Test various XML formats to identify potential parsing issues

test("XML Format Variations - find what breaks parsing", () => {
  console.log(`
╔════════════════════════════════════════════════════════════════════════════╗
║                    XML FORMAT DETECTION GUIDE                             ║
╚════════════════════════════════════════════════════════════════════════════╝

The parsing expects this exact structure:

  <Record>
    <StationID>...</StationID>
    <Station>...</Station>
    <Latitude>...</Latitude>
    <Longitude>...</Longitude>
  </Record>

But could receive variations:

`);

  const variations = [
    {
      name: "EXPECTED FORMAT",
      expected: true,
      xml: `<Record>
  <StationID>DT</StationID>
  <Station>Downtown</Station>
  <Latitude>25.7617</Latitude>
  <Longitude>-80.1918</Longitude>
</Record>`,
      result: "✓ WORKS"
    },
    {
      name: "WITH CDATA",
      expected: false,
      xml: `<Record>
  <StationID><![CDATA[DT]]></StationID>
  <Station><![CDATA[Downtown]]></Station>
  <Latitude>25.7617</Latitude>
  <Longitude>-80.1918</Longitude>
</Record>`,
      result: "✗ FAILS - CDATA not extracted"
    },
    {
      name: "DIFFERENT CASE",
      expected: false,
      xml: `<Record>
  <stationid>DT</stationid>
  <station>Downtown</station>
  <latitude>25.7617</latitude>
  <longitude>-80.1918</longitude>
</Record>`,
      result: "✗ FAILS - Case sensitive"
    },
    {
      name: "WITH ATTRIBUTES",
      expected: true,
      xml: `<Record id="1">
  <StationID type="string">DT</StationID>
  <Station nullable="false">Downtown</Station>
  <Latitude>25.7617</Latitude>
  <Longitude>-80.1918</Longitude>
</Record>`,
      result: "✓ WORKS - Attributes ignored"
    },
    {
      name: "NO RECORD WRAPPER",
      expected: false,
      xml: `<Stations>
  <StationID>DT</StationID>
  <Station>Downtown</Station>
  <Latitude>25.7617</Latitude>
  <Longitude>-80.1918</Longitude>
</Stations>`,
      result: "✗ FAILS - No Record tags"
    },
    {
      name: "NESTED IN RESPONSE",
      expected: false,
      xml: `<Response>
  <Data>
    <Stations>
      <StationID>DT</StationID>
      <Station>Downtown</Station>
    </Stations>
  </Data>
</Response>`,
      result: "✗ FAILS - Different nesting"
    },
    {
      name: "WITH NAMESPACE",
      expected: true,
      xml: `<Record xmlns="http://example.com" xmlns:xsd="http://www.w3.org/2001/XMLSchema">
  <StationID>DT</StationID>
  <Station>Downtown</Station>
  <Latitude>25.7617</Latitude>
  <Longitude>-80.1918</Longitude>
</Record>`,
      result: "✓ WORKS - Namespaces handled"
    },
    {
      name: "EMPTY FIELDS MISSING",
      expected: false,
      xml: `<Record>
  <StationID>DT</StationID>
  <Station>Downtown</Station>
  <Latitude></Latitude>
  <Longitude>-80.1918</Longitude>
</Record>`,
      result: "✗ FAILS - Empty Latitude → skipped"
    },
    {
      name: "SELF-CLOSING TAGS",
      expected: false,
      xml: `<Record>
  <StationID>DT</StationID>
  <Station>Downtown</Station>
  <Latitude/>
  <Longitude>-80.1918</Longitude>
</Record>`,
      result: "✗ FAILS - Self-closing → empty → skipped"
    }
  ];

  variations.forEach((variation, idx) => {
    console.log(`
${idx + 1}. ${variation.name}
   Expected to ${variation.expected ? 'WORK ✓' : 'FAIL ✗'}

   XML:
${variation.xml.split("\\n").map(line => "   " + line).join("\\n")}

   ${variation.result}
`);
  });

  console.log(`
╔════════════════════════════════════════════════════════════════════════════╗
║ IF YOU'RE SEEING EMPTY ARRAYS                                              ║
╚════════════════════════════════════════════════════════════════════════════╝

The raw XML response likely falls into one of the FAILS categories above.

TO IDENTIFY WHICH ONE:

1. Run the feature and capture debug log
2. Look for: "DEBUG: loopResponse value:" or "DEBUG: stationResponse value:"
3. Share the XML snippet from that log
4. Compare to variations above

Example log output to look for:

  DEBUG: stationResponse value: []

  Then check module logs, you should see raw XML logged

Common issues:
  - ✗ Different XML structure (not Record tags)
  - ✗ CDATA wrappers around field values
  - ✗ Different field names or casing
  - ✗ Fields wrapped in additional tags
  - ✗ Namespaces (actually works ✓ but might look different)

================================================================================
`);

});

test("Regex Pattern Testing - verify tag extraction works", () => {
  console.log("\n\nTesting regex pattern used in parsing:\n");

  function testRegex(tagName, content, shouldMatch = true) {
    const escaped = tagName.replace(/[.*+?^${}()|[\\]\\]/g, "\\$&");
    const pattern = new RegExp(`<${escaped}\\b[^>]*>([\\s\\S]*?)<\\/${escaped}>`, "i");
    const match = pattern.exec(content);
    const found = match !== null;
    const value = match ? match[1].trim() : null;

    const status = found === shouldMatch ? "✓" : "✗";
    console.log(`  ${status} Pattern for <${tagName}>: ${found ? `matched "${value}"` : "NO MATCH"}`);

    return found === shouldMatch;
  }

  console.log("Pattern: <TagName...>content</TagName>");
  console.log("");

  testRegex("StationID", "<StationID>DT</StationID>", true);
  testRegex("StationID", '<StationID type="id">DT</StationID>', true);
  testRegex("StationID", `<StationID attr="val">
    DT
  </StationID>`, true);
  testRegex("StationID", "<stationid>DT</stationid>", true); // case insensitive
  testRegex("StationID", "<StationID/>");  // self-closing, should NOT match
  testRegex("StationID", "<Station>DT</Station>", false); // wrong tag
});

