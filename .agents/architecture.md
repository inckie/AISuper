# AI Super App - Architecture Overview

## Overview
The AI Super App is a container application designed to load and execute dynamic "Applets". Applets are defined by a set of artifacts (manifest JSON, layout JSON, JS/TS scripts) and run inside a sandboxed environment. The core goal is to allow an "External AI" to generate these applets to fulfil specific user needs on-the-fly.

---

## Gradle Module Structure

```
:androidApp  ──► :composeApp ──► :shared
                      │               ▲
                      └──► :applet-provider ──► :shared
                                 ▲
:server ────────────────────────►┘
   (Ktor JVM)     also ──► :shared

client-react/ (npm/Vite)  ── REST/SSE ──► :server
modules-ts/   (npm/tsc)   ── compiled JS loaded at runtime by :shared engine
```

### `:shared`  *(Kotlin Multiplatform library)*
Targets: `android`, `iosArm64`, `iosSimulatorArm64`, `jvm`, `js`, `wasmJs`, `quickjsMain`.

The **pure logic core** – no Compose, no platform UI, no server wiring.
Contains:

| Sub-package | Contents |
|---|---|
| `runtime/` | `Applet`, `Feature`, `AppletManifest`, `AppletResourceLoader`, `StorageBindings`, `XmlJsonParser` |
| `engine/` | `AppJSEngine` interface, `KeightJSEngine` (QuickJS), `createAppJSEngine()` expect/actual, `JsonBridgeConversion` |
| `layout/` | Serializable widget model: `Widget` union, `LayoutRoot`, `StyleSheet`, `StyleRule`, `StyleTokens` |
| `modules/` | `FeatureModule`, `FeatureModuleContext`, `FeatureModuleHost`, `FeatureModuleFactory`; built-in impls: `http`, `audioPlayer`, `geolocation`, `jsModule`, `mcpHttp` |
| `storage/` | `StateStorage`, `InMemoryStateStorage`, `PersistentStateStorage`, `CompositeStateStorage`, `ScopedStateStorage` |
| `headless/` | `HeadlessSessionManager`, `HeadlessSession`, `HeadlessApiModels` (DTOs), `RemoteAppletClient` (Ktor HTTP client) |

### `:applet-provider`  *(Kotlin Multiplatform library)*
Targets: same as `:shared` + Compose Resources plugin.

Responsible for **supplying applet artifacts** to whoever needs them.

| Class | Description |
|---|---|
| `AppletProvider` interface | `fun createLoader(): AppletResourceLoader` |
| `ComposeAppletProvider` | Reads from Compose Multiplatform bundled resources (`commonMain/composeResources/files/`). Used by all Compose targets. |
| `AppletProviders` (jvmMain) | Factory: `classpath()`, `filesystem(path, fallbackToClasspath)`, `zip(path)`. Used by `:server` and JVM tests. |

Also **bundles the default applet** in both `composeResources/files/` (for Compose targets) and `jvmMain/resources/files/` (for plain classpath access on JVM/server).

### `:composeApp`  *(Kotlin Multiplatform app)*
Targets: `android`, `iosArm64`, `iosSimulatorArm64`, `jvm` (Desktop), `js` (Browser), `wasmJs` (Browser).

Responsible for **all Compose UI rendering and platform entry points**. No server logic here.

| Source set | Key files |
|---|---|
| `commonMain/layout/frontend/rikka/` | `RikkaLayoutRenderer.kt` (primary renderer), `RikkaFrontendTheme.kt` |
| `commonMain/layout/frontend/material3/` | `Material3LayoutRenderer.kt` |
| `commonMain/layout/` | `LayoutRenderUtils.kt`, `ColorUtils.kt` |
| `androidMain/layout/frontend/glance/` | `GlanceLayoutRenderer.kt` – App Widgets via Jetpack Glance |
| `androidMain/` | `MainActivity.kt`, `AISuperWidget.kt`, widget helpers |
| `jvmMain/` | `main.kt` – Desktop Compose window |
| `webMain/` | `main.kt` – Browser Compose canvas |

Loads applets via `ComposeAppletProvider` from `:applet-provider`.

