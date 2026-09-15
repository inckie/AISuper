---
categories:
- system-overview
created: '2026-06-20T04:51:20.602117+00:00'
id: skills
modified: '2026-09-15T12:08:10.233466+00:00'
tags:
- skills
- ai-agent
- development-workflow
title: AI Developer Skills
type: category
---

# AI Developer Skills

<!-- human:start -->
This category contains specialized guidelines, references, and recipes designed for AI agents developing and debugging AISuper systems. These articles provide the necessary context to develop features, layouts, and modules across both the native Kotlin codebase and sandboxed applets.
<!-- human:end -->

> [!IMPORTANT]
> **Mandatory Implementation Rule**: All AI agents modifying AISuper code (core native Kotlin engine or Keight TS/JSON applets) MUST adhere to [[ponytail-skill|Ponytail AI Development Skill]]. Default intensity is **full**: ladder enforced, stdlib/native widgets first, shortest working diff, YAGNI.

## Articles in This Category

<!-- ai:start -->
### [[ponytail-skill|Ponytail AI Development Skill]]
**MANDATORY CODING DISCIPLINE.** Enforces the ladder of simplicity (YAGNI, stdlib/native first, shortest diff) for both core Kotlin runtime and TS/JSON applets.

### [[applet-developer|Applet Developer Workflow]]
Specialized path for external AI agents focusing on interactive requirement gathering, UI prototyping, and sandboxed implementation.

### [[applet-developer-skill|Applet Developer Skill]]
The primary entry point for AI applet developers. Defines the sandbox purpose, key references (UI, Native APIs), and the overall development approach.

### [[dynamic-interfaces-skill|Dynamic Interfaces Skill]]
A comprehensive guide to building UIs. Details widget properties, reactive data binding patterns (Static vs Dynamic), and responsive design strategies.

### [[js-modules-creation-skill|JS Modules Creation Skill]]
Specific technical rules for creating TypeScript/JavaScript modules. Covers the Keight runtime's compatibility rules and the JS-to-Kotlin bridge contract.

### [[ai-harness-skill|AI Harness Skill]]
Technical reference for the MCP-based development harness. Lists available tools for reloading applets, inspecting state, and viewing logs in real-time.

### [[applet-debugging-recipes-skill|Applet Debugging Recipes]]
Practical "how-to" hints for troubleshooting applets via MCP, including layout inspection and storage verification.

### [[native-modules-skill|Native Module Development Skill]]
Guide for extending the engine with new native capabilities (Kotlin). Covers the factory pattern and JS bridge registration.

### [[applet-interactive-generator-skill|Applet Interactive Generator Skill]]
A structured 4-step workflow for interactively interviewing users to build new applets from scratch.
<!-- ai:end -->