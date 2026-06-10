---
name: applet-interactive-generator
description: Skill for interactively interviewing the user to generate a new AISuper applet or feature from scratch. Guides the AI through a structured step-by-step development process.
---

# Applet Interactive Generator Skill

Use this skill when the user wants to create a new Applet or Feature from scratch but hasn't provided a detailed technical specification. Your goal is to guide the user interactively through the development process.

## Process Overview

You MUST execute the following steps in sequence. Do not jump ahead. Present one step at a time to the user, waiting for their response before proceeding to the next.

### Step 1: Requirements Gathering
- **Action**: Ask the user what issue they are trying to solve or what functionality they want the applet/feature to perform.
- **Objective**: Understand the domain, the expected inputs, the desired outputs, and the overall user flow. Ask clarifying questions about data sources (e.g., "Will you need an external API for weather?") and UI needs (e.g., "Do you want a settings screen?").
- **Output**: Present a brief summary of the requirements for the user to approve.

### Step 2: UI Prototyping
- **Action**: Draft the JSON UI layouts.
- **Reference**: Strictly adhere to the [layout-types.ts](../template/applet/types/layout-types.ts) type definitions.
- **Objective**: Create the `main_layout.json` (or equivalent). Bind UI elements to state variables (`id`, `progressId`, `dynamicChildrenId`, etc.) and define action strings for buttons (`"action": "submit_form"`).
- **Output**: Write the JSON layout file and use the MCP `applet_reload` to visually present the layout prototype to the user for feedback.

### Step 3: Top-Level Scripts & Stubs
- **Action**: Write the top-level JavaScript (`main_script.js`).
- **Reference**: Use [runtime-globals.d.ts](../template/typescript/types/runtime-globals.d.ts) for the available native functions.
- **Objective**: Implement the `initialize()` function and event handlers (e.g., `submit_form()`). Wire up the UI state bindings using `setValue` and `getValue`. 
- **Stubs**: If the feature requires heavy business logic (like fetching a radio stream, parsing weather data, or complex math), write a stub function that returns mock data. Do not implement the heavy logic directly in the top-level script.
- **Output**: Test the stubbed script interactively. The user should be able to click buttons and see the UI react with the mock data.

### Step 4: jsModule Development
- **Action**: Develop the actual TypeScript modules (`.ts`).
- **Reference**: Use the [template/typescript/modules/hello](../template/typescript/modules/hello) structure.
- **Objective**: Replace the stubs with real implementation. Create a new `jsModule` (e.g., `weather`, `radio_search`), register it via `registerExports`, and then hook it up in your top-level script by calling the module function.
- **Output**: Finalize the feature, reload the applet, and ask the user to verify the end-to-end functionality.
