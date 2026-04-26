# AI Super App - Architecture Overview

## Overview
The AI Super App is a container application designed to load and execute dynamic "Applets". These applets are defined by a set of artifacts (Layouts, Scripts) and run within a sandboxed environment. The core goal is to allow an "External AI" to generate these applets to fulfill specific user needs on-the-fly.

## Core Components

### 1. Applet Runtime
The runtime loads an applet manifest and launches one feature at a time.
*   Loads applet artifacts (manifest, feature layout JSON, feature JavaScript block).
*   Manages current feature lifecycle and per-feature state.
*   Bridges UI events to the active feature scripting engine.
*   Exposes global and feature-scoped native functions to JavaScript.
*   Uses a generic module host/factory system to connect modules on demand from feature manifest declarations.

**Key Classes:**
*   `com.damn.aisuper.runtime.Applet` (manifest loading, feature switching, global actions)
*   `com.damn.aisuper.runtime.Feature` (layout/script load, feature values, action execution)

### 2. Layout System
The UI is driven by a data-driven layout system, not hardcoded Compose utility.
*   **Format**: JSON.
*   **Widgets**: `Column`, `Row`, `Text`, `TextField`, `Button`, `Image`, `AudioPlayer`, `Dropdown`.
*   **Layout Modifiers (JSON)**: `fillMaxSize`, `fillMaxWidth`, `weight`, `isScrollable`.
*   **Styling**: class-based style rules loaded from separate style JSON files (`styles/*.json`) and resolved at render time.
*   **Class Names**: each widget supports `classes: []` similar to CSS classes for targeted style overrides.
*   **Style Capabilities**: text color, container/background color, paddings, and corner radius.
*   **Rendering**: A recursive Compose function (`RenderWidget`) maps the JSON object tree to Compose UI nodes.
*   **Dynamic Layout**: `ColumnWidget` can bind to a state variable (`dynamicChildrenId`) containing widget JSON values, allowing runtime UI updates.
*   **Image Sources**: `Image` supports both remote `url` and inline `data` (e.g., `data:image/svg+xml;utf8,...`) for local/generated media.
*   **Action Payloads**: `Button` supports optional `actionArgs` (JSON array) propagated to `Applet.handleAction(action, args)` for per-item actions in dynamic lists.
*   **Screen Structure Pattern**: fixed header/footer with a weighted, scrollable middle region (e.g., chat and image list features).

**Key Classes:**
*   `com.damn.aisuper.layout.LayoutRoot`, `Widget` (Data Models via `kotlinx.serialization`).
*   `com.damn.aisuper.layout.LayoutRenderer.kt` (Rendering Logic).

### 3. Scripting Engine (The "Brain")
Business logic for applets is written in JavaScript. This allows the logic to be dynamic, hot-swappable, and sandboxed.
*   **Engine**: `Keight` (Kotlin Multiplatform JavaScript Runtime).
*   **Integration**: wraps the `Keight` engine to execute scripts and manage context.
*   **Bridge**:
    *   `getValue(key)`: Scripts can pull data from the Applet state.
    *   `setValue(key, value)`: Scripts can push updates to the UI.
    *   `httpGet(url)`: Scripts can performing blocking HTTP requests.
    *   `process()`, `initialize()`: Entry point functions called by Runtime.
*   **Resource Loading**: Uses `Res.getUri("files/...")` to resolve platform-specific resource paths (Android namespacing) before reading via `Res.readBytes()`.

**Key Interface:** `com.damn.aisuper.engine.AppJSEngine`
**Implementation:** `com.damn.aisuper.engine.KeightJSEngine`

