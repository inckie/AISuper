r # JavaScript Module Development Guide

## Overview

JS modules are TypeScript files compiled to JavaScript that extend AISuper's functionality. They run in a Kotlin JS engine with access to global functions like `xmlParse()` and `httpGet()`.

## Creating a New Module

### 1. Create Module Directory

```bash
mkdir -p modules-ts/modules/my_module
```

### 2. Create Configuration

**modules-ts/modules/my_module/module.config.json:**
```json
{
  "id": "myModule",
  "entry": "index.ts",
  "output": "my_module.js"
}
```

### 3. Create TypeScript Source

**modules-ts/modules/my_module/index.ts:**
```typescript
// Global functions are available automatically
// httpGet - fetch XML/JSON from URLs
// xmlParse - parse XML to JSON objects
// registerExports - expose functions to the host

async function getStations() {
  const xml = await httpGet("http://example.com/stations");
  const parsed = xmlParse(xml);
  return parseData(parsed);
}

function parseData(parsed: Record<string, unknown>) {
  // Process parsed data
  return [];
}

registerExports("myModule", ["getStations"]);
```

## XML Parsing Pattern

### Pattern 1: Single Root Element

**XML:**
```xml
<Stations>
  <Station>
    <ID>ST1</ID>
    <Name>Downtown</Name>
  </Station>
</Stations>
```

**TypeScript:**
```typescript
const parsed = xmlParse(xml);
const root = parsed["Stations"];
const station = root["Station"];
const id = station["ID"]["#text"];  // "ST1"
```

### Pattern 2: Multiple Elements (Array)

**XML:**
```xml
<Stations>
  <Station><ID>ST1</ID></Station>
  <Station><ID>ST2</ID></Station>
</Stations>
```

**TypeScript:**
```typescript
const parsed = xmlParse(xml);
const root = parsed["Stations"];
const stations = Array.isArray(root["Station"])
  ? root["Station"]
  : [root["Station"]];

stations.forEach(station => {
  const id = station["ID"]["#text"];
});
```

### Pattern 3: Helper Function

**Recommended approach:**
```typescript
function extractText(element: Record<string, unknown>, tagName: string): string | null {
  const child = element[tagName];
  if (!child) return null;

  if (typeof child === "object") {
    const obj = child as Record<string, unknown>;
    const text = obj["#text"];
    if (typeof text === "string" && text.length > 0) {
      return text;
    }
  }

  return null;
}

// Usage:
const station = parsed["Stations"]["Station"];
const id = extractText(station, "ID");  // "ST1"
const name = extractText(station, "Name");  // "Downtown"
```

## Global Functions Reference

### `xmlParse(xmlString: string): Record<string, unknown>`

Parses XML into JSON objects.

**Returns:**
- Root element becomes a key: `{ "RootTag": { /* contents */ } }`
- Text stored in `"#text"` key
- Attributes in `"@attributes"` key
- Arrays for repeated elements

**Example:**
```typescript
const parsed = xmlParse('<root><item>value</item></root>');
console.log(parsed); // { root: { item: { "#text": "value" } } }
```

### `httpGet(url: string): Promise<string>`

Fetches HTTP content asynchronously.

**Example:**
```typescript
const xml = await httpGet("http://example.com/data.xml");
const parsed = xmlParse(xml);
```

### `registerExports(moduleName: string, functionNames: string[]): void`

Exposes module functions to the host.

**Must call this at end of module:**
```typescript
registerExports("myModule", ["getStations", "getDetails"]);
```

**Result in host:**
- `myModule_getStations()`
- `myModule_getDetails()`

## Type Definitions

### TypeScript Interfaces

Define interfaces for your data structures:

```typescript
interface Station {
  id: string;
  name: string;
  latitude: number;
  longitude: number;
}

interface StationList {
  stations: Station[];
  count: number;
}

async function listStations(): Promise<StationList> {
  const xml = await httpGet(URL);
  const parsed = xmlParse(xml);

  // Parse and type-check
  const stations: Station[] = [];
  // ... populate stations

  return {
    stations,
    count: stations.length
  };
}
```

## Error Handling

### Try-Catch

```typescript
async function safeGetData() {
  try {
    const xml = await httpGet(URL);
    const parsed = xmlParse(xml);
    return parseData(parsed);
  } catch (error) {
    console.error("Failed to get data:", error);
    return null;
  }
}
```

### Validation

```typescript
function validateParsed(obj: unknown): boolean {
  if (typeof obj !== "object" || obj === null) return false;

  const root = obj as Record<string, unknown>;
  return "data" in root;
}

async function getData() {
  const xml = await httpGet(URL);
  const parsed = xmlParse(xml);

  if (!validateParsed(parsed)) {
    throw new Error("Invalid response format");
  }

  return parsed;
}
```