### `:androidApp`  *(Android application shell)*
Thin Android app module that depends on `:composeApp`. Provides `AndroidManifest.xml`, signing, and packaging.

### `:server`  *(JVM application – Ktor)*
A **headless HTTP server** that runs applets server-side and exposes them over REST + SSE so web/remote clients can drive them.

Entry point: `ServerMain.kt`
1. Builds an `AppletResourceLoader` via `AppletProviders.filesystem(fallbackToClasspath = true)`.
2. Creates a `HeadlessSessionManager` (one `Applet` per session).
3. Starts Ktor/Netty on `:8080`.

REST API:

| Method | Path | Description |
|---|---|---|
| `GET` | `/health` | Liveness check |
| `POST` | `/sessions` | Create session; body `{"manifestPath": "..."}` |
| `GET` | `/sessions` | List active session IDs |
| `GET` | `/sessions/{id}/state` | Current state snapshot |
| `POST` | `/sessions/{id}/action` | Dispatch action; body `{"action","args"}` |
| `POST` | `/sessions/{id}/value` | Set widget value; body `{"id","value"}` |
| `POST` | `/sessions/{id}/module-command` | Module command; body `{"moduleType","target","command","args"}` |
| `GET` | `/sessions/{id}/events` | SSE stream – emits `event: state` on every state change |

### `client-react/`  *(Vite + React + TypeScript)*
**Browser UI** that connects to `:server` and renders applet layouts in the DOM.

| File | Responsibility |
|---|---|
| `src/api.ts` | `serverApi` object – all REST calls with content-type / error-body handling |
| `src/App.tsx` | Root component: session state, inline `EventSource` SSE, settings & debug panels |
| `src/WidgetRenderer.tsx` | Recursive widget-tree → React DOM renderer; focus registry; framework label |
| `src/types.ts` | TypeScript mirror of the Kotlin layout model types |
| `src/layoutUtils.ts` | `parseJsonInput()` safe JSON helper |

### `modules-ts/`  *(npm + TypeScript)*
TypeScript source for JavaScript modules loaded by the engine at runtime. Compiled output is bundled as applet resources.

---

## Data Flow

### Native Compose path (`:composeApp`)

```
Platform entry point (MainActivity / main.kt / …)
  └─ ComposeAppletProvider → AppletResourceLoader
       └─ Applet.loadApplet("files/applet.json")
            ├─ Parse AppletManifest
            ├─ Launch entry Feature
            │   ├─ Load layout JSON → Widget tree (LayoutRoot)
            │   ├─ Load + evaluate feature script (AppJSEngine / Keight)
            │   ├─ Attach FeatureModules via FeatureModuleHost
            │   └─ Call script.initialize()
            └─ currentFeature / layoutRoot / values (StateFlows)
                 └─ RikkaLayoutRenderer / Material3LayoutRenderer → Compose UI
```

### Headless / Web path (`:server` + `client-react`)

```
client-react (browser)
  POST /sessions  ──────────────────────────────────────────►
                                                    ServerMain (Ktor)
                                                      └─ HeadlessSessionManager.create(manifestPath)
                                                           └─ Applet.loadApplet(…)   [same core]
                                                                └─ HeadlessSession
                                                                     ├─ snapshot() → REST response
                                                                     └─ events Flow → SSE stream
  ◄── event: state ──────────────────────────────────────────────────────────────────────
  EventSource in App.tsx → WidgetRenderer.tsx → React DOM
```

### User action dispatch

```
UI event (Compose click / React button)
  ├─ [Native]  Feature.handleAction(action, args)
  └─ [Web]     POST /sessions/{id}/action → applet.handleAction(action, args)
                    └─ AppJSEngine.callFunction(actionName, args)
                         └─ JS feature script function
                              ├─ May call registered Kotlin module functions
                              ├─ May call setValue(key, value) → values StateFlow emits
                              └─ Layout / values changes → snapshot pushed to client (SSE / recompose)
```

---

