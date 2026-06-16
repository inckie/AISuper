---
name: applet-debugging-recipes
description: Recipes and hints for debugging applets using the MCP bridge. Contains specific workarounds and tips based on the Kotlin MCP Server implementation.
---

# Applet Debugging Recipes (MCP Bridge)

This skill provides practical recipes and hints for debugging AISuper applets through the MCP bridge. It acts as a companion to the `ai-harness-skill.md`.

## 1. Action Sending (`action_send`)
The `action_send` tool is used to simulate user interactions by calling JS functions directly. 

**Recipe for calling `action_send`:**
When using `call_mcp_tool`, ensure your `Arguments` object contains a proper JSON array for `args`, and the `action` matches the exact string name of your JS function:
```json
{
  "action": "viewSession",
  "args": [0]
}
```

## 2. Layout Inspection (`layout_get` and `ui_state_get`)
If you're unsure why an applet is not rendering correctly (e.g., you see a blank screen after an action), use the `layout_get` or `ui_state_get` tools.
**Hint:** These tools pull the current JSON layout tree directly from the `currentFeature.value?.layoutRoot?.value`. This is exactly what the engine is attempting to render. If this is `null` or missing children, your script logic hasn't populated the layouts correctly via `setValue` or `setLayout`.

## 3. Storage Scopes and Types
When using `storage_get` and `storage_set`, be mindful of the `scope` parameter and the `persistent` flag.
**Hint:** The Kotlin implementation matches the `scope` string (case-insensitively) against `StorageScope` entries (`applet`, `feature`, `module`, `module.global`).
Use the `persistent: true` parameter for `appletPersistentStorage`. Otherwise, it defaults to `appletTransientStorage` (in-memory).

**Recipe for inspecting saved data:**
To verify that your applet is successfully saving data, call `storage_get` with:
```json
{
  "scope": "applet",
  "key": "your_storage_key",
  "persistent": true
}
```

## 4. Log Filtering (`logs_get`, `logs_tail`, `logs_since`)
The AISuper JVM captures all logs (including JS `console.log` and `console.error`) into a `LogBufferSink`.
**Hint:** You can filter these logs natively using the `tagFilter` parameter.
- Natively generated logs usually have tags like `Harness`, `MCP`, `Applet`, or `QuickJS`.
- If you're looking specifically for script errors, `logs_tail` with `count: 50` is the fastest way to get recent errors without being overwhelmed by network or layout logs.

## 5. UI Verification (`screenshot_take`)
**Hint:** `screenshot_take` relies on `java.awt.Robot` to capture the application window bounds. If you receive an image but it shows "Loading Applet...", you may have captured the screenshot too quickly after an `applet_reload` or layout change.
**Recipe:** Always wait a few seconds (or check `logs_tail` to see if the layout rendering has finished) before calling `screenshot_take`.

## 6. Shell Input (`adb_shell_input`)
This is a proxy to `adb shell input`. You can use it to inject keystrokes or touch events if the applet is running on an Android device/emulator.
**Recipe:** 
- Tap at coordinates: `{"args": "tap x y"}`
- Input text: `{"args": "text 'hello'"}`
