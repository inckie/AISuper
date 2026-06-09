/**
 * Test Setup for JS Module Development
 *
 * Provides a complete test environment with polyfills for xmlParse, httpGet, etc.
 *
 * Usage in test files:
 *
 *   import { setupTestEnvironment, mockHttpGet } from "./test-setup";
 *
 *   beforeEach(() => {
 *     setupTestEnvironment();
 *     // Now xmlParse, httpGet, registerExports are available globally
 *   });
 */

import { xmlParsePolyfill, httpGetPolyfill, registerExportsPolyfill } from "../polyfills";

interface MockHttpResponse {
  url: string;
  body: string;
  status?: number;
}

let mockResponses: Map<string, MockHttpResponse> = new Map();

/**
 * Register a mock HTTP response for testing
 *
 * @param url - URL pattern or exact URL
 * @param body - Response body
 * @param status - HTTP status code (default 200)
 */
export function mockHttpGet(
  url: string,
  body: string,
  status: number = 200
): void {
  mockResponses.set(url, { url, body, status });
}

/**
 * Clear all mock HTTP responses
 */
export function clearMocks(): void {
  mockResponses.clear();
}

/**
 * Replace httpGet with mock version that returns registered responses
 */
function createMockHttpGet() {
  return async (url: string): Promise<string> => {
    // Try exact match first
    if (mockResponses.has(url)) {
      const response = mockResponses.get(url)!;
      if (response.status !== 200) {
        throw new Error(`HTTP ${response.status}`);
      }
      return response.body;
    }

    // Try pattern matching (for URLs with query params)
    for (const [pattern, response] of mockResponses) {
      if (url.includes(pattern)) {
        if (response.status !== 200) {
          throw new Error(`HTTP ${response.status}`);
        }
        return response.body;
      }
    }

    throw new Error(`No mock response registered for ${url}`);
  };
}

/**
 * Setup test environment with polyfills
 *
 * Call this in your test setup or beforeEach hook
 */
export function setupTestEnvironment(): void {
  // Install polyfills globally
  (globalThis as any).xmlParse = xmlParsePolyfill;
  (globalThis as any).httpGet = createMockHttpGet();
  (globalThis as any).registerExports = registerExportsPolyfill;

  // Clear previous mocks
  clearMocks();
}

/**
 * Helper to test XML parsing with local XML
 */
export function parseXmlString(xml: string): Record<string, unknown> {
  return xmlParsePolyfill(xml) as Record<string, unknown>;
}

/**
 * Helper to setup common MetroMover test data
 */
export class MetroMoverTestData {
  static stationsXml(): string {
    return `<?xml version="1.0" encoding="utf-8"?>
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
</MoverStations>`;
  }

  static loopsXml(): string {
    return `<?xml version="1.0" encoding="utf-8"?>
<MoverMapShapeLoops xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <Record><LoopID>OVR</LoopID></Record>
  <Record><LoopID>IRL</LoopID></Record>
  <Record><LoopID>ORL</LoopID></Record>
  <Record><LoopID>PRY</LoopID></Record>
</MoverMapShapeLoops>`;
  }

  static trainsXml(): string {
    return `<?xml version="1.0" encoding="utf-8"?>
<MoverTrains xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <Train>
    <TrainID>101</TrainID>
    <Latitude>25.7617</Latitude>
    <Longitude>-80.1918</Longitude>
    <LoopID>OVR</LoopID>
    <LoopName>Omni</LoopName>
    <vehDirection>NorthBound</vehDirection>
  </Train>
  <Train>
    <TrainID>102</TrainID>
    <Latitude>25.7589</Latitude>
    <Longitude>-80.1925</Longitude>
    <LoopID>IRL</LoopID>
    <LoopName>Brickell</LoopName>
    <vehDirection>SouthBound</vehDirection>
  </Train>
</MoverTrains>`;
  }

  static arrivalsXml(): string {
    return `<?xml version="1.0" encoding="utf-8"?>
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
</MoverTracker>`;
  }

  static setupMocks(): void {
    mockHttpGet("MoverStations", this.stationsXml());
    mockHttpGet("MoverMapShapeLoops", this.loopsXml());
    mockHttpGet("MoverTrains", this.trainsXml());
    mockHttpGet("MoverTracker", this.arrivalsXml());
  }
}

/**
 * Example test usage:
 *
 * import { describe, it, expect, beforeEach } from "vitest";
 * import { setupTestEnvironment, mockHttpGet, parseXmlString, MetroMoverTestData } from "./test-setup";
 * import { listStations } from "../modules/miami_metromover/index";
 *
 * describe("Miami Metromover Module", () => {
 *   beforeEach(() => {
 *     setupTestEnvironment();
 *     MetroMoverTestData.setupMocks();
 *   });
 *
 *   it("should parse stations", async () => {
 *     const stations = await listStations();
 *     expect(stations).toHaveLength(2);
 *     expect(stations[0].id).toBe("DT");
 *   });
 *
 *   it("should parse XML directly", () => {
 *     const xml = '<root><item>test</item></root>';
 *     const parsed = parseXmlString(xml);
 *     expect(parsed.root.item["#text"]).toBe("test");
 *   });
 * });
 */

