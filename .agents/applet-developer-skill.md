---
name: applet-developer
description: Core skill for AI agents acting as Applet Developers. Explains the AISuper architecture from an applet perspective, providing essential context, reference links, and guidelines for building applet features, layouts, and modules.
---

# Applet Developer Skill (AISuper)

Welcome to the AISuper Applet Developer guidelines! This document serves as the primary entry point for AIs writing applets, features, and modules without needing to touch or understand the underlying Kotlin native code.

## 1. Overall Idea and Purpose

The **AISuper App** is a flexible runtime engine (similar to a game engine) that dynamically loads and executes specialized, sandboxed "applets". 

Instead of building large monolithic applications for every need (like a standalone Audio Player, Weather App, or Spending Analyzer), developers (or AI) can rapidly prototype and deploy these as lightweight applets. The engine provides the UI widgets, layout rendering, navigation, and core native modules (like HTTP, GPS, Audio). The applet simply provides a set of JSON layouts and JS/TS scripts to tie these building blocks together securely.

## 2. Key References & Type Definitions

As an applet developer, you should rely entirely on these defined interfaces rather than inventing properties:

- **UI & Layouts**: See [Dynamic Interfaces Skill](dynamic-interfaces-skill.md) for how to construct valid JSON widget trees.
- **Widget Types**: Use [layout-types.ts](../template/applet/types/layout-types.ts) as the absolute source of truth for allowed widget properties. **Do not invent non-existing properties.**
- **Runtime Native APIs**: See [runtime-globals.d.ts](../template/typescript/types/runtime-globals.d.ts) for globally injected functions available in your applet's JS environment (e.g., `setValue`, `getValue`, `httpGet`, `persistentStorageGet`).
- **JS Module Creation**: See [JS Modules Creation Skill](js-modules-creation-skill.md) for critical guidelines on engine quirks (e.g., avoiding `switch` statements) and writing safe TS/JS modules.
- **Interactive Execution**: See [AI Harness Skill](ai-harness-skill.md) for using the MCP to interactively test, debug, and reload applets. Use `logs_tail` for quick status updates after reloads and `logs_since` to monitor logs in real-time.

## 3. Working with TypeScript Modules (`jsModule`)

While simple scripts can be written in vanilla JavaScript (e.g. `files/main_script.js`), more complex business logic should be broken out into typed TypeScript modules. 

### The Template Directory
When creating a new TS module, use [template/typescript/modules/hello](../template/typescript/modules/hello) as your reference structure.
It typically contains:
- `module.config.json` - defines the module configuration.
- `index.ts` - Contains your typed logic. You MUST export the module functions to the engine using `registerExports("moduleName", ["functionName1"])`.

### Polyfills for Testing
The `template/typescript` project includes polyfills that mock the native runtime APIs (like `persistentStorageGet`). This allows you to write standard Jest/Mocha unit tests for complex TypeScript logic without needing to boot up the entire AISuper native engine.

## 4. Reference Applets

Whenever you are unsure of how a feature is wired up, refer to the fully working Widgets Demo Applet:
**[Widgets Demo Applet Location](sample-applets/widgets)**

The Widgets applet contains exhaustive examples of data binding (`progressId`, `dynamicChildrenId`), onChange actions, nested layouts, and multi-layout switching.

## 5. Development Approach

**Note on File System Access**: The AI Harness MCP server exposes its own file creation/deletion/read/write endpoints. Full workspace file system access is completely optional if you are connected to the MCP server.

When writing an applet, follow this separation of concerns:
1. **`applet.json`**: Define the entry points, feature lists, styles, and required modules.
2. **Layouts (`.json`)**: Design visually using only the allowed primitives.
3. **Scripts (`.js`)**: Handle UI state, navigation, and action glue code.
4. **Modules (`.ts`)**: Encapsulate heavy business logic, network requests, or domain-specific computations.
