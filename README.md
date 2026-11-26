# AI Super App

# General overview

Currently there are a number of so-called "super applications", like Grab, WeChat, Uber Go, Yandex Go and so on. These applications include functionality of what used to be multiple separate applications, like banking app, taxi app, food delivery app.
The document proposes the next step of Super Application, where these separate applications, called "applets" can be built, loaded and executed dynamically based on user needs. Actual development of these applets is performed by strong "External" AI, that delivers a set of artifacts, which can be verified and signed. Super applications can load and execute these artifacts in safe sandbox environments, with permission guards.
Rationale: while in theory any functionality provided by applet can be performed using "Strong" LLM AI with ad-hock UI "dashboards" for visual presentation, there is a number of drawbacks for that generic case:

* In many cases it's more reasonable to have specialized self contained "closed loop" UI applications, like "Podcast player" or "Spending Analyzer"
* It's not always comfortable to use chat mode to operate the app.
* Running "Strong" AI is expensive and wasteful for performing basic functionality of say Audio Player
* Since "Strong" LLM by nature is stochastic, there is no guarantee of it having reproducible and safe behavior, which is usually expected from the typical purpose-built application.

# Building blocks

| Block | Provided by |
| :---- | :---- |
| Widget – visual component in UI: Label, Button, Slider, Image, Map, Video View, and so on. | Application runtime |
| Layout – spatial arrangement of Widgets or other Layouts. | Applet |
| Screen – container to present layout in a specific way: modal, regular | Application runtime |
| Navigation – stack of screens, can be hierarchical | Application runtime |
| Module – isolated and permission guarded building block providing MCP-like interface | Application runtime |
| Action – abstracted call to the module, widget update, navigation to another screen and so on. Some actions are "embedded", some are provided by Modules and Features. | Application runtime, applet |
| Use Case – primitive case of interaction with a system, like "pressing a button on specific screen when in specific state performs specific action" | Applet |
| Flow – a state machine that implements some Use Case or a group of Use Cases. **Represented as a graph so it can be verified**. | Applet |
| Pipeline – a chain of actions calls (both native and pure JS) with parameters passed as blobs and dictionaries. **Represented as a graph so it can be verified**. | Applet |
| Block – a sandboxed script that implements logical parts of the Flows, like calling of the actions or interaction with Modules. Can be run both locally or remotely. | Applet |
| Flow State – a set of variables and objects that keeps the current state of the Flow and all required data. Can be interacted with using actions. | Applet |
| Blob – text or binary named piece of data that can be shared/transferred between modules to avoid passing it though interfaces. AI modules should operate with blobs when dealing with data. For example, if large HTTP response must be passed to the Block, it's done wia Blob. Can be permanent (file or database), cache files, or in-memory | Applet |
| Global State – State that keeps information shared by all Flows. Access can be protected by permissions. | Applet |
| Feature – a connection of Widgets, Layouts, Screens, Flows | Applet |
| Permission – receiving user approval to perform specific action using a specific module in the context of the specific Flow or Use Case. Can be one time or permanent. | Applet |
| Applet – a full set of artifacts: Layouts, screens, Use Cases, Flows, and Blocks that work together as a single application from user point of view | Applet |

Every Layout file, Flow graph file, block script file, and entire feature can be digitally signed.  
Expected list of base Modules:

* HTTP calls module: can perform REST queries.  
* Audio Player (can play in the background)  
* Video Player (rich video player screen, can also have additional layouts on top, and include blocks as regular screen)  
* In-App purchases module  
* GPS module

# Runtime environment

