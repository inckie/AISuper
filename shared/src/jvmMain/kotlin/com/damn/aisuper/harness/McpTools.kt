package com.damn.aisuper.harness

import com.damn.aisuper.runtime.Applet
import com.damn.aisuper.storage.StorageScope
import com.damn.aisuper.util.LogBufferSink
import com.damn.aisuper.util.LogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File

data class McpParameter(
    val name: String,
    val description: String,
    val type: String = "string",
    val required: Boolean = true
)

interface McpTool {
    val name: String
    val description: String
    val parameters: List<McpParameter> get() = emptyList()

    suspend fun execute(args: JsonObject): JsonElement

    companion object {
        fun formatLogEntries(entries: List<LogEntry>): JsonElement {
            return buildJsonObject {
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
}

class AppletReloadTool(private val applet: Applet, private val scope: CoroutineScope) : McpTool {
    override val name = "applet_reload"
    override val description = "Reloads the current applet from disk"

    override suspend fun execute(args: JsonObject): JsonElement {
        scope.launch { applet.loadApplet("applet.json") }
        return JsonPrimitive("Reload triggered")
    }
}

class FeatureLaunchTool(private val applet: Applet, private val scope: CoroutineScope) : McpTool {
    override val name = "feature_launch"
    override val description = "Launches a specific feature by ID"
    override val parameters = listOf(McpParameter("featureId", "ID of the feature to launch"))

    override suspend fun execute(args: JsonObject): JsonElement {
        val featureId = args["featureId"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing featureId")
        scope.launch { applet.launchFeature(featureId) }
        return JsonPrimitive("Launch triggered")
    }
}

class ActionSendTool(private val applet: Applet, private val scope: CoroutineScope) : McpTool {
    override val name = "action_send"
    override val description = "Sends an action to the current feature"
    override val parameters = listOf(
        McpParameter("action", "Action name"),
        McpParameter("args", "Action arguments", type = "array", required = false)
    )

    override suspend fun execute(args: JsonObject): JsonElement {
        val action = args["action"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing action")
        val actionArgs = args["args"] as? JsonArray ?: JsonArray(emptyList())
        scope.launch { applet.handleAction(action, actionArgs) }
        return JsonPrimitive("Action sent")
    }
}

class ValuesGetTool(private val applet: Applet) : McpTool {
    override val name = "values_get"
    override val description = "Gets all current feature values"

    override suspend fun execute(args: JsonObject): JsonElement {
        val values = applet.currentFeature.value?.values?.value ?: emptyMap()
        return JsonObject(values)
    }
}

class ValueSetTool(private val applet: Applet) : McpTool {
    override val name = "value_set"
    override val description = "Sets a specific feature value"
    override val parameters = listOf(
        McpParameter("id", "Value ID"),
        McpParameter("value", "Value to set (can be any JSON type)", type = "any")
    )

    override suspend fun execute(args: JsonObject): JsonElement {
        val id = args["id"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing id")
        val value = args["value"] ?: JsonNull
        applet.updateValue(id, value)
        return JsonPrimitive("Value updated")
    }
}

abstract class StorageTool(protected val applet: Applet) : McpTool {
    protected fun getScope(args: JsonObject): StorageScope {
        val scopeStr = args["scope"]?.jsonPrimitive?.content ?: "applet"
        return StorageScope.entries.find { it.name.equals(scopeStr, ignoreCase = true) }
            ?: StorageScope.entries.find { it.key.equals(scopeStr, ignoreCase = true) }
            ?: StorageScope.Applet
    }

    protected fun isPersistent(args: JsonObject): Boolean {
        // Support both explicit persistent flag and legacy AppletPersistent-style strings
        val persistent = args["persistent"]?.jsonPrimitive?.booleanOrNull
        if (persistent != null) return persistent
        
        val scopeStr = args["scope"]?.jsonPrimitive?.content ?: ""
        return scopeStr.contains("persistent", ignoreCase = true)
    }
}

class StorageGetTool(applet: Applet) : StorageTool(applet) {
    override val name = "storage_get"
    override val description = "Gets a value from storage"
    override val parameters = listOf(
        McpParameter("scope", "Storage scope (applet, feature, module, module.global)"),
        McpParameter("key", "Storage key"),
        McpParameter("persistent", "Whether to use persistent storage", type = "boolean", required = false)
    )

    override suspend fun execute(args: JsonObject): JsonElement {
        val scope = getScope(args)
        val key = args["key"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing key")
        val storage = if (isPersistent(args)) applet.appletPersistentStorage else applet.appletTransientStorage
        return storage.getObject(scope, key) ?: JsonPrimitive("null")
    }
}

class StorageSetTool(applet: Applet) : StorageTool(applet) {
    override val name = "storage_set"
    override val description = "Sets a value in storage"
    override val parameters = listOf(
        McpParameter("scope", "Storage scope (applet, feature, module, module.global)"),
        McpParameter("key", "Storage key"),
        McpParameter("value", "Value to set", type = "any"),
        McpParameter("persistent", "Whether to use persistent storage", type = "boolean", required = false)
    )

    override suspend fun execute(args: JsonObject): JsonElement {
        val scope = getScope(args)
        val key = args["key"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing key")
        val value = args["value"] ?: JsonNull
        val storage = if (isPersistent(args)) applet.appletPersistentStorage else applet.appletTransientStorage
        storage.putObject(scope, key, value)
        return JsonPrimitive("Value set")
    }
}

class LogsGetTool(private val logBuffer: LogBufferSink) : McpTool {
    override val name = "logs_get"
    override val description = "Gets recent logs"
    override val parameters = listOf(
        McpParameter("limit", "Max number of logs to return", type = "number", required = false),
        McpParameter("offset", "Number of logs to skip", type = "number", required = false),
        McpParameter("tagFilter", "Filter logs by tag", required = false)
    )

    override suspend fun execute(args: JsonObject): JsonElement {
        val limit = args["limit"]?.jsonPrimitive?.content?.toIntOrNull() ?: 100
        val offset = args["offset"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val tagFilter = args["tagFilter"]?.jsonPrimitive?.content
        
        val allLogs = logBuffer.entries
        val filtered = if (tagFilter != null) {
            allLogs.filter { it.tag.contains(tagFilter, ignoreCase = true) }
        } else {
            allLogs
        }

        val resultLogs = filtered.drop(offset).take(limit)
        return McpTool.formatLogEntries(resultLogs)
    }
}

class LogsTailTool(private val logBuffer: LogBufferSink) : McpTool {
    override val name = "logs_tail"
    override val description = "Gets the last N logs"
    override val parameters = listOf(
        McpParameter("count", "Number of recent logs to return", type = "number", required = false),
        McpParameter("tagFilter", "Filter logs by tag", required = false)
    )

    override suspend fun execute(args: JsonObject): JsonElement {
        val count = args["count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 50
        val tagFilter = args["tagFilter"]?.jsonPrimitive?.content
        
        val allLogs = logBuffer.entries
        val filtered = if (tagFilter != null) {
            allLogs.filter { it.tag.contains(tagFilter, ignoreCase = true) }
        } else {
            allLogs
        }

        val resultLogs = filtered.takeLast(count)
        return McpTool.formatLogEntries(resultLogs)
    }
}

class LogsSinceTool(private val logBuffer: LogBufferSink) : McpTool {
    override val name = "logs_since"
    override val description = "Gets logs since a specific timestamp"
    override val parameters = listOf(
        McpParameter("timestamp", "Start timestamp (ms)", type = "number"),
        McpParameter("limit", "Max number of logs to return", type = "number", required = false),
        McpParameter("tagFilter", "Filter logs by tag", required = false)
    )

    override suspend fun execute(args: JsonObject): JsonElement {
        val timestamp = args["timestamp"]?.jsonPrimitive?.content?.toLongOrNull()
            ?: throw IllegalArgumentException("Missing or invalid timestamp")
        val limit = args["limit"]?.jsonPrimitive?.content?.toIntOrNull() ?: 100
        val tagFilter = args["tagFilter"]?.jsonPrimitive?.content
        
        val allLogs = logBuffer.entries
        val filtered = allLogs.filter { 
            it.timestamp > timestamp && (tagFilter == null || it.tag.contains(tagFilter, ignoreCase = true))
        }

        val resultLogs = filtered.take(limit)
        return McpTool.formatLogEntries(resultLogs)
    }
}

abstract class FileTool(protected val server: McpServer) : McpTool {
    protected fun resolvePath(args: JsonObject): File {
        val pathString = args["path"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing path")
        return server.resolveSafePath(pathString)
    }
}

class FileListTool(server: McpServer) : FileTool(server) {
    override val name = "file_list"
    override val description = "Lists files in the applet directory"
    override val parameters = listOf(McpParameter("path", "Directory path", required = false))

    override suspend fun execute(args: JsonObject): JsonElement {
        val pathString = args["path"]?.jsonPrimitive?.content ?: ""
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
}

class FileReadTool(server: McpServer) : FileTool(server) {
    override val name = "file_read"
    override val description = "Reads a file from the applet directory"
    override val parameters = listOf(McpParameter("path", "File path"))

    override suspend fun execute(args: JsonObject): JsonElement {
        val file = resolvePath(args)
        if (!file.exists()) throw IllegalArgumentException("File not found")
        return JsonPrimitive(file.readText())
    }
}

class FileWriteTool(server: McpServer) : FileTool(server) {
    override val name = "file_write"
    override val description = "Writes/Overwrites a file in the applet directory"
    override val parameters = listOf(
        McpParameter("path", "File path"),
        McpParameter("content", "File content")
    )

    override suspend fun execute(args: JsonObject): JsonElement {
        val content = args["content"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing content")
        val file = resolvePath(args)
        file.parentFile.mkdirs()
        file.writeText(content)
        return JsonPrimitive("File written")
    }
}

class FileDeleteTool(server: McpServer) : FileTool(server) {
    override val name = "file_delete"
    override val description = "Deletes a file in the applet directory"
    override val parameters = listOf(McpParameter("path", "File path"))

    override suspend fun execute(args: JsonObject): JsonElement {
        val file = resolvePath(args)
        if (file.exists()) {
            file.delete()
            return JsonPrimitive("File deleted")
        } else {
            return JsonPrimitive("File not found")
        }
    }
}

class LayoutGetTool(private val applet: Applet, override val name: String = "layout_get") : McpTool {
    override val description = "Gets the current feature layout tree"

    override suspend fun execute(args: JsonObject): JsonElement {
        val layout = applet.currentFeature.value?.layoutRoot?.value
        return if (layout != null) Json.encodeToJsonElement(layout) else JsonNull
    }
}

class ScreenshotTakeTool(private val server: McpServer) : McpTool {
    override val name = "screenshot_take"
    override val description = "Takes a screenshot of the window"

    override suspend fun execute(args: JsonObject): JsonElement {
        val base64Image = server.takeScreenshot()
        return buildJsonObject {
            put("type", "image")
            put("data", base64Image)
            put("mimeType", "image/png")
        }
    }
}

class AdbShellInputTool : McpTool {
    override val name = "adb_shell_input"
    override val description = "Executes an adb shell input command"
    override val parameters = listOf(McpParameter("args", "Command arguments (e.g., 'tap 100 200')"))

    override suspend fun execute(args: JsonObject): JsonElement {
        val adbArgs = args["args"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing args")
        val fullCommand = "adb shell input $adbArgs"
        val process = withContext(Dispatchers.IO) {
            Runtime.getRuntime().exec(fullCommand)
        }
        val output = withContext(Dispatchers.IO) {
            process.inputStream.bufferedReader().readText()
        }
        val error = withContext(Dispatchers.IO) {
            process.errorStream.bufferedReader().readText()
        }
        return buildJsonObject {
            put("stdout", output)
            put("stderr", error)
        }
    }
}
