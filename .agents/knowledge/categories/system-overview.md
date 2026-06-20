---
categories: []
created: '2026-06-20T05:03:43.480844+00:00'
id: system-overview
modified: '2026-06-20T05:03:43.480867+00:00'
tags:
- overview
- philosophy
- system-design
title: General Overview
type: category
---

# AI Super App - General Overview

<!-- human:start -->
The **AI Super App** is the next evolution of the "Super Application" concept (Grab, WeChat, Uber). While traditional super apps bundle pre-built services, AISuper treats applications as dynamic, sandboxed **"Applets"** that can be generated, loaded, and executed on-the-fly based on user needs.

### Core Philosophy
Instead of relying on a single monolithic LLM chat interface for everything, AISuper advocates for:
*   **Specialized "Closed-Loop" UIs**: Purpose-built dashboards (e.g., "Podcast Player", "Spending Analyzer") that provide reproducible and safe behavior.
*   **Decoupled Development**: Applets are built by "Strong" external AI agents that deliver artifacts (Layouts, Scripts, Manifests) to a safe runtime sandbox.
*   **Efficiency**: Basic functionality is handled by lightweight, deterministic logic rather than expensive, stochastic LLM calls.

### The Ecosystem
The system is composed of two primary actors:
1.  **The Runtime Engine**: A flexible, "game-engine-like" environment that manages lifecycles, permissions, and native modules (GPS, Audio, HTTP).
2.  **The Applet**: A collection of signed artifacts (JSON layouts, JavaScript blocks) that define the user experience and business logic.
<!-- human:end -->

## Key Knowledge Areas

<!-- ai:start -->
### [[architecture-overview|System Architecture]]
Detailed breakdown of the Kotlin Multiplatform core, the headless server layer, and the React web frontend.

### [[applet-developer|Applet Development]]
Comprehensive guides for AI agents focusing on building new features and UI prototypes within the sandbox.

### [[ui-layout-system|UI and Layout System]]
Reference for the data-driven widget model used to render interfaces natively (Compose) or on the web (React).

### [[applet-lifecycle|Applet Lifecycle and Loading]]
Technical details on how applets are packaged, distributed, and loaded dynamically on Android or JVM Desktop.
<!-- ai:end -->