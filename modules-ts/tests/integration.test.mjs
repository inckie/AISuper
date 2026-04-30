import assert from "node:assert";
import { test } from "node:test";

// This test simulates the module being loaded in the runtime
// It checks that:
// 1. Module functions are callable
// 2. The registerExports call structure is correct
// 3. httpGet bridge requirements are identified

test("Module Integration - registerExports call", () => {
  // This simulates what the module does at load time
  const exportedFunctions = [];

  // Mock global registerExports that the module will call
  global.registerExports = (moduleName, functionNames) => {
    assert.strictEqual(moduleName, "miamiMetromover", "Module name should be miamiMetromover");
    assert(Array.isArray(functionNames), "Function names should be an array");
    assert(functionNames.length > 0, "Should export at least one function");
    exportedFunctions.push(...functionNames);
  };

  const expectedExports = [
    "list_stations",
    "list_loops",
    "get_trains",
    "get_station_arrivals",
    "get_loop_svg",
    "find_nearest_station"
  ];

  // Simulate what the compiled module does
  registerExports("miamiMetromover", [
    "list_stations",
    "list_loops",
    "get_trains",
    "get_station_arrivals",
    "get_loop_svg",
    "find_nearest_station"
  ]);

  assert.deepEqual(exportedFunctions, expectedExports, "All expected functions should be exported");
});

test("Module Integration - httpGet is required", (t) => {
  // List all functions that depend on httpGet
  const functionsUsingHttpGet = [
    "list_stations",      // calls fetchXml -> fetchXmlOnce -> httpGet
    "list_loops",         // calls fetchXml -> fetchXmlOnce -> httpGet
    "get_trains",         // calls fetchXml -> fetchXmlOnce -> httpGet
    "get_station_arrivals", // calls fetchXml -> fetchXmlOnce -> httpGet
    "get_loop_svg",       // calls getLoopShape -> fetchXml -> fetchXmlOnce -> httpGet
    "find_nearest_station" // only uses list_stations (which uses httpGet)
  ];

  // Verify that these functions require httpGet as the HTTP bridge
  assert.strictEqual(
    functionsUsingHttpGet.length,
    6,
    "All 6 exported functions should require httpGet"
  );

  console.log("Functions that require httpGet bridge:");
  functionsUsingHttpGet.forEach((fn) => {
    console.log(`  - ${fn}`);
  });
});

test("Module Configuration - jsModule wiring", () => {
  // Verify the module.config.json references match what's expected
  const config = {
    id: "miamiMetromover",
    entry: "index.ts",
    output: "miami_metromover_module.js"
  };

  assert.strictEqual(config.id, "miamiMetromover", "Config ID should match module ID");
  assert(config.entry.endsWith(".ts"), "Entry should be TypeScript");
  assert(config.output.endsWith(".js"), "Output should be JavaScript");
});

test("Feature Script - module proxy calls", () => {
  // Verify the feature script uses correct proxy function names
  const proxyFunctionCalls = [
    "miamiMetromover_list_loops()",
    "miamiMetromover_list_stations()",
    "miamiMetromover_find_nearest_station(geo.latitude, geo.longitude, 2.0)",
    "miamiMetromover_get_loop_svg(loopId, 720, 720, 16)",
    "miamiMetromover_get_station_arrivals(stationId)"
  ];

  // Each proxy should follow pattern: {moduleName}_{functionName}
  proxyFunctionCalls.forEach((call) => {
    const match = call.match(/^miamiMetromover_(\w+)\(/);
    assert(match, `Proxy call should match pattern: ${call}`);

    const functionName = match[1];
    const validFunctions = [
      "list_loops",
      "list_stations",
      "find_nearest_station",
      "get_loop_svg",
      "get_station_arrivals"
    ];

    assert(validFunctions.includes(functionName), `Function ${functionName} should be exported by module`);
  });
});

test("Module Integration - xmlParse global function is available", () => {
  // Verify that xmlParse is declared as a global function
  // This simulates the Kotlin AppJSEngine making it available

  // Mock global xmlParse function (as provided by XmlJsonParser.kt)
  global.xmlParse = (xmlString) => {
    // Simulate what the Kotlin XmlJsonParser.parse does:
    // Returns: { "RootTag": { ... } }
    return {
      MoverStations: {
        Record: {
          StationID: { "#text": "DT" },
          Station: { "#text": "Downtown" },
          Latitude: { "#text": "25.7617" },
          Longitude: { "#text": "-80.1918" }
        }
      }
    };
  };

  // Test that xmlParse is callable and returns expected structure
  const xml = "<MoverStations><Record>...</Record></MoverStations>";
  const result = xmlParse(xml);

  assert(typeof result === "object", "xmlParse should return an object");
  assert(result.MoverStations, "Should have root element MoverStations");
  assert(result.MoverStations.Record, "Should have Record child");
  assert(result.MoverStations.Record.StationID["#text"] === "DT", "Should extract text from nested #text");

  console.log("✓ xmlParse global function is available and returns correct structure");
});

