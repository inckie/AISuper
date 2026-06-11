package com.damn.aisuper.harness

import com.damn.aisuper.runtime.Applet
import com.damn.aisuper.storage.StorageScope
import com.damn.aisuper.util.LogBufferSink
import com.damn.aisuper.util.Logger
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.milliseconds

@Serializable
data class McpRequest(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val method: String,
    val params: JsonObject = JsonObject(emptyMap())
)

@Serializable
data class McpResponse(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val result: JsonElement? = null,
    val error: McpError? = null
)

@Serializable
data class McpError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)

class McpServer(
    private val applet: Applet,
    private val port: Int = 8081,
    private val logBuffer: LogBufferSink,
    private val appletRoot: File? = null
) {
    private var windowBoundsProvider: (() -> java.awt.Rectangle?)? = null

    fun setWindowBoundsProvider(provider: () -> java.awt.Rectangle?) {
        windowBoundsProvider = provider
    }

    private val scope = CoroutineScope(Dispatchers.Default)
    private val sessions = ConcurrentHashMap<String, Channel<JsonObject>>()

    fun start() {
        val server = embeddedServer(Netty, port = port, host = "0.0.0.0") {
            install(ContentNegotiation) {
                json(Json {
                    encodeDefaults = false
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            install(CORS) {
                anyHost()
                allowHeader(HttpHeaders.ContentType)
                allowHeader("Mcp-Session-Id")
                allowMethod(HttpMethod.Options)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Get)
            }
            routing {
                // SSE Endpoint for standard MCP
                get("/sse") {
                    val sessionId = UUID.randomUUID().toString()
                    val channel = Channel<JsonObject>(Channel.UNLIMITED)
                    sessions[sessionId] = channel

                    Logger.i("MCP") { "New SSE session established: $sessionId" }
                    call.response.cacheControl(CacheControl.NoCache(null))
                    
                    try {
                        call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                            write("event: endpoint\n")
                            write("data: http://127.0.0.1:$port/mcp?sessionId=$sessionId\n\n")
                            flush()

                            for (message in channel) {
                                write("event: message\n")
                                write("data: ${message.toString()}\n\n")
                                flush()
                            }
                        }
                    } finally {
                        sessions.remove(sessionId)
                        Logger.i("MCP") { "SSE session closed: $sessionId" }
                    }
                }

                post("/mcp") {
                    try {
                        val sessionId = call.request.queryParameters["sessionId"]
                        val request = call.receive<McpRequest>()
                        Logger.d("MCP") { "Received request: ${request.method} (Session: $sessionId)" }
                        
                        if (request.id == null) {
                            // Notification
                            handleNotification(request)
                            call.respond(HttpStatusCode.Accepted, "")
                        } else {
                            // Request
                            val response = try {
                                val result = handleRequest(request)
                                createResponse(request.id, result = result)
                            } catch (e: Exception) {
                                Logger.e("MCP", throwable = e) { "Error handling ${request.method}: ${e.message}" }
                                createResponse(request.id, error = McpError(-32603, e.message ?: "Internal error"))
                            }

                            if (sessionId != null && sessions.containsKey(sessionId)) {
                                sessions[sessionId]?.send(response)
                                call.respond(HttpStatusCode.Accepted, "")
                            } else {
                                call.respond(response)
                            }
                        }
                    } catch (e: Exception) {
                        Logger.e("MCP", throwable = e) { "Critical error in POST /mcp: ${e.message}" }
                        call.respond(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    }
                }

                get("/mcp/tools") {
                    call.respond(createResponse(JsonPrimitive("list"), result = handleListTools()))
                }

                get("/mcp/logs") {
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                    val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                    val tagFilter = call.request.queryParameters["tagFilter"]
                    val result = handleLogsGet(limit, offset, tagFilter)
                    call.respond(createResponse(JsonPrimitive("logs"), result = result))
                }
            }
        }
        
        server.start(wait = false)
        
        scope.launch {
            try {
                delay(500.milliseconds)
                applet.updateValue("mcp_url", JsonPrimitive("http://127.0.0.1:$port/sse"))
                Logger.i("Harness") { "MCP Server started on http://0.0.0.0:$port/sse" }
            } catch (e: Exception) {
                Logger.e("Harness", throwable = e) { "Error during server post-startup: ${e.message}" }
            }
        }
    }

    private suspend fun handleRequest(request: McpRequest): JsonElement {
        return when (request.method) {
            "initialize" -> handleInitialize()
            "tools/list" -> handleListTools()
            "tools/call" -> handleCallTool(request.params)
            "resources/list" -> handleListResources()
            "resources/read" -> handleReadResource(request.params)
            "prompts/list" -> buildJsonObject { put("prompts", buildJsonArray { }) }
            "logging/setLevel" -> JsonPrimitive("ok")
            else -> throw IllegalArgumentException("Unknown method: ${request.method}")
        }
    }

    private fun handleNotification(request: McpRequest) {
        when (request.method) {
            "notifications/initialized" -> Logger.i("MCP") { "Client successfully initialized" }
            "notifications/cancelled" -> Logger.d("MCP") { "Request cancelled by client" }
        }
    }

    private fun createResponse(id: JsonElement?, result: JsonElement? = null, error: McpError? = null): JsonObject {
        return buildJsonObject {
            put("jsonrpc", "2.0")
            if (id != null) put("id", id)
            if (error != null) {
                put("error", buildJsonObject {
                    put("code", error.code)
                    put("message", error.message)
                    error.data?.let { put("data", it) }
                })
            } else {
                put("result", result ?: JsonNull)
            }
        }
    }

    private fun handleInitialize(): JsonElement {
        return buildJsonObject {
            put("protocolVersion", "2024-11-05")
            put("capabilities", buildJsonObject {
                put("tools", buildJsonObject {
                    put("listChanged", true)
                })
                put("resources", buildJsonObject {
                    put("listChanged", false)
                    put("subscribe", false)
                })
                put("logging", buildJsonObject {})
            })
            put("serverInfo", buildJsonObject {
                put("name", "aisuper-harness")
                put("version", "1.0.0")
            })
        }
    }

    private fun handleListTools(): JsonElement {
        return buildJsonObject {
            put("tools", buildJsonArray {
                addTool("applet_reload", "Reloads the current applet from disk")
                addTool("feature_launch", "Launches a specific feature by ID", listOf("featureId"))
                addTool("action_send", "Sends an action to the current feature", listOf("action", "args"))
                addTool("values_get", "Gets all current feature values")
                addTool("value_set", "Sets a specific feature value", listOf("id", "value"))
                addTool("storage_get", "Gets a value from storage", listOf("scope", "key"))
                addTool("storage_set", "Sets a value in storage", listOf("scope", "key", "value"))
                addTool("logs_get", "Gets recent logs", listOf("limit", "offset", "tagFilter"))
                addTool("file_list", "Lists files in the applet directory", listOf("path"))
                addTool("file_read", "Reads a file from the applet directory", listOf("path"))
                addTool("file_write", "Writes/Overwrites a file in the applet directory", listOf("path", "content"))
                addTool("file_delete", "Deletes a file in the applet directory", listOf("path"))
                addTool("layout_get", "Gets the current feature layout tree")
                addTool("screenshot_take", "Takes a screenshot of the window")
                addTool("ui_state_get", "Gets the current UI state (alias for layout_get)")
                addTool("adb_shell_input", "Executes an adb shell input command", listOf("args"))
            })
        }
    }

    private fun JsonArrayBuilder.addTool(name: String, description: String, params: List<String> = emptyList()) {
        add(buildJsonObject {
            put("name", name)
            put("description", description)
            put("inputSchema", buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    params.forEach { param ->
                        put(param, buildJsonObject { put("type", "string") })
                    }
                })
            })
        })
    }

    private suspend fun handleCallTool(params: JsonObject): JsonElement {
        val name = params["name"]?.let { (it as? JsonPrimitive)?.content }
        val args = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())

        val result: JsonElement = when (name) {
            "applet_reload" -> {
                scope.launch { applet.loadApplet("applet.json") }
                JsonPrimitive("Reload triggered")
            }
            "feature_launch" -> {
                val featureId = args["featureId"]?.let { (it as? JsonPrimitive)?.content }
                    ?: throw IllegalArgumentException("Missing featureId")
                scope.launch { applet.launchFeature(featureId) }
                JsonPrimitive("Launch triggered")
            }
            "action_send" -> {
                val action = args["action"]?.let { (it as? JsonPrimitive)?.content }
                    ?: throw IllegalArgumentException("Missing action")
                val actionArgs = args["args"] as? JsonArray ?: JsonArray(emptyList())
                scope.launch { applet.handleAction(action, actionArgs) }
                JsonPrimitive("Action sent")
            }
            "values_get" -> {
                val values = applet.currentFeature.value?.values?.value ?: emptyMap()
                JsonObject(values)
            }
            "value_set" -> {
                val id = args["id"]?.let { (it as? JsonPrimitive)?.content } ?: throw IllegalArgumentException("Missing id")
                val value = args["value"] ?: JsonNull
                applet.updateValue(id, value)
                JsonPrimitive("Value updated")
            }
            "storage_get" -> {
                val scopeStr = args["scope"]?.let { (it as? JsonPrimitive)?.content } ?: "Applet"
                val key = args["key"]?.let { (it as? JsonPrimitive)?.content } ?: throw IllegalArgumentException("Missing key")
                val scope = StorageScope.entries.find { it.name.equals(scopeStr, ignoreCase = true) } ?: StorageScope.Applet
                val storage = if (scopeStr.contains("persistent", ignoreCase = true)) applet.appletPersistentStorage else applet.appletTransientStorage
                val value = storage.getObject(scope, key) ?: JsonPrimitive("null")
                value
            }
            "storage_set" -> {
                val scopeStr = args["scope"]?.let { (it as? JsonPrimitive)?.content } ?: "Applet"
                val key = args["key"]?.let { (it as? JsonPrimitive)?.content } ?: throw IllegalArgumentException("Missing key")
                val value = args["value"] ?: JsonNull
                val scope = StorageScope.entries.find { it.name.equals(scopeStr, ignoreCase = true) } ?: StorageScope.Applet
                val storage = if (scopeStr.contains("persistent", ignoreCase = true)) applet.appletPersistentStorage else applet.appletTransientStorage
                storage.putObject(scope, key, value)
                JsonPrimitive("Value set")
            }
            "logs_get" -> {
                val limit = args["limit"]?.let { (it as? JsonPrimitive)?.content?.toIntOrNull() } ?: 100
                val offset = args["offset"]?.let { (it as? JsonPrimitive)?.content?.toIntOrNull() } ?: 0
                val tagFilter = args["tagFilter"]?.let { (it as? JsonPrimitive)?.content }
                handleLogsGet(limit, offset, tagFilter)
            }
            "file_list" -> {
                val path = args["path"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                val root = getAppletRoot() ?: throw IllegalStateException("Applet root not found")
                val dir = if (path.isEmpty()) root else root.resolve(path)
                if (!dir.toFile().exists()) throw IllegalArgumentException("Directory not found: $path")
                
                buildJsonObject {
                    put("files", buildJsonArray {
                        dir.toFile().listFiles()?.forEach { file ->
                            add(buildJsonObject {
                                put("name", file.name)
                                put("isDirectory", file.isDirectory)
                                put("size", file.length())
                            })
                        }
                    })
                }
            }
            "file_read" -> {
                val path = args["path"]?.let { (it as? JsonPrimitive)?.content } ?: throw IllegalArgumentException("Missing path")
                val root = getAppletRoot() ?: throw IllegalStateException("Applet root not found")
                val file = root.resolve(path).toFile()
                if (!file.exists()) throw IllegalArgumentException("File not found: $path")
                JsonPrimitive(file.readText())
            }
            "file_write" -> {
                val path = args["path"]?.let { (it as? JsonPrimitive)?.content } ?: throw IllegalArgumentException("Missing path")
                val content = args["content"]?.let { (it as? JsonPrimitive)?.content } ?: throw IllegalArgumentException("Missing content")
                val root = getAppletRoot() ?: throw IllegalStateException("Applet root not found")
                val file = root.resolve(path).toFile()
                file.parentFile.mkdirs()
                file.writeText(content)
                JsonPrimitive("File written: $path")
            }
            "file_delete" -> {
                val path = args["path"]?.let { (it as? JsonPrimitive)?.content } ?: throw IllegalArgumentException("Missing path")
                val root = getAppletRoot() ?: throw IllegalStateException("Applet root not found")
                val file = root.resolve(path).toFile()
                if (file.exists()) {
                    file.delete()
                    JsonPrimitive("File deleted: $path")
                } else {
                    JsonPrimitive("File not found: $path")
                }
            }
            "layout_get" -> {
                val layout = applet.currentFeature.value?.layoutRoot?.value
                if (layout != null) {
                    Json.encodeToJsonElement(layout)
                } else {
                    JsonNull
                }
            }
            "ui_state_get" -> {
                val layout = applet.currentFeature.value?.layoutRoot?.value
                if (layout != null) {
                    Json.encodeToJsonElement(layout)
                } else {
                    JsonNull
                }
            }
            "screenshot_take" -> {
                val base64Image = takeScreenshot()
                buildJsonObject {
                    put("type", "image")
                    put("data", base64Image)
                    put("mimeType", "image/png")
                }
            }
            "adb_shell_input" -> {
                val adbArgs = args["args"]?.let { (it as? JsonPrimitive)?.content }
                    ?: throw IllegalArgumentException("Missing args")
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
                buildJsonObject {
                    put("stdout", output)
                    put("stderr", error)
                }
            }
            else -> throw IllegalArgumentException("Unknown tool: $name")
        }

        return buildJsonObject {
            put("content", buildJsonArray {
                if (result is JsonObject && result.containsKey("type") && result.containsKey("data") && result["type"]?.let { (it as? JsonPrimitive)?.content } == "image") {
                    add(result)
                } else {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", result.toString())
                    })
                }
            })
        }
    }

    private suspend fun takeScreenshot(): String {
        val bounds = windowBoundsProvider?.invoke() ?: java.awt.Rectangle(0, 0, 1024, 768)
        val robot = java.awt.Robot()
        val screenShot = robot.createScreenCapture(bounds)
        val baos = ByteArrayOutputStream()
        withContext(Dispatchers.IO) {
            ImageIO.write(screenShot, "png", baos)
        }
        return Base64.getEncoder().encodeToString(baos.toByteArray())
    }

    private fun handleListResources(): JsonElement {
        return buildJsonObject {
            put("resources", buildJsonArray {
                add(buildJsonObject {
                    put("uri", "screenshot://current")
                    put("name", "Current Screenshot")
                    put("description", "A real-time screenshot of the current window")
                    put("mimeType", "image/png")
                })
            })
        }
    }

    private suspend fun handleReadResource(params: JsonObject): JsonElement {
        val uri = params["uri"]?.let { (it as? JsonPrimitive)?.content }
            ?: throw IllegalArgumentException("Missing uri")

        if (uri == "screenshot://current") {
            val base64Image = takeScreenshot()
            return buildJsonObject {
                put("contents", buildJsonArray {
                    add(buildJsonObject {
                        put("uri", uri)
                        put("mimeType", "image/png")
                        put("blob", base64Image)
                    })
                })
            }
        }

        throw IllegalArgumentException("Unknown resource: $uri")
    }

    private fun getAppletRoot(): java.nio.file.Path? {
        return appletRoot?.toPath() ?: java.nio.file.Paths.get("").toAbsolutePath()
    }

    private fun handleLogsGet(limit: Int, offset: Int, tagFilter: String?): JsonElement {
        val allLogs = logBuffer.entries
        val filtered = if (tagFilter != null) {
            allLogs.filter { it.tag.contains(tagFilter, ignoreCase = true) }
        } else {
            allLogs
        }

        val resultLogs = filtered.drop(offset).take(limit)

        return buildJsonObject {
            put("content", buildJsonArray {
                resultLogs.forEach { log ->
                    add(buildJsonObject {
                        put("level", log.level.name)
                        put("tag", log.tag)
                        put("message", log.message)
                        put("timestamp", log.timestamp)
                        log.throwable?.let { put("throwable", it) }
                    })
                }
            })
        }
    }
}
