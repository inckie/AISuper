---
categories:
- applet-developer
created: '2026-06-20T04:50:22.677893+00:00'
id: ai-harness-skill
modified: '2026-06-20T04:57:29.030068+00:00'
tags:
- skills
- mcp
- debugging
- harness
title: AI Harness Skill
type: leaf
---

# AI Harness Skill (AISuper)

This skill enables a "strong" AI model to develop, test, and debug AISuper applets in real-time by controlling a running JVM instance via MCP.

## 1. Connection
The AISuper JVM app starts an MCP server on port 8081 (default) when launched with the `--mcp-server` flag. It listens on all network interfaces (`0.0.0.0`).

## 2. MCP Tools Reference

| Tool | Parameters | Description |
|---|---|---|
| `applet_reload` | None | Reloads the applet from disk. Call this after editing files. |
| `feature_launch` | `featureId` | Navigates to a specific feature. |
| `action_send` | `action`, `args` (array) | Triggers a JS function in the current feature. |
| `values_get` | None | Returns all current feature state variables. |
| `storage_get` | `scope`, `key` | Reads from `Applet`, `Feature`, or `Module` storage. |
| `storage_set` | `scope`, `key`, `value` | Writes to storage. |
| `logs_get` | `limit`, `offset`, `tagFilter` | Fetches the in-memory log buffer from start. |
| `logs_tail` | `count`, `tagFilter` | Returns the last `count` log entries. Useful for quick status checks. |
| `logs_since` | `timestamp`, `limit`, `tagFilter` | Returns logs after `timestamp` (ms) up to `limit`. Ideal for continuous log streaming. |
| `file_list` | `path` | Lists files in the applet directory. |
| `file_read` | `path` | Reads a file from the applet directory. |
| `file_write` | `path`, `content` | Writes/Overwrites a file in the applet directory. |
| `file_delete` | `path` | Deletes a file in the applet directory. |
| `layout_get` | None | Returns the current feature layout tree (as JSON). |
| `ui_state_get` | None | Returns the current UI state (alias for `layout_get`). |
| `screenshot_take` | None | Takes a PNG screenshot of the JVM window. Returns as base64. |
| `adb_shell_input` | `args` | Proxy for `adb shell input` (e.g., `tap x y`). |

## 3. Workflow for building a feature

1. **Setup**: Identify the applet directory passed to the app.
2. **Develop**: Write/Edit `.json` (layouts) and `.js` (scripts) in the applet folder.
3. **Apply**: Call `applet_reload` via MCP.
4. **Inspect**: Use `logs_tail` or `logs_since` to check for syntax or runtime errors.
   - `logs_tail(count=20)` is great for immediate feedback after reload.
   - `logs_since(lastTimestamp)` allows you to see only new logs since your last check.
5. **Debug**: Use `values_get` to see the current state of UI-bound variables.
6. **Interact**: Use `action_send` to simulate button clicks or trigger logic if the UI is not yet ready.

## 4. Applet Structure Template

```text
my-applet/
├── applet.json             # Manifest (features, entry point)
└── files/
    ├── main_layout.json    # UI definition
    └── main_script.js      # Business logic
```

## 5. Storage Scopes
- `applet`: Shared across all features.
- `feature`: Local to the current feature.
- `module`: Private to a native module instance.
- `module.global`: Shared across all instances of the same native module.

**Persistence**:
To use durable storage, use the functions prefixed with `persistentStorage` (e.g., `persistentStorageGetObject("feature", "key")`). Do NOT add "Persistent" to the scope name (e.g., `AppletPersistent` is invalid).

## 7. Template and TypeScript Modules

The `template/` directory provides a starting point for applets and TS modules.

### Applet Template (`template/applet/`)
- `applet.json`: Root manifest.
- `files/`: Contains layouts and scripts.

### TypeScript Template (`template/typescript/`)
Used to build JS modules that are loaded via `jsModule` type in `applet.json`.
1. **Develop**: Edit `modules/<module_id>/index.ts`.
2. **Configure**: Ensure `modules/<module_id>/module.config.json` has correct `output` path (should point to `../applet/files/<name>.js`).
3. **Build**:
   ```powershell
   cd template/typescript
   npm install
   npm run build
   ```
4. **Reload**: Call `applet_reload` via MCP to see changes.

## 8. Layout Inspection
Use `layout_get` to see the actual widget tree. This is critical when:
- `Row` and `Column` widgets support `dynamicChildrenId`.
- You need to verify if styles or modifiers were applied correctly.
- You are debugging layout nesting issues.