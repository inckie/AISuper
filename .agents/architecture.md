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

**Key Classes:**
*   `com.damn.aisuper.runtime.Applet` (manifest loading, feature switching, global actions)
*   `com.damn.aisuper.runtime.Feature` (layout/script load, feature values, action execution)

### 2. Layout System
The UI is driven by a data-driven layout system, not hardcoded Compose utility.
*   **Format**: JSON.
*   **Widgets**: `Column`, `Row`, `Text`, `TextField`, `Button`, `Image`.
*   **Layout Modifiers (JSON)**: `fillMaxSize`, `fillMaxWidth`, `weight`, `isScrollable`.
*   **Rendering**: A recursive Compose function (`RenderWidget`) maps the JSON object tree to Compose UI nodes.
*   **Dynamic Layout**: `ColumnWidget` can bind to a state variable (`dynamicChildrenId`) containing a JSON string definition of child widgets, allowing runtime UI updates.
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

