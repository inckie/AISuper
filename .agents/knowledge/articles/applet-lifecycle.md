---
categories:
- system-overview
created: '2026-06-20T05:03:50.596219+00:00'
id: applet-lifecycle
modified: '2026-06-20T05:03:50.596247+00:00'
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
Applets must be delivered as a **ZIP archive** or a standard directory (JVM only).

```text
my-custom-applet/
├── applet.json         <-- Root Manifest (Entry point)
└── files/
    ├── main_script.js  <-- Logic
    └── main_layout.json <-- UI
```

## Loading Mechanisms

### JVM Desktop
Path to the ZIP or directory is passed as a command-line argument to the executable or via Gradle:
```powershell
.\com.damn.aisuper.exe "C:\path\to\my-applet.zip"
```

### Android
*   **File Open**: Tapping a `.zip` file in a file manager and selecting "AISuper".
*   **App Shortcut**: Long-pressing the app icon and selecting "Run custom applet...".

## Runtime Execution
Once loaded, the `Applet` core manages the lifecycle, switching between **Features** and maintaining **State Storage** (Transient or Persistent) across sessions.