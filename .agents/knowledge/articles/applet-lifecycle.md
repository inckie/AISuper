---
categories:
- system-overview
created: '2026-06-20T05:03:50.596219+00:00'
id: applet-lifecycle
modified: '2026-11-26T05:49:49.750764+00:00'
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

AISuper dynamically discovers and loads external applets without bundling any baked-in applet inside the application binary.

## Package Format
Applets can be delivered as a **ZIP archive** or an **unzipped root directory**. The manifest `applet.json` can reside at the root, under `files/applet.json`, or inside a single top-level wrapper folder. Internal resources referenced under `"files/"` paths must reside in a sibling `files/` directory:

```text
my-custom-applet/                <-- Pass this path as the CLI argument or ZIP root
├── applet.json                  <-- Root Manifest (Entry point)
└── files/
    ├── main_script.js           <-- Logic (loaded as "files/main_script.js")
    └── main_layout.json         <-- UI (loaded as "files/main_layout.json")
```

If a provided path is invalid or fails to load, the application displays a detailed error message and lets the user return to the Applet Picker.

## Default Applets Directory & Picker

When launched without a CLI/Intent target, AISuper scans the configured default applets directory for folder applets and `.zip` archives and presents the **Applet Picker**:

- **JVM Desktop**: Defaults to `~/.aisuper/applets` (configurable via **Change Folder**, persisted in `~/.aisuper/aisuper_host.properties`). Also supports **Browse Folder...**, **Browse .zip / .json...**, and drag-and-drop onto the window.
- **Android**: Defaults to `App Storage / applets` (`/sdcard/Android/data/com.damn.aisuper.androidapp/files/applets`), or any user-selected directory via **Change Folder** (Android Storage Access Framework `OpenDocumentTree`). Also supports **Browse Folder...** and **Browse .zip...**.
- **Running Status Bar**: While an applet is running, a top status bar provides **☰ Applets** (reopen picker while keeping the active applet alive), **↻** (reload), and on Android **+ Home** to pin a home screen shortcut.

## Loading Mechanisms

### JVM Desktop (`:composeApp`)
Path to the ZIP, directory, or `applet.json` can be passed as a command-line argument:
```powershell
# Standalone executable
.\com.damn.aisuper.exe "C:\path\to\my-applet.zip"
.\com.damn.aisuper.exe "C:\path\to\my-applet-directory"

# Gradle development run
.\gradlew.bat :composeApp:run --args="C:\path\to\my-applet.zip"
```

### Android
* **Applet Picker**: Scans the default directory (App Storage or selected SAF folder) or browses external folders / `.zip` files.
* **File Open**: Tapping a `.zip` file in a file manager and selecting "AISuper".
* **App Shortcut**: Long-pressing the app icon and selecting "Run custom applet..." or a recently opened dynamic applet shortcut.
* **Pinned Home Screen Shortcuts**: Tapping **+ Home** in the running applet status bar pins a shortcut (`ShortcutManagerCompat.requestPinShortcut`) that launches that applet directly via `com.damn.aisuper.ACTION_LAUNCH_APPLET`.

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
If launched without arguments, `:lanternaApp` scans the configured default applets directory and displays a terminal picker.

---

## Runtime Execution
Once loaded, the `Applet` core manages the lifecycle, switching between **Features** and maintaining [[state-management-and-persistence|State Storage]] (Transient or Persistent) across sessions.

---
**Implementation Details:** [[src:internals/applet-core]]