## Modules
*   **HttpModule**: Provides HTTP GET functionality via Ktor Client.
*   **AudioPlayerModule**: Feature-scoped module that manages named players declared in manifest.
    *   Shared interface: `AudioPlayer` (`load`, `play`, `pause`, `stop`, `seek`, `state`).
    *   Factory: `createPlatformAudioPlayer(name)` via `expect/actual`.
    *   Android actual: basic stream playback with `MediaPlayer`.
    *   JVM/iOS/JS/Wasm actual: `NoopAudioPlayer` fallback for API/state/event wiring.
    *   **McpHttpFeatureModule**: Feature-scoped MCP client over HTTP JSON-RPC.
        *   Configurable per feature/server via module `config` (`url`, optional `groups`).
        *   Supports grouped tools on one server (e.g., `weather`, `miami_metromover`) by exposing `mcpCall(group, tool, args)`.
        *   Exposes `mcpListTools(group?)` and `mcpServerInfo()` into JS.
        *   **GeolocationModule**:
            *   JS API: `geoGetCurrent(ipOverride?)` returning normalized JSON with `success`, `latitude`, `longitude`, `source`, `error`.
            *   Android: uses OS `LocationManager` last-known location (GPS/network/passive) with runtime permission checks.
            *   Non-Android: GeoIP fallback via HTTP (`ipwho.is` or `hackertarget`) selected by build-time constant in code.

## Applet Styles and Themes
Applet manifest can declare reusable style files and default style:

```json
{
  "styles": {
    "light": { "file": "files/styles/light_style.json" },
    "dark": { "file": "files/styles/dark_style.json" }
  },
  "defaultStyle": "light"
}
```

Runtime behavior:
* `Applet` loads all declared style files at startup.
* Active style is exposed as `currentStyleSheet` flow and passed to `RenderWidget`.
* JavaScript can query and switch styles using global functions:
  * `getAvailableThemes()`
  * `getCurrentTheme()`
  * `setCurrentTheme(themeId)`
* `menu` feature uses `Dropdown` to switch style at runtime.

## Feature Manifest Extensions
Feature definitions now support module declarations:

```json
{
  "layout": "files/radio_layout.json",
  "script": "files/radio_script.js",
  "modules": [
    { "type": "audioPlayer", "name": "radioMain" }
  ]
}
```

This allows one feature to bind multiple independent player instances by name.

Applet manifest also supports reusable JavaScript modules declared once and imported by features:

```json
{
  "jsModules": {
    "onlineRadioBrowser": {
      "script": "files/online_radio_browser_module.js"
    }
  },
  "features": {
    "radio": {
      "modules": [
        { "type": "jsModule", "name": "onlineRadioBrowser" },
        { "type": "http", "name": "httpMain" }
      ]
    }
  }
}
```

Runtime behavior:
* `jsModule` imports are resolved from top-level `jsModules` by `name`.
* Imported JS modules run in isolated JS VMs and expose declared exports through Kotlin-registered proxy functions.
* Native modules (`http`, `audioPlayer`, `mcpHttp`, `geolocation`) continue using the module host/factory path.

## Audio JS Bridge
Feature runtime registers audio APIs into JS:
* `getAudioPlayers()`
* `audioLoad(player, url)`
* `audioPlay(player)`
* `audioPause(player)`
* `audioStop(player)`
* `audioSeek(player, positionMs)`
* `audioGetState(player)`
* `audioSubscribe(player, handlerFunctionName)`
* `audioUnsubscribe(player, handlerFunctionName?)`

Player state is mirrored into feature values using composite keys:
* `playerName.media.sourceUrl`
* `playerName.media.state`
* `playerName.media.positionMs`
* `playerName.media.durationMs`
* `playerName.media.error`

Subscribed handlers receive a JSON object payload with the same fields.

## Native Audio Widget (Optional)
Layout system includes `AudioPlayer` widget type:
* Binds directly to a named player (`player` field).
* Reads mirrored state from `values`.
* Sends native media commands (`play/pause/stop`) through Applet -> Feature, bypassing JS button actions.
* JS control remains fully supported, so feature authors can choose either path.

