---
categories:
- system-overview
created: '2026-06-20T05:03:47.078480+00:00'
id: ui-layout-system
modified: '2026-11-26T08:06:13.175564+00:00'
tags:
- ui
- layout
- rendering
- widgets
title: UI and Layout System
type: leaf
---

# UI and Layout System

The AISuper UI is driven by a data-driven layout system, ensuring that the same applet looks and behaves consistently across native and web platforms.

## Core Concepts

| Concept | Description |
| :--- | :--- |
| **Widget** | Base visual component provided by the runtime (Label, Button, TextField, Image, etc.). |
| **Layout** | A JSON-defined spatial arrangement of Widgets (Column, Row). |
| **Styling** | Class-based rules defined in a `StyleSheet` and resolved at render time. |

## Platform Renderers

The UI is rendered using a "thin" layer that maps the widget tree to platform-specific primitives:

*   **Native UI (`:composeApp`)**: Uses **Compose Multiplatform** to render widgets on Android, iOS, and Desktop.
*   **Native C++ Desktop UI (`cui-app/`)**: A zero-JVM C++20 renderer built on **CUI (`v1.5.0`)** + **LunaSVG (`v3.5.0`)** with aspect-ratio-preserving Fit (`contain`) image/SVG scaling, backed in-process by Kotlin/Native (`:native-bridge`). See [[cui-native-frontend|CUI + Kotlin/Native Desktop Frontend]].
*   **Web UI (`client-react/`)**: A recursive **React** renderer that maps the same widget tree to DOM elements.
*   **App Widgets**: **Jetpack Glance** implementation for Android home screen widgets.

## Modal Dialogs & Alerts

Modal dialogs (such as awaitable, non-cancellable confirmations) are managed by a separate modal context provider (`ModalContextProvider` in Compose, persistent `cui_dialog_begin` in `cui-app`) completely outside the widget rendering tree. See [[modal-dialog-system|Modal Dialog and Confirmation System]].

## Design Patterns
For more details on building interfaces, see the [[dynamic-interfaces-skill|Dynamic Interfaces Skill]].

---
**Implementation Details:** [[src:internals/layout-models]]