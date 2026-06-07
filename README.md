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

# Reference implementation details

* App and UI framework: Compose Multiplatform  
* Flow graph format: XState  
* Block script format: JavaScript  
* Initial Block JavaScript engine: https://github.com/alexzhirkevich/keight

# UI and Core Architecture

The reference implementation is split into clearly separated layers so that the same applet logic can run in native UI or headlessly behind a web server.

## Core / Logic layer (`:shared`)

All applet execution lives in the `:shared` Kotlin Multiplatform module — no UI framework, no server, just pure logic:

- **`Applet`** — loads `applet.json`, manages feature lifecycle, exposes JS globals.
- **`Feature`** — loads layout JSON + feature script, attaches native modules, dispatches actions/values.
- **`AppJSEngine`** — abstracts the JavaScript sandbox (QuickJS via Keight on native/JVM; browser JS on web).
- **`FeatureModule`** / **`FeatureModuleHost`** — pluggable native modules (HTTP, audio, GPS, MCP, JS modules) that register functions into the JS context.
- **`StateStorage`** — hierarchical, scope-isolated state with transient and persistent backends (APPLET / FEATURE / MODULE / MODULE_GLOBAL scopes).
- **`HeadlessSessionManager`** / **`HeadlessSession`** — wraps `Applet` for server-side use; re-emits state changes as a `Flow<HeadlessSessionSnapshot>` for REST/SSE delivery.

## Applet Resources layer (`:applet-provider`)

Decouples *where* applet files live from *who* executes them:

- **`ComposeAppletProvider`** — reads from Compose Multiplatform bundled resources; works on Android, iOS, Desktop, and web-compose targets.
- **`AppletProviders`** (JVM) — classpath, filesystem, or ZIP strategies; used by the server and JVM tests.

The default applet is bundled here once and made available to all consumers.

## Native UI layer (`:composeApp`)

Renders applet widget trees using Compose Multiplatform on all native and web-compose targets:

- **`RikkaLayoutRenderer`** — primary Compose renderer (Rikka UI design system).
- **`Material3LayoutRenderer`** — alternative Material 3 Compose renderer.
- **`GlanceLayoutRenderer`** — renders applets as Android App Widgets via Jetpack Glance.
- Platform entry points: `MainActivity` (Android), `main.kt` (Desktop / web-compose), iOS glue.

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

## How it fits together

```
                    ┌────────────────────────────────────────────────────┐
                    │             :shared  (core logic)                  │
                    │  Applet · Feature · AppJSEngine · Modules          │
                    │  Storage · HeadlessSession                         │
                    └──────────────────────┬─────────────────────────────┘
                                           │
              ┌────────────────────────────┼──────────────────────────────────┐
              ▼                            ▼                                  ▼
  ┌─────────────────────┐    ┌──────────────────────────┐   ┌──────────────────────────┐
  │  :composeApp        │    │  :applet-provider        │   │  :server  (Ktor JVM)     │
  │  Compose renderers  │    │  ComposeAppletProvider   │   │  REST + SSE              │
  │  platform entries   │◄───│  AppletProviders (JVM)   │──►│  HeadlessSessionManager  │
  └─────────────────────┘    └──────────────────────────┘   └────────────┬─────────────┘
                                                                          │ REST / SSE
                                                             ┌────────────▼─────────────┐
                                                             │  client-react/  (npm)    │
                                                             │  React · WidgetRenderer  │
                                                             └──────────────────────────┘
```

# Custom Applet Loading

You can dynamically load external custom applets into the running application instead of relying on the bundled default applet.

### Format and Directory Structure Requirements
Custom applets must be either a **ZIP archive** or a **standard directory** (JVM only). The applet must contain the entry point manifest file `applet.json` directly at its root. Any internal resources (scripts, layouts, etc.) referenced in `applet.json` under `"files/"` paths (e.g. `"files/feature1.js"`) must be located inside a sibling `files/` directory.

```text
my-custom-applet/                <-- Root of the ZIP or Directory
├── applet.json                  <-- (Entry point)
└── files/
    ├── feature1.js              <-- (Loaded as "files/feature1.js")
    └── feature1.json            <-- (Loaded as "files/feature1.json")
```

### Loading on JVM Desktop
Pass the path to your custom applet ZIP or directory as a command-line argument:
```powershell
# Running the compiled desktop executable
.\com.damn.aisuper.exe "C:\path\to\my-applet.zip"
.\com.damn.aisuper.exe "C:\path\to\my-applet-directory"

# Running during development with Gradle
.\gradlew.bat :composeApp:run --args="C:\path\to\my-applet.zip"
```

*For more details on building the JVM Desktop app, see [JVM Application Packaging Guide](docs/JVM_APP_PACKAGING.md).*

### Loading on Android
On Android, only `.zip` applets are supported. You can load a custom applet using two methods:
1. **From a File Manager**: Tap on any `.zip` file. If prompted, choose "AISuper" from the "Open With..." dialog.
2. **From the App Shortcut**: Long-press the AISuper app icon on your home screen and select **Run custom applet...**. A native file picker will open allowing you to select your applet `.zip`.