## Core Component Details

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
The UI is driven by a data-driven layout system, not hardcoded Compose code.
*   **Format**: JSON.
*   **Widgets**: `Column`, `Row`, `Text`, `TextField`, `Button`, `Image`, `AudioPlayer`, `Dropdown`, `Switch`, `Spinner`, `Progress`.
*   **Layout Modifiers (JSON)**: `fillMaxSize`, `fillMaxWidth`, `weight`, `isScrollable`.
*   **Styling**: class-based style rules loaded from separate style JSON files and resolved at render time.
*   **Class Names**: each widget supports `classes: []` similar to CSS classes for targeted style overrides.
*   **Style Capabilities**: text color, container/background color, paddings, corner radius.
*   **Rendering**:
    *   **Native**: recursive Compose function (`RenderWidget` / `RikkaLayoutRenderer`) maps widget tree to Compose nodes.
    *   **Web**: `WidgetRenderer.tsx` in `client-react` maps widget tree to React DOM nodes.
*   **Dynamic Layout**: `ColumnWidget` can bind to a state variable (`dynamicChildrenId`) containing widget JSON values, allowing runtime UI updates.
*   **Image Sources**: `Image` supports both remote `url` and inline `data` (e.g., `data:image/svg+xml;utf8,...`).
*   **Action Payloads**: `Button` supports optional `actionArgs` (JSON array) propagated to `Applet.handleAction(action, args)`.
*   **Screen Structure Pattern**: fixed header/footer with a weighted, scrollable middle region.

**Key model location:** `shared/src/commonMain/kotlin/com/damn/aisuper/layout/`
**Compose rendering:** `composeApp/src/commonMain/kotlin/com/damn/aisuper/layout/frontend/`
**React rendering:** `client-react/src/WidgetRenderer.tsx`

### 3. Scripting Engine (The "Brain")
Business logic for applets is written in JavaScript / TypeScript.
*   **Engine**: `Keight` (Kotlin Multiplatform JavaScript Runtime, QuickJS-backed on Android/iOS/JVM).
*   **Bridge functions registered into JS context**:
    *   `getValue(key)` / `setValue(key, value)` – state I/O.
    *   `httpGet(url)` – blocking HTTP.
    *   `xmlParse(xml)` – XML → JSON.
    *   `jsonParse(json)` – JSON parse.
    *   `encodeURIComponent(s)` – URL encoding.
    *   `launchFeature(featureId)` – navigation.
    *   Module-specific functions added by each `FeatureModule.attach()`.
*   **Entry points called by runtime**: `initialize()`, `process()`.

**Key Interface:** `com.damn.aisuper.engine.AppJSEngine`
**Implementation:** `com.damn.aisuper.engine.keight.KeightJSEngine`

### 4. Modules
*   **HttpModule**: HTTP GET/POST via Ktor Client.
*   **AudioPlayerModule**: Named players declared in manifest; JS bridge: `audioLoad`, `audioPlay`, `audioPause`, `audioStop`, `audioSeek`, `audioGetState`, `audioSubscribe`, `audioUnsubscribe`. Player state mirrored into feature values as `playerName.media.*`.
*   **McpHttpFeatureModule**: MCP client over HTTP JSON-RPC. Exposes `mcpCall(group, tool, args)`, `mcpListTools(group?)`, `mcpServerInfo()`.
*   **GeolocationModule**: `geoGetCurrent(ipOverride?)` returning `{success, latitude, longitude, source, error}`. Android uses `LocationManager`; others use GeoIP HTTP fallback.
*   **JsModule**: Loads compiled TypeScript modules from `modules-ts/` as isolated JS VMs; exports exposed as Kotlin-registered proxy functions.

### 5. Headless Layer (`shared/headless/`)
Enables server-side and remote-client use of the applet runtime without any UI:

*   `HeadlessSession` wraps an `Applet` and reacts to all `currentFeature`, `values`, `layoutRoot`, `currentStyleSheet`, `currentFramework` state-flow changes, emitting `HeadlessSessionSnapshot` events on a `SharedFlow`.
*   `HeadlessSessionManager` is the session registry (create / get / list / close).
*   `HeadlessApiModels` are the serializable DTOs shared between `:server` and `client-react`.
*   `RemoteAppletClient` is a Kotlin Ktor-based client for consuming the headless API from another Kotlin process (e.g., tests, mobile client connecting to remote server).

