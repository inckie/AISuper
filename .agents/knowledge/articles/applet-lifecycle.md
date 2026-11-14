---
categories:
- system-overview
created: '2026-06-20T05:03:50.596219+00:00'
id: applet-lifecycle
modified: '2026-11-14T03:00:15.357704+00:00'
tags:
- lifecycle
- loading
- android
- jvm
- deployment
title: Applet Lifecycle and Loading
type: leaf
---

# Applet Lifecycle and Loading

AISuper allows for dynamic loading of external applets, bypassing the need for a full application update.

## Package Format
Applets must be delivered as a **ZIP archive** or an **unzipped root directory** (JVM / TinyJS). The root must contain `applet.json`, and any internal resources referenced under `"files/"` paths must reside in a sibling `files/` directory:

```text
my-custom-applet/                <-- Pass this path as the CLI argument or ZIP root
├── applet.json                  <-- Root Manifest (Entry point)
└── files/
    ├── main_script.js           <-- Logic (loaded as "files/main_script.js")
    └── main_layout.json         <-- UI (loaded as "files/main_layout.json")
```

If a provided path is invalid or fails to load, the application displays a detailed error message rather than silently falling back to the bundled default applet.

## Loading Mechanisms

### JVM Desktop (`:composeApp`)
Path to the ZIP or directory is passed as a command-line argument to the packaged executable or via Gradle:
```powershell
# Standalone executable
.\com.damn.aisuper.exe "C:\path\to\my-applet.zip"
.\com.damn.aisuper.exe "C:\path\to\my-applet-directory"

# Gradle development run
.\gradlew.bat :composeApp:run --args="C:\path\to\my-applet.zip"
```

### Android
* **File Open**: Tapping a `.zip` file in a file manager and selecting "AISuper".
* **App Shortcut**: Long-pressing the app icon and selecting "Run custom applet...".

---

## JVM Desktop & TUI Packaging

### 1. Standalone Native Directory (Recommended)
Compiles `:composeApp` into a self-contained directory (`composeApp/build/compose/binaries/main/app/com.damn.aisuper/`) with a native launcher (`com.damn.aisuper.exe`) and a stripped-down bundled JRE (no system Java required):
```powershell
.\gradlew.bat :composeApp:createDistributable         # Debug
.\gradlew.bat :composeApp:createReleaseDistributable  # Release
```

### 2. Single Executable JAR (Uber JAR)
Packages `:composeApp` into a single fat JAR (`composeApp/build/compose/binaries/main/default/com.damn.aisuper-1.0.0.jar`, requires a JRE on the target machine):
```powershell
.\gradlew.bat :composeApp:packageUberJarForCurrentOS         # Debug
.\gradlew.bat :composeApp:packageReleaseUberJarForCurrentOS  # Release
```
Run with: `java -jar com.damn.aisuper-1.0.0.jar`

### 3. Lanterna TUI Application (`:lanternaApp`)
Packages the terminal UI runtime into a standalone native directory (`lanternaApp/build/distributions/app-image/lanternaApp/lanternaApp.exe`):
```powershell
.\gradlew.bat :lanternaApp:createDistributable
.\lanternaApp\build\distributions\app-image\lanternaApp\lanternaApp.exe "C:\path\to\my-applet-directory"
```

---

## Runtime Execution
Once loaded, the `Applet` core manages the lifecycle, switching between **Features** and maintaining [[state-management-and-persistence|State Storage]] (Transient or Persistent) across sessions.

---
**Implementation Details:** [[src:internals/applet-core]]