package com.damn.aisuper.harness

import com.damn.aisuper.rpc.RpcMethod
import com.damn.aisuper.rpc.RpcParam
import com.damn.aisuper.rpc.RpcService
import com.damn.aisuper.rpc.SurfaceKind
import com.damn.aisuper.runtime.Applet
import com.damn.aisuper.storage.StorageScope
import com.damn.aisuper.util.LogBufferSink
import com.damn.aisuper.util.LogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

/**
 * Self-documenting JSON-RPC & MCP surface for the AISuper AI Harness.
 *
 * @wk-id mcp-tools
 * @wk-tags kotlin, mcp, rpc, tools, harness
 * @wk-categories ai-harness-skill
 *
 * Replaces the legacy hand-written `McpTool` subclasses with `packages/rpc-core`
 * `@RpcService` / `@RpcMethod` / `@RpcParam` annotations backed by `ReflectiveRpcSurface`.
 */
@RpcService(
    name = "harness",
    description = "AISuper AI Harness tools for applet lifecycle, state/storage, logs, files, layout, window control, and input simulation.",
    kind = SurfaceKind.OS
)
class HarnessSurface(
    private val applet: Applet,
    private val scope: CoroutineScope,
    private val logBuffer: LogBufferSink,
    private val server: McpServer
) {

    @RpcMethod(name = "applet_reload", description = "Reloads the current applet from disk")
    fun appletReload(): String {
        scope.launch { applet.loadApplet("applet.json") }
        return "Reload triggered"
    }

    @RpcMethod(name = "feature_launch", description = "Launches a specific feature by ID")
    fun featureLaunch(
        @RpcParam(name = "featureId", description = "ID of the feature to launch") featureId: String
    ): String {
        scope.launch { applet.launchFeature(featureId) }
        return "Launch triggered"
    }

    @RpcMethod(name = "action_send", description = "Sends an action to the current feature")
    fun actionSend(
        @RpcParam(name = "action", description = "Action name") action: String,
        @RpcParam(name = "args", description = "Action arguments", required = false, type = "array") args: JsonArray? = null
    ): String {
        scope.launch { applet.handleAction(action, args ?: JsonArray(emptyList())) }
        return "Action sent"
    }

    @RpcMethod(name = "values_get", description = "Gets all current feature values", readOnly = true)
    fun valuesGet(): JsonElement {
        val values = applet.currentFeature.value?.values?.value ?: emptyMap()
        return JsonObject(values)
    }

    @RpcMethod(name = "value_set", description = "Sets a specific feature value")
    fun valueSet(
        @RpcParam(name = "id", description = "Value ID") id: String,
        @RpcParam(name = "value", description = "Value to set (can be any JSON type)", required = false) value: JsonElement? = null
    ): String {
        applet.updateValue(id, value ?: JsonNull)
        return "Value updated"
    }

    @RpcMethod(name = "storage_get", description = "Gets a value from storage", readOnly = true)
    suspend fun storageGet(
        @RpcParam(name = "scope", description = "Storage scope (applet, feature, module, module.global)") scope: String,
        @RpcParam(name = "key", description = "Storage key") key: String,
        @RpcParam(name = "persistent", description = "Whether to use persistent storage", required = false) persistent: Boolean? = null
    ): JsonElement {
        val resolvedScope = resolveStorageScope(scope)
        val usePersistent = persistent ?: scope.contains("persistent", ignoreCase = true)
        val storage = if (usePersistent) applet.appletPersistentStorage else applet.appletTransientStorage
        return storage.getObject(resolvedScope, key) ?: JsonPrimitive("null")
    }

    @RpcMethod(name = "storage_set", description = "Sets a value in storage")
    suspend fun storageSet(
        @RpcParam(name = "scope", description = "Storage scope (applet, feature, module, module.global)") scope: String,
        @RpcParam(name = "key", description = "Storage key") key: String,
        @RpcParam(name = "value", description = "Value to set", required = false) value: JsonElement? = null,
        @RpcParam(name = "persistent", description = "Whether to use persistent storage", required = false) persistent: Boolean? = null
    ): String {
        val resolvedScope = resolveStorageScope(scope)
        val usePersistent = persistent ?: scope.contains("persistent", ignoreCase = true)
        val storage = if (usePersistent) applet.appletPersistentStorage else applet.appletTransientStorage
        storage.putObject(resolvedScope, key, value ?: JsonNull)
        return "Value set"
    }

    @RpcMethod(name = "logs_get", description = "Gets recent logs", readOnly = true)
    fun logsGet(
        @RpcParam(name = "limit", description = "Max number of logs to return", required = false) limit: Int? = null,
        @RpcParam(name = "offset", description = "Number of logs to skip", required = false) offset: Int? = null,
        @RpcParam(name = "tagFilter", description = "Filter logs by tag", required = false) tagFilter: String? = null
    ): JsonElement = formatLogEntries(
        filterLogs(tagFilter).drop(offset ?: 0).take(limit ?: 100)
    )

    @RpcMethod(name = "logs_tail", description = "Gets the last N logs", readOnly = true)
    fun logsTail(
        @RpcParam(name = "count", description = "Number of recent logs to return", required = false) count: Int? = null,
        @RpcParam(name = "tagFilter", description = "Filter logs by tag", required = false) tagFilter: String? = null
    ): JsonElement = formatLogEntries(
        filterLogs(tagFilter).takeLast(count ?: 50)
    )

    @RpcMethod(name = "logs_since", description = "Gets logs since a specific timestamp", readOnly = true)
    fun logsSince(
        @RpcParam(name = "timestamp", description = "Start timestamp (ms)") timestamp: Long,
        @RpcParam(name = "limit", description = "Max number of logs to return", required = false) limit: Int? = null,
        @RpcParam(name = "tagFilter", description = "Filter logs by tag", required = false) tagFilter: String? = null
    ): JsonElement = formatLogEntries(
        logBuffer.entries
            .filter { it.timestamp > timestamp && (tagFilter == null || it.tag.contains(tagFilter, ignoreCase = true)) }
            .take(limit ?: 100)
    )

    @RpcMethod(name = "file_list", description = "Lists files in the applet directory", readOnly = true)
    fun fileList(
        @RpcParam(name = "path", description = "Directory path", required = false) path: String? = null
    ): JsonElement {
        val pathString = path ?: ""
        val dir = server.resolveSafePath(pathString)
        if (!dir.exists()) throw IllegalArgumentException("Directory not found: $pathString")
        return buildJsonObject {
            put("files", buildJsonArray {
                dir.listFiles()?.forEach { file ->
                    add(buildJsonObject {
                        put("name", file.name)
                        put("isDirectory", file.isDirectory)
                        put("size", file.length())
                    })
                }
            })
        }
    }

    @RpcMethod(name = "file_read", description = "Reads a file from the applet directory", readOnly = true)
    fun fileRead(
        @RpcParam(name = "path", description = "File path") path: String
    ): String {
        val file = server.resolveSafePath(path)
        if (!file.exists()) throw IllegalArgumentException("File not found")
        return file.readText()
    }

    @RpcMethod(name = "file_write", description = "Writes/Overwrites a file in the applet directory")
    fun fileWrite(
        @RpcParam(name = "path", description = "File path") path: String,
        @RpcParam(name = "content", description = "File content") content: String
    ): String {
        val file = server.resolveSafePath(path)
        file.parentFile?.mkdirs()
        file.writeText(content)
        return "File written"
    }

    @RpcMethod(name = "file_delete", description = "Deletes a file in the applet directory")
    fun fileDelete(
        @RpcParam(name = "path", description = "File path") path: String
    ): String {
        val file = server.resolveSafePath(path)
        return if (file.exists()) {
            file.delete()
            "File deleted"
        } else {
            "File not found"
        }
    }

    @RpcMethod(name = "layout_get", description = "Gets the current feature layout tree", readOnly = true)
    fun layoutGet(): JsonElement {
        val layout = applet.currentFeature.value?.layoutRoot?.value
        return if (layout != null) Json.encodeToJsonElement(layout) else JsonNull
    }

    @RpcMethod(name = "ui_state_get", description = "Gets the current feature layout tree", readOnly = true)
    fun uiStateGet(): JsonElement = layoutGet()

    @RpcMethod(name = "screenshot_take", description = "Takes a screenshot of the window", readOnly = true)
    suspend fun screenshotTake(): JsonElement {
        server.bringToForeground()
        delay(100)
        val base64Image = server.takeScreenshot()
        return buildJsonObject {
            put("type", "image")
            put("data", base64Image)
            put("mimeType", "image/png")
        }
    }

    @RpcMethod(name = "window_focus", description = "Brings the application window to foreground and requests focus")
    fun windowFocus(): JsonElement {
        val success = server.bringToForeground()
        return buildJsonObject {
            put("success", success)
            put("message", if (success) "Window brought to foreground" else "Window focus handler not available")
        }
    }

    @RpcMethod(
        name = "window_bring_to_foreground",
        description = "Brings the application window to foreground and restores it if minimized"
    )
    fun windowBringToForeground(): JsonElement = windowFocus()

    @RpcMethod(name = "window_resize", description = "Resizes the desktop application window to the given width and height")
    fun windowResize(
        @RpcParam(name = "width", description = "Window width in pixels") width: Int,
        @RpcParam(name = "height", description = "Window height in pixels") height: Int
    ): JsonElement {
        val success = server.resizeWindow(width, height)
        return buildJsonObject {
            put("success", success)
            put("width", width)
            put("height", height)
        }
    }

    @RpcMethod(name = "input_tap", description = "Performs a tap/click at window-relative coordinates (x, y) like ADB tap")
    fun inputTap(
        @RpcParam(name = "x", description = "X coordinate relative to window content area") x: Int,
        @RpcParam(name = "y", description = "Y coordinate relative to window content area") y: Int
    ): JsonElement {
        val success = server.tap(x, y)
        return buildJsonObject {
            put("success", success)
            put("x", x)
            put("y", y)
        }
    }

    @RpcMethod(
        name = "input_scroll",
        description = "Performs a mouse wheel scroll. Positive amount scrolls down, negative scrolls up."
    )
    fun inputScroll(
        @RpcParam(name = "amount", description = "Number of scroll notches (e.g. 3 to scroll down, -3 to scroll up)") amount: Int,
        @RpcParam(name = "x", description = "Optional X coordinate relative to window content area", required = false) x: Int? = null,
        @RpcParam(name = "y", description = "Optional Y coordinate relative to window content area", required = false) y: Int? = null
    ): JsonElement {
        val success = server.scroll(x, y, amount)
        return buildJsonObject {
            put("success", success)
            put("amount", amount)
        }
    }

    @RpcMethod(
        name = "input_drag",
        description = "Simulates a drag/swipe gesture from (startX, startY) to (endX, endY) like ADB swipe"
    )
    fun inputDrag(
        @RpcParam(name = "startX", description = "Starting X coordinate") startX: Int,
        @RpcParam(name = "startY", description = "Starting Y coordinate") startY: Int,
        @RpcParam(name = "endX", description = "Ending X coordinate") endX: Int,
        @RpcParam(name = "endY", description = "Ending Y coordinate") endY: Int,
        @RpcParam(name = "durationMs", description = "Duration of the drag in milliseconds (default 300)", required = false) durationMs: Long? = null
    ): JsonElement {
        val success = server.drag(startX, startY, endX, endY, durationMs ?: 300L)
        return buildJsonObject {
            put("success", success)
            put("startX", startX)
            put("startY", startY)
            put("endX", endX)
            put("endY", endY)
        }
    }

    @RpcMethod(name = "adb_shell_input", description = "Executes an adb shell input command")
    suspend fun adbShellInput(
        @RpcParam(name = "args", description = "Command arguments (e.g., 'tap 100 200')") args: String
    ): JsonElement {
        val cmd = listOf("adb", "shell", "input") + args.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val process = withContext(Dispatchers.IO) {
            ProcessBuilder(cmd).start()
        }
        val output = withContext(Dispatchers.IO) { process.inputStream.bufferedReader().readText() }
        val error = withContext(Dispatchers.IO) { process.errorStream.bufferedReader().readText() }
        return buildJsonObject {
            put("stdout", output)
            put("stderr", error)
        }
    }

    @RpcMethod(name = "modal_get", description = "Gets the currently active modal dialog, if any", readOnly = true)
    fun modalGet(): JsonElement {
        val active = applet.modalHost.activeModal.value
        return if (active is com.damn.aisuper.modal.ConfirmationRequest) {
            buildJsonObject {
                put("active", true)
                put("type", "confirmation")
                put("id", active.id)
                put("title", active.title)
                put("message", active.message)
                put("confirmText", active.confirmText)
                put("cancelText", active.cancelText)
                put("isDestructive", active.isDestructive)
            }
        } else {
            buildJsonObject { put("active", false) }
        }
    }

    @RpcMethod(name = "modal_respond", description = "Responds to the currently active modal dialog")
    fun modalRespond(
        @RpcParam(name = "confirm", description = "Whether to confirm (true) or cancel (false)") confirm: Boolean = true
    ): JsonElement {
        val handled = applet.modalHost.respondToActive(confirm)
        return buildJsonObject {
            put("success", handled)
            put("message", if (handled) "Responded to modal: $confirm" else "No active modal found")
        }
    }

    private fun resolveStorageScope(scopeStr: String): StorageScope {
        return StorageScope.entries.find { it.name.equals(scopeStr, ignoreCase = true) }
            ?: StorageScope.entries.find { it.key.equals(scopeStr, ignoreCase = true) }
            ?: StorageScope.Applet
    }

    private fun filterLogs(tagFilter: String?): List<LogEntry> {
        val all = logBuffer.entries
        return if (tagFilter != null) all.filter { it.tag.contains(tagFilter, ignoreCase = true) } else all
    }

    companion object {
        fun formatLogEntries(entries: List<LogEntry>): JsonElement = buildJsonObject {
            put("content", buildJsonArray {
                entries.forEach { log ->
                    add(buildJsonObject {
                        put("level", log.level.name)
                        put("tag", log.tag)
                        put("message", log.message)
                        put("timestamp", log.timestamp)
                        log.throwable?.let { put("throwable", it.toString()) }
                    })
                }
            })
        }
    }
}
