# AISuper Native C++ Frontend (`cui-app`)

A lightweight, zero-JVM desktop frontend for AISuper written in C++20 on top of [yappologistic/CUI (`v1.5.0`)](https://github.com/yappologistic/CUI). Rather than re-implementing applet execution in another runtime, `cui-app` dynamically loads AISuper's existing `:shared` Kotlin Multiplatform core compiled via Kotlin/Native (`:native-bridge`) into a native shared library (`aisuper_core.dll` / `.so` / `.dylib`).

## Architecture Overview

```text
cui-app (C++20, ~1.1 MB exe)
  ├── CoreBridge (src/core_bridge.*)        ── dynamic C ABI (LoadLibraryW / dlopen)
  ├── AppletLoader (src/applet_loader.*)    ── directory, applet.json, .zip (miniz 3.0.2)
  ├── StyleResolver (src/style_resolver.*)  ── StyleSheet rules + light/dark palette tokens
  ├── ImageCache (src/image_cache.*)        ── WinHTTP/disk/applet fetch + stb_image + LunaSVG v3.5.0
  ├── WidgetRenderer (src/widget_renderer.*)── JSON Layout tree -> CUI widgets (Fit contain images)
  └── main.cpp                              ── App chrome, file pickers, persistent modals, Snapshot Inspector
        │
        ▼ (7-function C ABI in include/aisuper_core.h)
kotlin/native-bridge (:native-bridge -> aisuper_core.dll, ~9.3 MB stripped)
  └── kotlin/shared (:shared desktopNativeMain)
        ├── QuickJS + Keight JS engines
        ├── Ktor HttpClient (WinHttp / Curl)
        ├── File-backed PersistentStateStorage
        └── HeadlessSession + ModalHostState (atomic snapshots + modal callbacks)
```

## Key Features

- **In-Process Kotlin/Native Core**: Runs the exact `:shared` `Applet` / `Feature` / `HeadlessSession` logic used on Android, JVM, and Ktor Server, with both **QuickJS** and **Keight** JS engines enabled.
- **Flat Dynamic C ABI (`include/aisuper_core.h`)**: Loads `aisuper_core` dynamically (`LoadLibraryW`/`GetProcAddress` on Windows, `dlopen`/`dlsym` on Linux/macOS) through 7 C exports (`aisuper_session_create`, `aisuper_session_load_applet`, `aisuper_session_send_action`, `aisuper_session_set_value`, `aisuper_session_module_command`, `aisuper_session_respond_modal`, `aisuper_session_destroy`).
- **Full Image & SVG Support (`LunaSVG v3.5.0` + `stb_image`)**: Decodes PNG/JPEG/BMP/GIF, raw `<svg>` markup, URL-encoded or Base64 `data:image/svg+xml` URIs, local `.svg` assets, and HTTP `.svg` resources, including `svg { color: ... }` injection for `currentColor` button icons.
- **Aspect-Ratio-Preserving Fit Layout**: `Image` widgets and button icons scale using `ContentScale.Fit` (`contain`) semantics matching AISuper's Compose and React frontends (no horizontal stretching on `fillMaxWidth` containers).
- **Non-Cancellable Modal Dialogs**: Renders `ConfirmationRequest` modals with `persistent = true` and `no_close_button = true`, plus destructive button styling.
- **Live Snapshot Inspector**: Built-in right-hand sheet displaying the live JSON state snapshot emitted by `aisuper_core`.

## Prerequisites

1. **Java JDK** (e.g., Android Studio JBR) and Gradle wrapper in `kotlin/` to build `:native-bridge`.
2. **CMake (>= 3.20)**, **Ninja**, and a **C++20 compiler** (Visual Studio MSVC x64 `vcvars64.bat` on Windows; GCC/Clang on Linux/macOS).
3. Network access on first CMake configure so `FetchContent` can pull `CUI v1.5.0`, `nlohmann/json v3.11.3`, `miniz 3.0.2`, and `lunasvg v3.5.0`.

## Build Instructions

### 1. Build the Kotlin/Native Shared Library (`aisuper_core`)

From `kotlin/`:

```powershell
# Windows x64 (produces kotlin/native-bridge/build/bin/mingwX64/releaseShared/aisuper_core.dll)
# Release builds automatically strip COFF symbols via Konan's strip --strip-unneeded (~9.3 MB).
.\gradlew.bat :native-bridge:linkReleaseSharedMingwX64

# Linux x64 / macOS ARM64 targets are also configured:
# .\gradlew.bat :native-bridge:linkReleaseSharedLinuxX64
# .\gradlew.bat :native-bridge:linkReleaseSharedMacosArm64
```

> **Windows ARM64 note**: Kotlin/Native does not currently ship a `mingwArm64` target. On Windows ARM64 devices, run the x64 binaries (`aisuper_cui.exe` + `aisuper_core.dll`) under Windows x64 emulation.

### 2. Configure and Build `cui-app` (Windows MSVC + Ninja)

```powershell
cmd /c "call `"C:\Program Files\Microsoft Visual Studio\18\Community\VC\Auxiliary\Build\vcvars64.bat`" >nul && cmake -S d:\Work\Mobile\Android\AISuper\cui-app -B d:\Work\Mobile\Android\AISuper\cui-app\build -G Ninja -DCMAKE_BUILD_TYPE=Release && cmake --build d:\Work\Mobile\Android\AISuper\cui-app\build --config Release"
```

CMake automatically copies `aisuper_core.dll` next to `aisuper_cui.exe` and `aisuper_cui_smoke.exe` in `cui-app/build/`.

## Running

```powershell
# Launch interactive window (use toolbar "Folder" / ".zip / .json" buttons or drag & drop)
.\cui-app\build\aisuper_cui.exe

# Or launch an applet directory, applet.json, or .zip archive directly from CLI
.\cui-app\build\aisuper_cui.exe "d:\Work\Mobile\Android\AISuper\.agents\sample-applets\widgets\applet.json"
```

## Automated End-to-End Smoke Test

`aisuper_cui_smoke.exe` loads `aisuper_core.dll`, launches the `widgets` sample applet, renders multiple offscreen frames through CUI's software renderer (`cui_sw_renderer`), exercises a non-cancellable modal confirmation round-trip, and verifies LunaSVG `dynamic_svg`, `currentColor` icon rendering, and non-stretched Fit quad dimensions:

```powershell
.\cui-app\build\aisuper_cui_smoke.exe "d:\Work\Mobile\Android\AISuper\.agents\sample-applets\widgets\applet.json"
```

## Current Limitations

- **Audio Playback**: `desktopNativeMain` currently binds a no-op `StubAudioPlayer` (`AudioPlayer` widgets render state UI without audio output).
- **Native Windows ARM64**: Awaits upstream Kotlin/Native `mingwArm64` target support.
