# AISuper Architecture Overview

This document provides a comprehensive overview of the AISuper system architecture,
including all Gradle/npm modules, their responsibilities, and their interactions.

## High-Level Module Map

```
┌──────────────────────────────────────────────────────────────────────────┐
│                         :shared  (KMP library)                            │
│   runtime │ engine │ layout │ modules │ storage │ headless                │
│   Android · iOS · JVM · JS · wasmJS · quickJS                            │
└───────┬─────────────────────────────────────────┬────────────────────────┘
        │                                         │
┌───────▼──────────────┐               ┌──────────▼──────────────────┐
│  :composeApp  (KMP)  │               │  :applet-provider  (KMP)    │
│  Compose UI +        │               │  Resource loading:          │
│  platform entries    │               │  classpath · FS · ZIP       │
│  Android/iOS/Desktop │               │  + bundled default applet   │
│  /Web (Compose)      │               └──────────┬──────────────────┘
└──────────────────────┘                          │
        ▲                                         │
        │ depends on                    ┌─────────▼─────────────────┐
┌───────┴─────────┐                    │  :server  (JVM – Ktor)    │
│  :androidApp    │                    │  REST + SSE API            │
│  (Android shell)│                    └─────────┬─────────────────┘
└─────────────────┘                              │ REST / SSE
                                       ┌─────────▼─────────────────┐
                                       │  client-react/  (npm)     │
                                       │  React + TypeScript       │
                                       │  Browser UI               │
                                       └───────────────────────────┘

modules-ts/ (npm/tsc) – TypeScript source compiled to JS, loaded by :shared engine at runtime
```

---

## Module Descriptions

### `:shared`
**Type**: Kotlin Multiplatform library
**Targets**: `android`, `iosArm64`, `iosSimulatorArm64`, `jvm`, `js`, `wasmJs`, `quickjsMain`

The **pure logic core** – no Compose, no platform UI, no HTTP server.
Every other module depends on this one.

#### Sub-packages

| Package | Key Classes | Responsibility |
|---------|-------------|----------------|
| `runtime/` | `Applet`, `Feature`, `AppletManifest`, `AppletResourceLoader`, `StorageBindings`, `XmlJsonParser` | Applet lifecycle, feature loading, JS bridge wiring |
| `engine/` | `AppJSEngine`, `KeightJSEngine`, `createAppJSEngine()`, `JsonBridgeConversion` | JS execution environment (QuickJS via Keight) |
| `layout/` | `Widget` (union type), `LayoutRoot`, `StyleSheet`, `StyleRule`, `StyleTokens` | Platform-agnostic JSON layout model – no rendering |
| `modules/` | `FeatureModule`, `FeatureModuleContext`, `FeatureModuleHost`, `FeatureModuleFactory`; impls: `http`, `audioPlayer`, `geolocation`, `jsModule`, `mcpHttp` | Native Kotlin modules that extend JS context |
| `storage/` | `StateStorage`, `InMemoryStateStorage`, `PersistentStateStorage`, `CompositeStateStorage`, `ScopedStateStorage` | Hierarchical, scope-isolated state |
| `headless/` | `HeadlessSessionManager`, `HeadlessSession`, `HeadlessApiModels`, `RemoteAppletClient` | Server-side session lifecycle + REST/SSE DTOs |

### `:applet-provider`
**Type**: Kotlin Multiplatform library (with Compose Resources)
**Targets**: same as `:shared`

Decouples **where applet files come from** from the runtime that executes them.

| Class | Description |
|-------|-------------|
| `AppletProvider` | Interface: `fun createLoader(): AppletResourceLoader` |
| `ComposeAppletProvider` | Reads from Compose Multiplatform bundled resources (`composeResources/files/`). Works on all Compose targets. |
| `AppletProviders` (jvmMain) | Factory: `classpath()`, `filesystem(path, fallbackToClasspath)`, `zip(path)`. Used by `:server` and JVM tests. |

