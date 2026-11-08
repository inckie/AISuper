---
categories: []
created: '2026-06-20T05:03:43.480844+00:00'
id: system-overview
modified: '2026-11-08T04:17:28.780338+00:00'
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
1.  **The Runtime Engine**: A flexible, "game-engine-like" environment that manages lifecycles, permissions, and native modules (GPS, Audio, HTTP). Available as a Kotlin Multiplatform app (Android/Desktop) and a pure JavaScript TinyJS desktop container.
2.  **The Applet**: A collection of signed artifacts (JSON layouts, JavaScript blocks) that define the user experience and business logic.
<!-- human:end -->

## Key Knowledge Areas & Runtimes

<!-- ai:start -->
### [[architecture|Architecture]] & [[architecture-overview|System Architecture]]
Overview of the core engineering foundations and execution environments powering AISuper:
- **[[architecture|Architecture]]**: Master category covering the high-level design, KMP Multiplatform logic core, dynamic layout engines, and the standalone TinyJS runtime.
- **[[architecture-overview|Architecture Overview]]**: Detailed technical breakdown of the Kotlin Multiplatform core, the headless server layer, and the React web frontend.

### Subsystems & User Interface
- **[[ui-layout-system|UI and Layout System]]**: Reference for the data-driven JSON widget hierarchy rendered natively (Compose) or in WebViews (React).
- **[[modal-dialog-system|Modal Dialog and Confirmation System]]**: Awaitable, non-cancellable confirmation modal architecture ensuring user verification for security-critical actions across all runtimes.
- **[[applet-lifecycle|Applet Lifecycle and Loading]]**: Technical details on how applets are packaged, distributed, verified, and dynamically loaded across platforms.

### AI Developer Capabilities & Tooling
- **[[skills|AI Developer Skills]]**: Index of specialized skills, developer workflows, and Model Context Protocol (MCP) tooling enabling AI agents to design, generate, debug, and test applets.
<!-- ai:end -->