## Radio Search Feature Flow
`radio` feature now combines text input + HTTP + list rendering + native audio controls:
1. User types station name and triggers `findStations()`.
2. Script calls `httpGet("https://de1.api.radio-browser.info/json/stations/byname/{query}")`.
3. Script maps response to dynamic widgets (`Image`, `Text`, `Button`) in `stationList`.
4. Per-result `Play` button passes `[streamUrl, stationName]` via `actionArgs` into `playStation(url, name)`.
5. Script starts playback (`audioLoad`, `audioPlay`) and reveals `AudioPlayer` widget by setting `playerPanel`.

## Weather MCP Feature Flow
`weather` feature demonstrates MCP-backed tool invocation from the UI:
1. `initialize()` renders location buttons with action args `[lat, lon, cityName]`.
2. Optional `My Location` action uses `geoGetCurrent()` and then calls weather MCP for resolved coordinates.
3. Clicking a location triggers JS `loadWeather(lat, lon, name)`.
4. JS calls `mcpCall("weather", "get_current_weather", {...})` through MCP module.
5. MCP module sends JSON-RPC `tools/call` to configured server URL and returns parsed payload.
6. JS maps payload to dynamic widgets in `weatherResult`.

## Metromover MCP Feature Flow
`metromover` feature uses the same MCP server with the `miami_metromover` tool group:
1. `initialize()` loads loop IDs and station list via `mcpCall("miami_metromover", ...)`.
2. Feature renders dynamic station rows with per-row `Arrivals` button.
3. `loadArrivals(stationId, stationTitle)` fetches ETAs and appends readable loop ETA lines.
4. `loadLoopSvg(loopId)` requests loop map data and shows status metadata (SVG length) without rendering raw objects.
5. `findNearestStation()` uses `geoGetCurrent()` + `miami_metromover.find_nearest_station` and renders nearest station text.
6. All text conversion uses safe helpers to prevent `[object Object]` in widgets.
7. Loop SVG maps are converted to data URIs and rendered through the `Image` widget.

## Data Flow (MVP - Echo Chat)

1.  **User Action**: User clicks "Send" button in the UI.
2.  **Action Dispatch**: The `Button` widget triggers the action `"process"`.
3.  **Runtime Handling**: `Applet.handleAction("process")` is called.
4.  **Script Execution**: The `KeightJSEngine` executes the JavaScript function `process()`.
5.  **State Access (JS)**:
    *   JS calls `getValue("input_field")` -> Kotlin executes callback -> returns current text from `_values`.
6.  **Logic (JS)**: JS constructs the response string ("Echo: ...").
7.  **State Update (JS)**:
    *   JS calls `setValue("result_text", result)` -> Kotlin executes callback -> updates `_values` StateFlow.
8.  **UI Update**: The `App` composable collects `applet.values`, detects the change, and re-renders the `Text` widget with the new ID.

## Directory Structure
```
composeApp/src/commonMain/kotlin/com/damn/aisuper/
├── App.kt                # Main Entry Point (UI Shell)
├── engine/               # JavaScript Engine Integration
│   ├── JSEngine.kt       # Interface & Keight Implementation
├── layout/               # Layout System
│   ├── LayoutModels.kt   # JSON Serialization Models
│   └── LayoutRenderer.kt # recursive Compose Renderer
└── runtime/              # Applet Logic & State Management
    └── Applet.kt         # The "Controller"
```

## Future Roadmap (Implicit)
*   **Modules**: Add GPS, HTTP, etc., as injectable objects into the JS context.
*   **Navigation**: Support multiple screens and navigation stack.
*   **Security**: Sandbox the JS engine (timeout, memory limits, restricted imports).
*   **Validation**: Verify signatures of loaded artifacts.

## Module Runtime Abstraction
Module logic is no longer hardcoded in `Feature`.
* `FeatureModuleFactory` creates typed modules from manifest `modules[]` declarations.
* `FeatureModuleHost` attaches/detaches modules and routes native module commands by type.
* `FeatureModuleContext` exposes engine registration, value read/write, and script callback execution APIs.
* Default common factories: `http`, `audioPlayer`, `mcpHttp`; platform-specific factories can be provided via `platformFeatureModuleFactories()`.