Also **bundles the default applet** in `composeResources/files/` (Compose targets) and `jvmMain/resources/files/` (JVM classpath).

### `:composeApp`
**Type**: Kotlin Multiplatform application
**Targets**: `android`, `iosArm64`, `iosSimulatorArm64`, `jvm`, `js`, `wasmJs`

All **Compose UI rendering and platform entry points**. No server logic.

| Source set | Content |
|------------|---------|
| `commonMain/layout/frontend/rikka/` | `RikkaLayoutRenderer.kt` (primary renderer), `RikkaFrontendTheme.kt` |
| `commonMain/layout/frontend/material3/` | `Material3LayoutRenderer.kt` (alternative renderer) |
| `commonMain/layout/` | `LayoutRenderUtils.kt`, `ColorUtils.kt` |
| `androidMain/layout/frontend/glance/` | `GlanceLayoutRenderer.kt` – Jetpack Glance for App Widgets |
| `androidMain/` | `MainActivity.kt`, `AISuperWidget.kt`, widget helpers |
| `jvmMain/` | Desktop Compose window entry |
| `webMain/` | Browser Compose canvas entry |
| `iosMain/` | iOS UIKit/SwiftUI integration glue |

### `:androidApp`
Thin Android application shell (manifest, signing, packaging). Depends on `:composeApp`.

### `:server`
**Type**: JVM application (Kotlin JVM + Ktor/Netty)

Runs applets **headlessly** on the server and exposes them via HTTP REST + SSE, enabling browser and remote clients to drive them without a native runtime.

Startup sequence (`ServerMain.kt`):
1. `AppletProviders.filesystem(fallbackToClasspath = true)` → `AppletResourceLoader` (custom dirs first, then bundled default).
2. `HeadlessSessionManager` with one `Applet` per session.
3. Ktor/Netty starts on `:8080`.

#### REST + SSE API

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/health` | Liveness check → `{"ok": true}` |
| `POST` | `/sessions` | Create session; body `{"manifestPath": "files/applet.json"}` → `CreateSessionResponse` |
| `GET` | `/sessions` | List active session IDs |
| `GET` | `/sessions/{id}/state` | Snapshot current applet state |
| `POST` | `/sessions/{id}/action` | Dispatch action; body `{"action": "name", "args": [...]}` → updated snapshot |
| `POST` | `/sessions/{id}/value` | Set widget value; body `{"id": "widgetId", "value": ...}` → updated snapshot |
| `POST` | `/sessions/{id}/module-command` | Module command; body `{"moduleType", "target", "command", "args"}` |
| `GET` | `/sessions/{id}/events` | SSE stream; emits `event: state` on every applet state change |

#### Snapshot format (`HeadlessSessionSnapshot`)

```json
{
  "sessionId": "uuid",
  "reason": "update",
  "featureId": "dashboard",
  "values": { "widgetId": <JsonElement> },
  "layout": { "layout": { "type": "Column", ... } },
  "styleSheet": { "scheme": "dark", "tokens": {...}, "classes": {...} },
  "framework": "Rikka"
}
```

### `client-react/`
**Type**: Vite + React + TypeScript (npm project)

**Browser UI** that connects to `:server` and renders applet layouts in the DOM.
No Kotlin dependencies.

| File | Responsibility |
|------|----------------|
| `src/api.ts` | `serverApi` object – all REST calls with content-type / error-body handling; also `health`, `listSessions`, `getState`. |
| `src/App.tsx` | Root component: session lifecycle, inline `EventSource` SSE, settings & debug panels. |
| `src/WidgetRenderer.tsx` | Recursive widget-tree → React DOM renderer; handles focus registry, framework label. |
| `src/types.ts` | TypeScript mirror of the Kotlin layout model (`Widget` union, `HeadlessSessionSnapshot`, `StyleSheet`, etc.). |
| `src/layoutUtils.ts` | `parseJsonInput()` – safe JSON parse for debug panel inputs. |

### `modules-ts/`
TypeScript source for JavaScript modules loaded by the engine at runtime.
Compiled output is bundled as applet resource files.

---

## Data Flow

### Native Compose path

```
Platform entry (MainActivity / main.kt / …)
  │
  └─ ComposeAppletProvider → AppletResourceLoader
       │
       └─ Applet.loadApplet("files/applet.json")
            │
            ├─ Parse AppletManifest
            ├─ Launch entry Feature
            │   ├─ Load layout JSON → Widget tree (LayoutRoot)
            │   ├─ Load + evaluate feature script (AppJSEngine / Keight)
            │   ├─ Create FeatureModuleContext (transient + persistent storage)
            │   ├─ Attach FeatureModules via FeatureModuleHost
            │   │   └─ Each module registers its own JS functions
            │   └─ Call script.initialize()
            │
            └─ currentFeature / layoutRoot / values (StateFlows)
                 │
                 └─ RikkaLayoutRenderer / Material3LayoutRenderer / GlanceLayoutRenderer
                      └─ Compose / Glance UI
