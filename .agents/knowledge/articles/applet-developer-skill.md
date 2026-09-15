---
categories:
- applet-developer
created: '2026-06-20T04:50:28.009872+00:00'
id: applet-developer-skill
modified: '2026-09-15T12:08:37.893862+00:00'
tags:
- skills
- development
- applets
- guidelines
title: Applet Developer Skill
type: leaf
---

# Applet Developer Skill (AISuper)

Welcome to the AISuper Applet Developer guidelines! This document serves as the primary entry point for AIs writing applets, features, and modules without needing to touch or understand the underlying Kotlin native code.

> [!IMPORTANT]
> **Mandatory Rule: Follow Ponytail**
> All code, layouts, and scripts written for AISuper must adhere to [[ponytail-skill|Ponytail AI Development Skill]]. Always stop at the first rung of the ladder: question if code needs to exist (YAGNI), reuse existing engine widgets, rely on vanilla JS without npm dependencies, and ship the shortest working diff with a runnable check.

## 1. Overall Idea and Purpose

The **AISuper App** is a flexible runtime engine (similar to a game engine) that dynamically loads and executes specialized, sandboxed "applets". 

Instead of building large monolithic applications for every need (like a standalone Audio Player, Weather App, or Spending Analyzer), developers (or AI) can rapidly prototype and deploy these as lightweight applets. The engine provides the UI widgets, layout rendering, navigation, and core native modules (like HTTP, GPS, Audio). The applet simply provides a set of JSON layouts and JS/TS scripts to tie these building blocks together securely.

## 2. Bootstrapping New Applets

When kick-starting a new applet, you will encounter two primary development scenarios.

### Case 1: Full File System Access
If you are developing locally with full workspace access:
1. Copy the template from `template/applet` or examine existing samples in `.agents/sample-applets`.
2. Launch the AISuper JVM target via the [[src:internals/main-entry]] and pass your new applet directory as a command line argument, along with the `--mcp-server` flag:
   ```powershell
   .\com.damn.aisuper.exe "C:\path\to\new-applet" --mcp-server 8081
   ```
3. The platform will boot, mount your folder as the applet root, and spin up the [[src:internals/mcp-server]].

### Case 2: MCP-Only Interface (Remote/Agentic AI)
If you are an AI agent running in a remote or restricted environment where the user has *already* copied the template and started the MCP server for you:
1. **Do not** attempt to use native OS file tools to edit files directly.
2. The AISuper MCP server exposes safe, isolated file manipulation tools (`file_list`, `file_read`, `file_write`, `file_delete`) scoped strictly to the applet root folder (see [[src:internals/mcp-tools]]).
3. You can use these tools to build the applet and issue the `applet_reload` command to see your changes instantly.

## 3. Key References & Type Definitions

As an applet developer, you should rely entirely on these defined interfaces rather than inventing properties:

- **Coding Discipline**: See [[ponytail-skill|Ponytail AI Development Skill]] for the mandatory minimalist ladder (YAGNI, minimal diffs, native engine widgets first).
- **UI & Layouts**: See [[dynamic-interfaces-skill|Dynamic Interfaces Skill]] for how to construct valid JSON widget trees.
- **Widget Types**: Use `template/applet/types/layout-types.ts` as the absolute source of truth for allowed widget properties. **Do not invent non-existing properties.**
- **Runtime Native APIs**: See `template/typescript/types/runtime-globals.d.ts` for globally injected functions available in your applet's JS environment (e.g., `setValue`, `getValue`, `httpGet`, `persistentStorageGet`).
- **JS Module Creation**: See [[js-modules-creation-skill|JS Modules Creation Skill]] for critical guidelines on engine quirks (e.g., avoiding `switch` statements) and writing safe TS/JS modules.
- **Interactive Execution**: See [[ai-harness-skill|AI Harness Skill]] for using the MCP to interactively test, debug, and reload applets.

## 4. Working with TypeScript Modules (`jsModule`)

While simple scripts can be written in vanilla JavaScript (e.g. `files/main_script.js`), more complex business logic should be broken out into typed TypeScript modules. 

### The Template Directory
When creating a new TS module, use `template/typescript/modules/hello` as your reference structure.
It typically contains:
- `module.config.json` - defines the module configuration.
- `index.ts` - Contains your typed logic. You MUST export the module functions to the engine using `registerExports("moduleName", ["functionName1"])`.

### Polyfills for Testing
The `template/typescript` project includes polyfills that mock the native runtime APIs. This allows you to write standard Jest/Mocha unit tests for complex logic without needing to boot up the entire AISuper native engine. In accordance with [[ponytail-skill]], keep tests minimal: one runnable check for non-trivial logic.

## 5. Reference Applets

Whenever you are unsure of how a feature is wired up, refer to the fully working Widgets Demo Applet.

The Widgets applet contains exhaustive examples of data binding (`progressId`, `dynamicChildrenId`), onChange actions, nested layouts, and multi-layout switching.