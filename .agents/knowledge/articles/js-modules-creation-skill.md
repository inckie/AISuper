---
categories:
- applet-developer
created: '2026-06-20T04:50:58.173333+00:00'
id: js-modules-creation-skill
modified: '2026-06-20T04:57:58.283150+00:00'
tags:
- skills
- js-modules
- typescript
- keight
- runtime
title: JS Modules Creation Skill
type: leaf
---

# JS Modules Creation Skill (AISuper)

This note captures the repeatable workflow for adding or debugging JS modules in AISuper.

## 1) Module contract

- A module script must call:
  - `registerExports("<moduleId>", ["fn1", "fn2", ...])`
- `JsModuleRuntime` creates feature-callable proxies as:
  - `<moduleId>_<exportName>()`  ← **flat global function, NOT `moduleId.exportName()`**
- Example:
  - export `get_current_weather` from module `weather`
  - feature calls `weather_get_current_weather(lat, lon, "auto")`
  - ❌ wrong: `weather.get_current_weather(...)` → `ReferenceError: weather is not defined`

## 2) Files to touch when creating a new JS module

1. TypeScript source: `modules-ts/modules/<moduleId>/index.ts`
2. Build config: `modules-ts/modules/<moduleId>/module.config.json`
3. Build output (generated): `composeApp/src/commonMain/composeResources/files/<module>_module.js`
4. Feature declaration in: `composeApp/src/commonMain/composeResources/files/applet.json`
5. Feature script: `composeApp/src/commonMain/composeResources/files/<feature>_script.js`

### module.config.json shape
```json
{
  "id": "myModule",
  "entry": "index.ts",
  "output": "my_module.js"
}
```

### applet.json module declaration shape (feature-level, feature-owned lifetime)
```json
{
  "type": "jsModule",
  "name": "myModule",
  "script": "files/my_module.js"
}
```

### Build command
```powershell
Set-Location "D:\Work\Mobile\Android\AISuper\modules-ts"
npm run build
```

## 3) Host bridges and feature modules

- If module needs HTTP, feature must also include native module:
  - `{ "type": "http", "name": "httpMain" }`
- `JsModuleRuntime` bridges `httpGet` when `http` module is present.
- If module needs geolocation: `{ "type": "geolocation", "name": "geoMain" }`
- Missing bridge symptoms: module loads, but calls fail or return empty.

## 4) Keight JS runtime compatibility rules (important)

The embedded JS engine is **not a browser or Node.js**. Many standard Web APIs are absent.

### ❌ APIs that are NOT available

| API | Symptom | Fix |
|-----|---------|-----|
| `URLSearchParams` | `TypeError: 'undefined' (URLSearchParams) is not a constructor` | Build query string manually (see below) |
| `for (const key in obj)` | `TypeError: Assignment to constant variable ('key')` | Use `Object.keys()` + indexed `for` loop |
| `for...of` on some iterables | May fail silently or error | Prefer indexed `for` loops |
| `Map`, `Set` | May not work reliably | Use plain object caches |
| Template literals | Generally OK | OK to use |
| `encodeURIComponent` | Available as host bridge | OK to use |
| `JSON.parse` / `JSON.stringify` | Available | OK to use |
| `Promise`, `async/await` | Available | OK to use |
| `Array.isArray`, spread, destructuring | Generally OK | OK to use |

### ✅ Safe query string building (no URLSearchParams)
```typescript
function buildQueryString(params: Record<string, string>): string {
  const pairs: string[] = [];
  const keys = Object.keys(params);
  for (let i = 0; i < keys.length; i++) {
    const k = keys[i];
    pairs.push(encodeURIComponent(k) + "=" + encodeURIComponent(params[k]));
  }
  return pairs.join("&");
}
```

### ✅ Safe object iteration pattern
```typescript
const keys = Object.keys(someObj);
for (let i = 0; i < keys.length; i++) {
  const k = keys[i];
  // use k and someObj[k]
}
```

### ✅ Safe array iteration pattern
```typescript
for (let i = 0; i < arr.length; i++) {
  const item = arr[i];
}
```

## 5) Module lifetime

- **Feature-level** `jsModule` entries in `applet.json` are **owned by the Feature**.
  - Created when the feature loads, destroyed when the feature is closed/navigated away from.
- **Applet-level** `jsModules` (top-level key in `applet.json`) are reserved for future applet-lifetime modules.
  - Currently left empty intentionally.

## 6) API parity rule

