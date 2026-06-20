---
categories:
- skills
created: '2026-06-20T04:56:56.680754+00:00'
id: applet-developer
modified: '2026-06-20T04:56:56.680787+00:00'
tags:
- skills
- applet-developer
- ai-agent-workflow
- external-ai
title: Applet Developer
type: category
---

# Applet Developer

<!-- human:start -->
This category is specifically curated for **External AI Agents** who act as Applet Developers. These agents do not need to know about the internals of the AISuper system and typically do not have access to its source code. Their role is to design and build dynamic features and applets using the provided sandboxed runtime.

The development lifecycle for an Applet Developer usually follows this trajectory:
1.  **Interactive Discovery**: Initiating a dialog with the user to gather requirements, as detailed in the [[applet-interactive-generator-skill|Applet Interactive Generator Skill]].
2.  **UI Prototyping**: Building and iterating on the interface using the [[dynamic-interfaces-skill|Dynamic Interfaces Skill]].
3.  **Harness Integration**: Utilizing the AISuper MCP server for real-time reloading and state inspection, explained in the [[ai-harness-skill|AI Harness Skill]].
4.  **Feature Implementation**: Creating custom logic and `jsModules` through the [[js-modules-creation-skill|JS Modules Creation Skill]].
5.  **Debugging**: Refining the experience using the recipes in the [[applet-debugging-recipes-skill|Applet Debugging Recipes Skill]].

By following these skills, an agent can build complex, responsive, and reliable applets entirely within the AISuper sandbox.
<!-- human:end -->

## Articles in This Category

<!-- ai:start -->
### [[applet-developer-skill|Applet Developer Skill]]
The core entry point defining the purpose and mindset of an applet developer.

### [[applet-interactive-generator-skill|Applet Interactive Generator Skill]]
A step-by-step workflow for gathering requirements and building a project through user interaction.

### [[dynamic-interfaces-skill|Dynamic Interfaces Skill]]
Technical reference for the JSON-based widget system and reactive data binding patterns.

### [[ai-harness-skill|AI Harness Skill]]
Documentation for the real-time development environment and MCP-based control bridge.

### [[js-modules-creation-skill|JS Modules Creation Skill]]
Guidelines for writing TypeScript/JavaScript modules that are compatible with the Keight runtime.

### [[applet-debugging-recipes-skill|Applet Debugging Recipes]]
Practical hints for inspecting logs, state, and layouts to solve common development issues.
<!-- ai:end -->