"Super application" resembles a typical flexible Game engine, that can load external layouts, present screens in required way, execute Blocks in the sandboxes, persist Flow states, and request permissions. Can be UI, headless or both (background services inside Android application).
Application provides base Actions, like Present screen, and implements passing of data inside and between Flows.
The application has a built-in set of modules, like Audio Player, Video Player, Purchases, GPS, Http calls.  
The application also provides sandboxes to test each building block (Layout, Flow) or interaction with modules in isolation.  
Each block and module should come with a set of test cases for the sandbox. For modules it\`s a pre-generated layout with a list of cases that can be performed against the module, like "Start playing a song using Audio Player module", "Receive GPS coordinates", "Load available in-app purchases list", "Post local notification" and so on.

# Process of designing or expanding an applet

* The user provides an initial idea for the feature or even entire "applet".
* "External" AI with role "Feature designer" creates an initial specification and saves it.
* New session "External" AI with role "Developer" analyzes specification and generates a set of artifacts: Widgets, Layouts, Flows and Blocks.
* New session of External AI with role "verificator" and optionally human verifies generated artifacts. There are additional tools for verification of each block.
* After these steps are complete, artifacts are exported and then loaded into the runtime environment application.

# UI and Core Architecture

The reference implementation is split into clearly separated layers so that the same applet logic can run in native UI or headlessly behind a web server.

All Kotlin Multiplatform and Android Studio project files live in `kotlin/` (open `kotlin/` when launching Android Studio).

## Core / Logic layer (`:shared`)

All applet execution lives in the `:shared` Kotlin Multiplatform module — no UI framework, no server, just pure logic:

- **`Applet`** — loads `applet.json`, manages feature lifecycle, exposes JS globals.
- **`Feature`** — loads layout JSON + feature script, attaches native modules, dispatches actions/values.
- **`AppJSEngine`** — abstracts the JavaScript sandbox (QuickJS or Keight on native/JVM/desktop-native; Keight on JS/WASM).
- **`FeatureModule`** / **`FeatureModuleHost`** — pluggable native modules (HTTP, audio, GPS, MCP, JS modules) that register functions into the JS context.
- **`StateStorage`** — hierarchical, scope-isolated state with transient and persistent backends (APPLET / FEATURE / MODULE / MODULE_GLOBAL scopes).
- **`HeadlessSessionManager`** / **`HeadlessSession`** — wraps `Applet` for server-side and native FFI use; re-emits state changes as a `Flow<HeadlessSessionSnapshot>` for REST/SSE or C ABI delivery.

Desktop native targets (`mingwX64`, `linuxX64`, `macosArm64` via `desktopNativeMain`) are supported alongside Android, iOS, JVM, JS, and WASM.

**Important**: Keight JS engine while being an awesome piece of code was never intended to be used as a full blown JS runtime, so it has a lot of small issues.

## Applet Resources layer (`:applet-provider`)

Decouples *where* applet files live from *who* executes them:

- **`AppletProvider` / `AppletCatalog`** — shared `AppletEntry`, `ResolvedApplet`, and `AppletSourceKind` (`DIRECTORY`, `ZIP`) models.
- **`AppletProviders` & `JvmAppletScanner`** (JVM) — filesystem, ZIP, and directory-scanning strategies; scans a configurable default applets directory (defaults to `~/.aisuper/applets`) for folder applets and `.zip` archives.
- **Android providers** (`:composeApp`) — `AndroidLocalDirectoryAppletProvider`, `AndroidZipAppletProvider`, `AndroidSafDirectoryAppletProvider`, and `AndroidAppletScanner` (App Storage or user-selected SAF folder).

No default applet is baked into the app binary; applets are discovered dynamically or opened from any location.

## Native UI layer (`:composeApp`)

Renders applet widget trees using Compose Multiplatform on all native and web-compose targets:

- **`AppletPickerScreen` & `AppletStatusBar`** — TinyJS-inspired picker for the default applets directory, external folder/ZIP browsing, and a top status bar (`☰ Applets`, reload `↻`, and `+ Home` shortcut pinning on Android) while an applet is running.
- **`RikkaLayoutRenderer`** — primary Compose renderer (Rikka UI design system).
- **`Material3LayoutRenderer`** — alternative Material 3 Compose renderer.
- **`GlanceLayoutRenderer`** — renders applets as Android App Widgets via Jetpack Glance (discovering widget-capable features from the default applets directory).
- Platform entry points: `MainActivity` (Android, with SAF picking and home screen / dynamic launcher shortcuts), `main.kt` (Desktop with drag-and-drop / web-compose), iOS glue.

The Compose app simply collects the `layoutRoot` and `values` state flows from `Applet`/`Feature` and passes them to the renderer — no business logic here.

## Headless server (`:server`)

A Ktor/Netty JVM application that runs applets without any UI and exposes a REST + SSE API:

- `POST /sessions` — create a session, load an applet.
- `POST /sessions/{id}/action|value|module-command` — drive the applet.
- `GET /sessions/{id}/events` — SSE stream of `HeadlessSessionSnapshot` pushed on every state change.

This enables any HTTP client (browser, mobile, CLI) to interact with an applet remotely.

## React web client (`client-react/`)

A Vite + React + TypeScript browser app that connects to `:server`:

- **`api.ts`** — typed `serverApi` object wrapping all REST calls.
- **`App.tsx`** — session management and inline `EventSource` SSE subscription.
- **`WidgetRenderer.tsx`** — recursive renderer that maps the `HeadlessSessionSnapshot.layout` widget tree to React DOM elements; mirrors the role of `RikkaLayoutRenderer` on native platforms.
- **`types.ts`** — TypeScript mirror of the Kotlin layout model types.

## Native C++ Desktop Frontend (`:native-bridge` + `cui-app/`)

A lightweight, zero-JVM desktop frontend written in C++20 with [CUI (`v1.5.0`)](https://github.com/yappologistic/CUI) and `LunaSVG`, backed in-process by `:shared` compiled via Kotlin/Native:

- **`:native-bridge`** — compiles `:shared` (`desktopNativeMain`, QuickJS + Keight) into a shared library (`aisuper_core.dll` / `.so` / `.dylib`) exposing a 7-function flat `@CName` C ABI. Release builds strip COFF symbol tables automatically (~9.3 MB DLL).
- **`cui-app/`** — native C++20 desktop shell (~1.1 MB executable) supporting `.zip` archives (`miniz`), directory/`applet.json` loading, drag-and-drop, Fit (`contain`) image & SVG rendering (`LunaSVG` with `currentColor`), non-cancellable modal confirmations, and a live JSON Snapshot Inspector.

See [cui-app/README.md](cui-app/README.md) for build, run, and smoke-test instructions.

## How it fits together

```
                    ┌───────────────────────────────────────────────────────────┐
                    │                :shared  (core logic)                      │
                    │  Applet · Feature · AppJSEngine · Modules · Storage       │
                    │  HeadlessSession                                          │
                    └──┬──────────────────┬───────────────────┬─────────────────┘
                       │                  │                   │
       ┌───────────────▼──┐    ┌──────────▼─────────┐   ┌─────▼───────────────────┐
       │ :composeApp      │    │ :applet-provider   │   │ :server  (Ktor JVM)     │
       │ Picker/StatusBar │    │ AppletCatalog      │   │ REST + SSE              │
       │ Compose renderers│◄───│ JvmAppletScanner   │──►│ HeadlessSessionManager  │
       │ platform entries │    │ FS & ZIP Providers │   └──────────┬──────────────┘
       └──────────────────┘    └────────────────────┘              │ REST / SSE
                                                        ┌──────────▼──────────────┐
       ┌──────────────────┐                             │ client-react/  (npm)    │
       │ :native-bridge   │                             │ React · WidgetRenderer  │
       │ K/N shared lib   │                             └─────────────────────────┘
       │ (aisuper_core)   │
       └────────┬─────────┘
                │ C ABI (DLL/.so/.dylib)
       ┌────────▼─────────┐
       │ cui-app/ (C++20) │
       │ CUI · LunaSVG    │
       │ WidgetRenderer   │
       └──────────────────┘
