# Testing JS Modules - Quick Guide

## Overview

The `test-setup.ts` provides polyfills for `xmlParse`, `httpGet`, and `registerExports` so you can test your JS modules in Node.js with full IDE support.

## Setup

### 1. Import test utilities in your test file

```typescript
import { setupTestEnvironment, mockHttpGet, parseXmlString, MetroMoverTestData } from "./test-setup";
import { describe, it, expect, beforeEach } from "vitest";
```

### 2. Setup before each test

```typescript
describe("My Module", () => {
  beforeEach(() => {
    setupTestEnvironment();
  });

  it("should work", () => {
    // Your test here
  });
});
```

## Testing Patterns

### Pattern 1: Test XML Parsing

```typescript
import { parseXmlString } from "./test-setup";

it("should parse XML", () => {
  const xml = '<root><item>value</item></root>';
  const parsed = parseXmlString(xml);

  expect(parsed.root.item["#text"]).toBe("value");
});
```

### Pattern 2: Mock HTTP Responses

```typescript
import { setupTestEnvironment, mockHttpGet } from "./test-setup";

beforeEach(() => {
  setupTestEnvironment();

  mockHttpGet("http://api.example.com/data", '<response><status>ok</status></response>');
});

it("should fetch and parse data", async () => {
  const result = await myFunction();
  expect(result.status).toBe("ok");
});
```

### Pattern 3: Use MetroMover Test Data

```typescript
import { setupTestEnvironment, MetroMoverTestData } from "./test-setup";

beforeEach(() => {
  setupTestEnvironment();
  MetroMoverTestData.setupMocks();
});

it("should list stations", async () => {
  const stations = await list_stations();

  expect(stations).toHaveLength(2);
  expect(stations[0].id).toBe("DT");
  expect(stations[0].title).toBe("Downtown");
});
```

### Pattern 4: Test Error Handling

```typescript
import { setupTestEnvironment } from "./test-setup";

beforeEach(() => {
  setupTestEnvironment();
  // Don't set up any mocks - httpGet will fail
});

it("should handle missing data", async () => {
  await expect(myFunction()).rejects.toThrow("No mock response");
});
```

## Available Test Data

### MetroMoverTestData

Pre-made XML responses for testing Miami Metromover module:

```typescript
MetroMoverTestData.stationsXml()     // Stations with 2 records
MetroMoverTestData.loopsXml()        // 4 loop IDs
MetroMoverTestData.trainsXml()       // 2 train records
MetroMoverTestData.arrivalsXml()     // Arrival times at a station
MetroMoverTestData.setupMocks()      // Register all above at once
```

## Full Example Test

```typescript
import { describe, it, expect, beforeEach } from "vitest";
import { setupTestEnvironment, mockHttpGet, parseXmlString, MetroMoverTestData } from "./test-setup";
import { listStations, getStationArrivals, extractText } from "../modules/miami_metromover/index";

describe("Miami Metromover Module", () => {
  beforeEach(() => {
    setupTestEnvironment();
    MetroMoverTestData.setupMocks();
  });

  describe("XML Parsing", () => {
    it("should parse simple XML", () => {
      const xml = '<root><item key="value">text</item></root>';
      const parsed = parseXmlString(xml);

      expect(parsed.root.item["@attributes"].key).toBe("value");
      expect(parsed.root.item["#text"]).toBe("text");
    });

    it("should handle multiple elements", () => {
      const xml = '<root><item>first</item><item>second</item></root>';
      const parsed = parseXmlString(xml);

      expect(Array.isArray(parsed.root.item)).toBe(true);
      expect(parsed.root.item).toHaveLength(2);
    });
  });

  describe("Station Functions", () => {
    it("should list all stations", async () => {
      const stations = await listStations();

      expect(stations).toHaveLength(2);
      expect(stations[0].id).toBe("DT");
      expect(stations[1].id).toBe("BRI");
    });

    it("should get station arrivals", async () => {
      const arrivals = await getStationArrivals("DT");

      expect(arrivals.stationId).toBe("DT");
      expect(arrivals.arrivals).toHaveLength(3);
    });

    it("should handle invalid station", async () => {
      mockHttpGet("MoverTracker", "<MoverTracker><Info></Info></MoverTracker>");

      const arrivals = await getStationArrivals("INVALID");
      expect(arrivals.arrivals).toHaveLength(0);
    });
  });

  describe("Helper Functions", () => {
    it("should extract text from element", () => {
      const element = {
        Field: { "#text": "value" }
      };

      const text = extractText(element as any, "Field");
      expect(text).toBe("value");
    });

    it("should return null for missing field", () => {
      const element = {};

      const text = extractText(element as any, "Missing");
      expect(text).toBeNull();
    });
  });
});
```

## Running Tests

### Run all tests

```bash
npm test
```

### Run specific test file

```bash
npm test -- modules/miami_metromover
```

### Run tests in watch mode

```bash
npm test -- --watch
```

### Run tests with coverage

```bash
npm test -- --coverage
```

## Adding Custom Test Data

Create your own test data class:

```typescript
export class MyModuleTestData {
  static mockDataXml(): string {
    return `<root>
      <item id="1">Data</item>
    </root>`;
  }

  static setupMocks(): void {
    mockHttpGet("http://api.example.com/data", this.mockDataXml());
  }
}
```

Then use in tests:

```typescript
beforeEach(() => {
  setupTestEnvironment();
  MyModuleTestData.setupMocks();
});
```

## Polyfill Behavior

### xmlParse

- Parses XML to JSON
- Matches Kotlin XmlJsonParser output format
- Handles attributes, text, CDATA, comments
- Converts repeated elements to arrays

### httpGet

- Takes a URL string
- Returns Promise<string>
- Uses mocked responses from `mockHttpGet()`
- Throws if no mock is registered

### registerExports

- Logs module registration
- In tests, doesn't do anything special
- Use for verifying module structure

## Debugging Tests

### Print parsed data

```typescript
const parsed = parseXmlString(xml);
console.log("Parsed:", JSON.stringify(parsed, null, 2));
```

### Check mock calls

```typescript
// In vitest with mocking
import { vi } from "vitest";

const mockFetch = vi.fn();
// ... setup mock
expect(mockFetch).toHaveBeenCalledWith("http://api.example.com/data");
```

### Run single test

```bash
npm test -- -t "should list stations"
```

## See Also

- **JS_MODULE_DEVELOPMENT.md** - General module development guide
- **ARCHITECTURE_XML_PARSING.md** - How XML parsing works
- **modules-ts/tests/** - Existing tests for examples