### 6. Applet Styles and Themes
Applet manifest declares reusable style files:

```json
{
  "styles": {
    "light": { "file": "files/styles/light_style.json" },
    "dark":  { "file": "files/styles/dark_style.json" }
  },
  "defaultStyle": "light"
}
```

*   `Applet` loads all styles at startup; exposes `currentStyleSheet` flow.
*   JS can query/switch styles via `getAvailableThemes()`, `getCurrentTheme()`, `setCurrentTheme(themeId)`.

### 7. Feature Manifest (Module Declarations)

```json
{
  "layout": "files/radio_layout.json",
  "script": "files/radio_script.js",
  "modules": [
    { "type": "audioPlayer", "name": "radioMain" },
    { "type": "jsModule",    "name": "onlineRadioBrowser" },
    { "type": "http",        "name": "httpMain" }
  ]
}
```

Top-level `jsModules` in the applet manifest declares shared JS module scripts; features reference them by `name`.

---

## Storage Architecture

### Scope Hierarchy

```
APPLET (global)
  └─ readable by: all features and modules in the applet
  FEATURE (feature-local)
    └─ readable by: the active feature script + its attached modules
    MODULE (module-private, feature-isolated)
      └─ readable by: that specific module instance within that feature
    MODULE_GLOBAL (module-private, applet-wide)
      └─ readable by: same module type across all features in the applet
```

### Backends

| Backend | Class | Persistent | Typical use |
|---|---|---|---|
| Transient | `InMemoryStateStorage` | ❌ | Session-only caches, hot state |
| Persistent | `PersistentStateStorage` | ✅ | User preferences, durable caches |
| Hybrid | `CompositeStateStorage` | Both | Default feature context |
| Access-control wrapper | `ScopedStateStorage` | — | Enforces `maxScope` per caller |

### Backend key namespacing

| Scope | Key format |
|---|---|
| APPLET | `applet:{appletId}/{key}` |
| FEATURE | `feature:{appletId}/{featureId}/{key}` |
| MODULE | `module:{appletId}/{featureId}/{moduleName}/{key}` |
| MODULE_GLOBAL | `module.global:{appletId}/{moduleName}/{key}` |

---

## Audio JS Bridge
Feature runtime registers into JS:
`getAudioPlayers()`, `audioLoad(player, url)`, `audioPlay(player)`, `audioPause(player)`,
`audioStop(player)`, `audioSeek(player, positionMs)`, `audioGetState(player)`,
`audioSubscribe(player, handlerName)`, `audioUnsubscribe(player, handlerName?)`.

Player state mirrored into feature values:
`playerName.media.sourceUrl`, `.state`, `.positionMs`, `.durationMs`, `.error`.

---

## Notable Feature Flows

### Radio Search
1. User types station name → `findStations()`.
2. Script calls `httpGet(radio-browser API)`.
3. Maps response to dynamic widgets (`Image`, `Text`, `Button`) in `stationList`.
4. Per-result `Play` button passes `[streamUrl, stationName]` via `actionArgs` → `playStation()`.
5. Script calls `audioLoad`, `audioPlay`; reveals `AudioPlayer` widget.

### Weather MCP
1. `initialize()` renders location buttons.
2. `My Location` uses `geoGetCurrent()` + weather MCP tool.
3. `loadWeather(lat, lon, name)` calls `mcpCall("weather", "get_current_weather", {...})`.
4. Payload mapped to dynamic widgets in `weatherResult`.

### Metromover MCP
1. `initialize()` loads loop IDs and stations via `mcpCall("miami_metromover", ...)`.
2. Per-row `Arrivals` button → `loadArrivals(stationId)` fetches ETAs.
3. `findNearestStation()` uses `geoGetCurrent()` + `find_nearest_station` tool.
4. SVG loop maps fetched and rendered as data-URI `Image` widgets.

---

## Future Roadmap
*   Storage encryption for sensitive data.
*   Cross-feature state sharing protocols.
*   Module hot-reload.
*   State snapshots and undo/redo.
*   Navigation: multi-screen stack.
*   Security: JS sandbox timeout / memory limits / restricted imports.
*   Artifact digital signature verification.