```

# Applet Discovery and Loading

AISuper discovers and loads applets dynamically without any baked-in applet bundle.

### Format and Directory Structure Requirements
Applets can be a **ZIP archive** or a **standard directory**. The applet must contain the entry point manifest file `applet.json` at its root (or under `files/applet.json`, or a single top-level wrapper folder). Any internal resources referenced in `applet.json` under `"files/"` paths (e.g. `"files/feature1.js"`) must reside in a sibling `files/` directory:

```text
my-custom-applet/                <-- Root of the ZIP or Directory
├── applet.json                  <-- (Entry point)
└── files/
    ├── feature1.js              <-- (Loaded as "files/feature1.js")
    └── feature1.json            <-- (Loaded as "files/feature1.json")
```

### Default Applets Directory & Picker
When launched without arguments, AISuper scans the configured default applets directory for folder applets and `.zip` archives and displays the **Applet Picker**:
- **JVM Desktop**: Defaults to `~/.aisuper/applets` (configurable via **Change Folder** in the picker, saved in `~/.aisuper/aisuper_host.properties`). You can also browse for an external folder/`.zip`/`applet.json`, or drag and drop directly onto the window.
- **Android**: Defaults to `App Storage / applets` (`/sdcard/Android/data/com.damn.aisuper.androidapp/files/applets`), or any user-selected folder via **Change Folder** (Android Storage Access Framework). You can also browse external folders or `.zip` archives.
- **Running Status Bar**: While an applet is running, a top status bar provides quick access to **☰ Applets** (reopen picker while keeping the running applet alive), **↻** (reload), and on Android **+ Home** to pin a home screen shortcut (and register a dynamic launcher shortcut).

### Loading via CLI on JVM Desktop
Pass the path to your custom applet ZIP, directory, or `applet.json` as a command-line argument:
```powershell
# Running the compiled desktop executable
.\com.damn.aisuper.exe "C:\path\to\my-applet.zip"
.\com.damn.aisuper.exe "C:\path\to\my-applet-directory"

# Running during development with Gradle
.\gradlew.bat :composeApp:run --args="C:\path\to\my-applet.zip"
```

### Loading via Native Desktop (`cui-app`)
Launch without arguments and use the toolbar **Folder** / **.zip / .json** pickers or drag-and-drop onto the window, or pass an applet path on the CLI (see [cui-app/README.md](cui-app/README.md)):
```powershell
.\cui-app\build\aisuper_cui.exe "C:\path\to\my-applet-directory"
.\cui-app\build\aisuper_cui.exe "C:\path\to\my-applet.zip"
```

*For more details on building and packaging the JVM Desktop app, see the `applet-lifecycle` article in the `wikiknowledge-aisuper` Knowledge Base.*

### Additional Android Entry Points
1. **From a File Manager**: Tap on any `.zip` file. If prompted, choose "AISuper" from the "Open With..." dialog.
2. **From the App Shortcut**: Long-press the AISuper app icon on your home screen and select **Run custom applet...** (or any recently opened dynamic applet shortcut).
3. **Pinned Home Screen Shortcuts**: Tap **+ Home** in the running applet status bar to pin a dedicated shortcut for that applet to your home screen.
