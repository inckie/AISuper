---
categories:
- system-overview
created: '2026-06-20T04:51:16.720690+00:00'
id: architecture
modified: '2026-11-14T03:01:48.833861+00:00'
tags:
- architecture
- system-design
title: Architecture
type: category
---

# Architecture

<!-- human:start -->
This category covers the high-level design, module structure, and data flow of the AISuper application. It includes technical details on the Kotlin Multiplatform (KMP) logic core, the dynamic layout system, the headless execution layer, and the standalone Pure JS (TinyJS) runtime.
<!-- human:end -->

## Core Architectural Blueprints & Runtimes

<!-- ai:start -->
### [[architecture-overview|Architecture Overview]]
The master architectural blueprint for the Kotlin Multiplatform (KMP) stack. Documents the Gradle module structure (`:shared`, `:composeApp`, `:server`, etc.), data flow paths for native and web, platform backends, security model, and core components.

### [[state-management-and-persistence|State Management and Persistence]]
Documents the type-safe, scope-isolated dual-storage system (`APPLET`, `FEATURE`, `MODULE`, `MODULE_GLOBAL`) across transient in-memory and platform-specific persistent `KStorage` backends.

### [[tinyjs-runtime|TinyJS Pure JavaScript Runtime Architecture]]
A dedicated category and technical overview of the standalone, zero-JVM runtime built on `txiki.js v26.11.0` (QuickJS-ng + libuv) and WebView2. Documents isolate sandboxing, SharedArrayBuffer synchronous RPC, and child subsystem source modules.

### [[modal-dialog-system|Modal Dialog and Confirmation System]]
The decoupled, awaitable, and non-cancellable modal confirmation subsystem used across both KMP and TinyJS runtimes to ensure security-critical user confirmation before executing protected actions.
<!-- ai:end -->