```

### Headless / Web path

```
client-react (browser)
  │
  ├─ POST /sessions  ──────────────────────────────────────► ServerMain (Ktor)
  │                                                              └─ HeadlessSessionManager.create(…)
  │                                                                   └─ Applet.loadApplet(…)  [same core]
  │                                                                        └─ HeadlessSession
  │                                                                             ├─ snapshot() → REST JSON
  │                                                                             └─ events Flow → SSE
  │
  ├─ GET /sessions/{id}/events ──────────────────────────── EventSource in App.tsx
  │         event: state payload ◄──────────────────────────────────────────────
  │                   │
  │                   └─ WidgetRenderer.tsx → React DOM
  │
  └─ POST /sessions/{id}/action  (on user interaction)
```

### Feature Initialization (detailed)

```
Applet.launchFeature(featureId)
  │
  ├─ Create Feature instance
  ├─ Create FeatureModuleHost
  ├─ Register global JS functions
  │   ├─ xmlParse, jsonParse
  │   ├─ encodeURIComponent
  │   └─ launchFeature (navigation)
  │
  ├─ Load layout JSON
  ├─ Load feature script
  │
  ├─ Create FeatureModuleContext
  │   ├─ Feature script context: transient + persistent (APPLET/FEATURE only)
  │   └─ Per-module context: transient + persistent (APPLET/FEATURE/MODULE/MODULE_GLOBAL)
  │
  ├─ Attach all modules via FeatureModuleHost
  │   └─ Modules register their own functions
  │
  └─ Call feature script initialize()
```

### User Action Handling

```
UI Widget (onAction)
  │
  └─ applet.handleAction(actionName, args)
       │
       └─ feature.handleAction(actionName, args)
            │
            └─ engine.callFunction(actionName, args)
                 │
                 └─ Executes JS function with parameters
                      │
                      └─ May call registered Kotlin functions
                           └─ Modules/Features may update storage / values