Keep runtime API names stable between TS source and feature script callers.

- If feature expects `get_trains`, do not export `list_trains` only.
- Keep `registerExports(...)` list in sync with what feature scripts call.
- The registered proxy name is always: `<moduleId>_<functionName>`

## 7) Host-provided global functions available in module scripts

Declared via `declare function` in TypeScript:

| Function | Description |
|----------|-------------|
| `httpGet(url: string, headers?: Record<string, string>): Promise<string>` | HTTP GET, requires `http` native module in feature. Returns plain body. |
| `httpPost(url: string, body?: string, headers?: Record<string, string>): Promise<string>` | HTTP POST. Returns plain body. |
| `httpRequestRaw(method, url, body?, headers?): Promise<string>` | Raw HTTP request returning a JSON envelope (see below). |
| `jsonParse(json: string): unknown` | Safe JSON parse |
| `xmlParse(xml: string): Record<string, unknown>` | XML → JSON parse |
| `encodeURIComponent(s: string): string` | URI encoding |
| `consoleLog(...)` | Logging |
| `consoleError(...)` | Error logging |

### When to use `httpRequestRaw` vs `httpGet` / `httpPost`
`httpGet` and `httpPost` only return the plain response body as a string. They completely discard HTTP status codes and response headers.

If your module needs to handle specific HTTP status codes (e.g., handling `409 Conflict` for session token negotiation) or read response headers (e.g., extracting an `X-Transmission-Session-Id` token), you **must** use `httpRequestRaw`.

It returns a JSON-encoded string envelope containing the full response context:
```json
{
  "status": 200,
  "headers": {
    "X-Transmission-Session-Id": "token123"
  },
  "body": "..."
}
```
*Note: You must `JSON.parse()` the result of `httpRequestRaw` before accessing these fields.*

### Using `xmlParse` for XML processing
If your module receives XML data (e.g., from an RSS feed or SOAP API), convert it to a JSON object using the `xmlParse` global function. 

The parser automatically decodes XML entities, extracts attributes into an `@attributes` key, places inner text into a `#text` key, and groups repeated sibling tags into arrays.

**Input XML:**
```xml
<MoverStations>
  <Record id="1">
    <StationID>DT</StationID>
    <Station>Downtown</Station>
  </Record>
</MoverStations>
```

**Parser Output (JSON object):**
```json
{
  "MoverStations": {
    "Record": {
      "@attributes": { "id": "1" },
      "StationID": { "#text": "DT" },
      "Station": { "#text": "Downtown" }
    }
  }
}
```

*Note: The exact behavior of the parser is defined in the native Kotlin engine, but a fully compatible JavaScript implementation is available in `template/typescript/polyfills.ts` for unit testing. For more details on the architecture and debugging, see [[Architecture: XML Parsing Integration]].*

## 8) Test strategy

- Parser/unit checks (Node):
  - `modules-ts/tests/*.test.mjs`
- Keight behavior (JVM common tests):
  - `composeApp/src/commonTest/kotlin/com/damn/aisuper/engine/KeightJSModuleIntegrationTest.kt`

## 9) Useful commands (Windows PowerShell)

```powershell
# Set JAVA_HOME once per session
$env:JAVA_HOME="c:\Users\ink\.jdks\jbr-21.0.8\"

# Build TypeScript modules → JS assets
Set-Location "D:\Work\Mobile\Android\AISuper\modules-ts"
npm run build

# Compile-check Kotlin
Set-Location "D:\Work\Mobile\Android\AISuper"
.\gradlew.bat :composeApp:compileCommonMainKotlinMetadata --no-daemon

# Run Keight integration tests
.\gradlew.bat :composeApp:jvmTest --tests "com.damn.aisuper.engine.KeightJSModuleIntegrationTest"

# Run Node unit tests
Set-Location "D:\Work\Mobile\Android\AISuper\modules-ts\tests"
node --test *.test.mjs
```

## 10) Debugging checklist when a module call silently fails or errors

1. Check the **call syntax** in the feature script: `moduleId_functionName(...)` not `moduleId.functionName(...)`
2. Check **applet.json** `name` matches the `registerExports(...)` id exactly
3. Check the feature has `{ "type": "http", "name": "httpMain" }` if `httpGet` is used
4. Add `consoleLog` at module entry and after each async step
5. Look for missing Web APIs — especially `URLSearchParams`, `for...in const`, `Map`
6. After fixing module JS, **reinstall the app** (resources don't hot-reload on device)