## Caching Patterns

### Simple Cache

```typescript
let cache: CachedData | null = null;
let cacheTime = 0;
const CACHE_TTL_MS = 60 * 60 * 1000;  // 1 hour

async function getData(): Promise<CachedData> {
  if (cache && Date.now() - cacheTime < CACHE_TTL_MS) {
    return cache;
  }

  const data = await fetchFresh();
  cache = data;
  cacheTime = Date.now();
  return data;
}
```

### Map-based Cache

```typescript
const cache = new Map<string, CacheEntry>();

interface CacheEntry {
  value: unknown;
  expiresAt: number;
}

function getCached<T>(key: string): T | null {
  const entry = cache.get(key);
  if (!entry) return null;

  if (Date.now() > entry.expiresAt) {
    cache.delete(key);
    return null;
  }

  return entry.value as T;
}

function setCached<T>(key: string, value: T, ttlMs: number): void {
  cache.set(key, {
    value,
    expiresAt: Date.now() + ttlMs
  });
}
```

## Building and Testing

### Build Module

```bash
cd modules-ts
npm run build
```

Output: `composeApp/src/commonMain/composeResources/files/my_module.js`

### Type Check

```bash
npm run typecheck
```

### Run Tests

```bash
npm test
```

### Watch Mode

```bash
npm run watch
```

## Common Patterns

### URL Construction

```typescript
const baseUrl = "https://api.example.com";

async function fetchData(id: string) {
  const url = `${baseUrl}/data/${encodeURIComponent(id)}`;
  return await httpGet(url);
}
```

### Number Parsing

```typescript
function parseFloat2(value: string | null): number {
  if (!value) return 0;
  const num = parseFloat(value);
  return isNaN(num) ? 0 : num;
}

function parseInt2(value: string | null): number {
  if (!value) return 0;
  const num = parseInt(value, 10);
  return isNaN(num) ? 0 : num;
}
```

### Array Normalization

```typescript
function ensureArray<T>(value: T | T[]): T[] {
  return Array.isArray(value) ? value : [value];
}

// Usage:
const items = ensureArray(parsed["Item"]);
items.forEach(item => { /* process */ });
```

## Debugging

### Console Logging

```typescript
console.log("Debug info:", data);
console.error("Error occurred:", error);
```

### Type Assertions

```typescript
const obj = someValue as Record<string, unknown>;
const text = obj["field"] as string;
```

## Best Practices

1. **Use TypeScript**: TypeScript helps catch errors early
2. **Extract helpers**: Write small functions that do one thing
3. **Handle errors**: Always wrap async/await in try-catch
4. **Cache results**: Avoid repeated API calls
5. **Normalize data**: Convert XML/JSON to typed interfaces
6. **Document functions**: Add JSDoc comments
7. **Test thoroughly**: Write unit tests in `tests/`
8. **Keep modules small**: One module per concern

## Example: Complete Module

```typescript
/**
 * Example transit API module
 */

interface Station {
  id: string;
  name: string;
}

interface ApiResponse {
  stations: Station[];
}

let stationsCache: Station[] | null = null;
let cacheTime = 0;
const CACHE_TTL = 60 * 60 * 1000;  // 1 hour

async function listStations(): Promise<Station[]> {
  if (stationsCache && Date.now() - cacheTime < CACHE_TTL) {
    return stationsCache;
  }

  try {
    const xml = await httpGet("http://api.example.com/stations");
    const parsed = xmlParse(xml);

    const stations = parseStations(parsed);
    stationsCache = stations;
    cacheTime = Date.now();

    return stations;
  } catch (error) {
    console.error("Failed to get stations:", error);
    return [];
  }
}

function parseStations(parsed: unknown): Station[] {
  if (typeof parsed !== "object" || parsed === null) return [];

  const root = parsed as Record<string, unknown>;
  const stationsElement = root["Stations"];

  if (!stationsElement) return [];

  const list = Array.isArray(stationsElement)
    ? stationsElement
    : [stationsElement];

  return list.map(el => ({
    id: extractText(el as Record<string, unknown>, "ID") || "",
    name: extractText(el as Record<string, unknown>, "Name") || ""
  }));
}

function extractText(element: Record<string, unknown>, tag: string): string | null {
  const child = element[tag];
  if (typeof child === "object" && child !== null) {
    const text = (child as Record<string, unknown>)["#text"];
    if (typeof text === "string") return text;
  }
  return null;
}

registerExports("transitApi", ["listStations"]);
```

## Resources

- XmlJsonParser output format: See `ARCHITECTURE_XML_PARSING.md`
- TypeScript docs: https://www.typescriptlang.org/docs/
- Module examples: `modules-ts/modules/`