```

---

## Core Components

### 1. Applet (Root Container)
**File**: `shared/src/commonMain/kotlin/.../runtime/Applet.kt`

- Load applet configuration from `applet.json`
- Create and manage features
- Provide global JS functions (xmlParse, jsonParse, encodeURIComponent, launchFeature, theme functions)
- Manage applet-level storage (`StorageScope.APPLET`)
- Expose `currentFeature`, `currentStyleSheet`, `currentFramework` as `StateFlow`

### 2. Feature
**File**: `shared/src/commonMain/kotlin/.../runtime/Feature.kt`

- Parse and render layout
- Load and execute feature scripts
- Attach modules and provide `FeatureModuleContext`
- Manage feature-level storage (`StorageScope.FEATURE`)
- Handle `handleAction`, `updateValue`, `handleModuleCommand`

### 3. Layout System
**Model**: `shared/src/commonMain/kotlin/.../layout/`
**Compose rendering**: `composeApp/src/commonMain/kotlin/.../layout/frontend/`
**React rendering**: `client-react/src/WidgetRenderer.tsx`

- JSON-based, declarative widget trees
- Multi-frontend support: **Rikka UI** (primary Compose), **Material3** (alternative Compose), **Glance** (App Widgets), **React DOM** (web)
- Style sheets with tokens, class-based rules, and runtime theme switching

### 4. AppJSEngine
**File**: `shared/src/commonMain/kotlin/.../engine/`

- `AppJSEngine`: common interface
- `KeightJSEngine`: QuickJS-based implementation (Android, iOS, JVM, Desktop)
- `createAppJSEngine()`: expect/actual per platform
- `JsonBridgeConversion`: `JsonElement` ↔ JS value conversion

### 5. Feature Modules
**File**: `shared/src/commonMain/kotlin/.../modules/`

- `FeatureModule` / `FeatureModuleContext` / `FeatureModuleHost` / `FeatureModuleFactory`
- Built-in: `http`, `audioPlayer`, `geolocation`, `jsModule`, `mcpHttp`
- Modules register functions into JS context via `context.registerFunction` / `context.registerSuspendFunction`

### 6. Headless Layer
**File**: `shared/src/commonMain/kotlin/.../headless/`

- `HeadlessSession` bridges `Applet` state flows → `SharedFlow<HeadlessSessionSnapshot>`
- `HeadlessSessionManager` manages session lifecycle
- `HeadlessApiModels` — serializable DTOs
- `RemoteAppletClient` — Ktor client for Kotlin consumers of the headless API

### 7. AppletProvider
**File**: `applet-provider/src/`

- `AppletProvider` interface → `AppletResourceLoader`
- `ComposeAppletProvider` — reads from `composeResources/` (all Compose platforms)
- `AppletProviders` (JVM) — classpath, filesystem, ZIP strategies

---

## Storage Architecture

### Scope Hierarchy and Access Rules

```
APPLET Scope (Global)
  └─ Visible to: All features and modules in the applet
  └─ Persistence: Transient + Persistent

  FEATURE Scope (Feature-Level)
    └─ Visible to: The active feature script + modules attached to that feature
    └─ Persistence: Transient + Persistent

    MODULE Scope (Module-Private, feature-isolated)
      └─ Visible to: Only that module instance within that feature

    MODULE_GLOBAL Scope (Module-Private, applet-wide)
      └─ Visible to: Same module type across all features in the applet

