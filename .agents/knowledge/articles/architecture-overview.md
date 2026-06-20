---
categories:
- system-overview
- architecture
created: '2026-06-20T04:50:15.468213+00:00'
id: architecture-overview
modified: '2026-06-20T05:04:01.339489+00:00'
tags:
- architecture
- overview
- kotlin
- multiplatform
- compose
title: Architecture Overview
type: leaf
---

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