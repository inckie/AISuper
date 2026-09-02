---
name: aisuper-applet-dev
description: Primary entry point for AI agents building Applets for AISuper. Explains how to access the WikiKnowledge KB.
---

# AISuper Applet Developer Skill

You are an AI tasked with developing or debugging Applets for the AISuper platform.

AISuper uses a self-documenting Knowledge Base hosted via the MCP server `wikiknowledge-aisuper` (which might be available on your `mcp-host-http` connection).

## 1. Accessing Documentation
Before writing any code or making assumptions about how AISuper works, you MUST read the `applet-developer-skill` article from the Knowledge Base. 

Call the `wikiknowledge-aisuper_get_article` tool with `article_id: "applet-developer-skill"`. That document contains all the rules, API references, and guidelines you need.

## 2. Remote / Agentic Execution
If you are running in an isolated environment and have been asked to work on an applet via MCP:
Do NOT attempt to use native file system tools (like `list_dir`, `view_file`, or `write_to_file`) on the local workspace if you don't have access to the applet folder directly. 

Instead, the AISuper MCP server exposes its own file tools (`file_list`, `file_read`, `file_write`, `file_delete`) that are safely scoped to the applet root folder. You can read about this and other tools in the `ai-harness-skill` article in the KB!