Access enforcement (via ScopedStateStorage):
- Feature script context (maxScope = FEATURE) → APPLET, FEATURE
- Module context (maxScope = MODULE_GLOBAL, moduleName set) → all scopes
- MODULE / MODULE_GLOBAL require moduleName in StorageContext
- JS bridge exposes only "applet" and "feature" scope strings
```

### Storage Implementation

| Backend | Scope Coverage | Persistence | Typical Use Case |
|---------|----------------|-------------|------------------|
| `InMemoryStateStorage` | All scopes | ❌ | Session-only state, hot caches |
| `PersistentStateStorage` (KStorage) | All scopes | ✅ | Preferences, durable caches |

Platform KStorage backends:
- **Android**: `SharedPreferences`
- **iOS**: `UserDefaults`
- **Desktop/JVM**: file-based properties store
- **Web**: `LocalStorage`

Scope namespacing:
- `APPLET` → `applet:{appletId}/{key}`
- `FEATURE` → `feature:{appletId}/{featureId}/{key}`
- `MODULE` → `module:{appletId}/{featureId}/{moduleName}/{key}`
- `MODULE_GLOBAL` → `module.global:{appletId}/{moduleName}/{key}`

---

## Module System

### Native Kotlin Modules

```kotlin
class MyModule : FeatureModule {
    override suspend fun attach(context: FeatureModuleContext) {
        context.registerSuspendFunction("getData") { args ->
            val key = args[0].jsonPrimitive.content
            context.storage.getObject(StorageScope.FEATURE, key) ?: JsonNull
        }
    }
    override fun detach() { /* cleanup */ }
}
```

### JavaScript Modules (`modules-ts/`)

TypeScript modules compiled and bundled as applet resources:
- `xmlParse()`, `httpGet()` – global bridge functions available in all scripts
- `registerExports()` – declares the module's exported functions to the Kotlin host
- Loaded per-feature via `{ "type": "jsModule", "name": "..." }` in the manifest

---

## Configuration

### applet.json (Manifest)
```json
{
  "entryFeature": "dashboard",
  "styles": {
    "light": { "file": "files/styles/light.json" },
    "dark":  { "file": "files/styles/dark.json" }
  },
  "defaultStyle": "light",
  "jsModules": {
    "radioBrowser": { "script": "files/radio_browser_module.js" }
  },
  "features": {
    "dashboard": {
      "name": "Dashboard",
      "layout": "files/features/dashboard.json",
      "script": "files/features/dashboard.js",
      "modules": [
        { "type": "http",        "name": "httpMain" },
        { "type": "audioPlayer", "name": "player" },
        { "type": "jsModule",    "name": "radioBrowser" }
      ]
    }
  }
}
```

---

## Platform-Specific Notes

### Android
- JS engine: QuickJS (via Keight)
- Storage: SharedPreferences (KStorage)
- UI: Compose + Glance (App Widgets)

### iOS
- JS engine: QuickJS (via Keight)
- Storage: UserDefaults (KStorage)
- UI: Compose Multiplatform

### Desktop/JVM
- JS engine: QuickJS (via Keight)
- Storage: file-based (KStorage)
- UI: Compose for Desktop

### Web / Browser (Compose)
- JS engine: native browser JS
- Storage: LocalStorage (KStorage)
- UI: Compose for Web (canvas)

### Web / Browser (React)
- No Kotlin; connects to `:server` via REST/SSE
- UI: React + TypeScript (`client-react/`)
- Storage: handled server-side inside the session's `Applet`

---

## Key Design Patterns

1. **Scope-Based Isolation** – modules only see what they should; enforced at `ScopedStateStorage`.
2. **Type-Safe Storage** – `getString` returns `String?`, `getInt` returns `Int?`, no casting.
3. **Null-Safe API** – all storage reads return `null` for missing keys, no exceptions.
4. **Lazy Initialization** – features and modules load only when needed.
5. **Resource Management** – cleanup via `detach()` and `close()`.
6. **Headless / UI parity** – the same `Applet` + `Feature` core runs both in native Compose UI and as a headless server session; only the rendering and event delivery layer differs.

---

## Security Model

1. **Scope Enforcement**: `ScopedStateStorage` prevents cross-scope access
2. **Script Isolation**: JS runs in a sandboxed engine; only explicitly registered functions are callable
3. **Function Registration**: modules declare exactly what they expose to JS
4. **Type Validation**: automatic conversion via `JsonBridgeConversion`
5. **No Direct File Access**: all I/O routed through modules

---

## Performance Optimization

- In-memory caches for hot data; persistent cache for frequently accessed data
- Lazy loading of modules and features
- Batched writes where possible
- SSE pushes avoid polling; `SharedFlow(replay=1)` ensures late subscribers get latest state

---

## Testing

- **Unit**: test features in isolation; mock `AppJSEngine`; mock storage
- **Integration**: full applet loading; feature + module interaction; storage persistence
- **Runtime**: widget rendering verification; performance profiling; memory leak detection

---

## Future Enhancements

- [ ] Storage encryption for sensitive data
- [ ] Cross-feature state sharing protocols
- [ ] Module hot-reload
- [ ] State snapshots and undo/redo
- [ ] Storage inspection tools
- [ ] Performance metrics collection
- [ ] A/B testing framework
- [ ] Remote feature loading
- [ ] JS sandbox: timeout, memory limits, restricted imports
- [ ] Artifact digital signature verification
