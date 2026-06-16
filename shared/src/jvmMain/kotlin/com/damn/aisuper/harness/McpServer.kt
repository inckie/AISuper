package com.damn.aisuper.harness

import com.damn.aisuper.runtime.Applet
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

    val tools = listOf(
        AppletReloadTool(applet, scope),
        FeatureLaunchTool(applet, scope),
        ActionSendTool(applet, scope),
        ValuesGetTool(applet),
        ValueSetTool(applet),
        StorageGetTool(applet),
        StorageSetTool(applet),
        LogsGetTool(logBuffer),
        LogsTailTool(logBuffer),
        LogsSinceTool(logBuffer),
        FileListTool(this),
        FileReadTool(this),
        FileWriteTool(this),
        FileDeleteTool(this),
        LayoutGetTool(applet),
        LayoutGetTool(applet, "ui_state_get"),
        ScreenshotTakeTool(this),
        AdbShellInputTool()
    ).associateBy { it.name }

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
                tools.values.forEach { tool ->
                    add(buildJsonObject {
                        put("name", tool.name)
                        put("description", tool.description)
                        put("inputSchema", buildJsonObject {
                            put("type", "object")
                            put("properties", buildJsonObject {
                                tool.parameters.forEach { param ->
                                    put(param.name, buildJsonObject {
                                        put("type", param.type)
                                        put("description", param.description)
                                    })
                                }
                            })
                            put("required", buildJsonArray {
                                tool.parameters.filter { it.required }.forEach { add(it.name) }
                            })
                        })
                    })
                }
            })
        }
    }

    private suspend fun handleCallTool(params: JsonObject): JsonElement {
        val name = params["name"]?.jsonPrimitive?.content
        val args = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())

        val tool = tools[name] ?: throw IllegalArgumentException("Unknown tool: $name")
        val result = tool.execute(args)

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

    suspend fun takeScreenshot(): String {
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

    fun resolveSafePath(pathString: String): File {
        val root = getAppletRoot()?.toAbsolutePath()?.normalize()
            ?: throw IllegalStateException("Applet root not found")

        val resolved = root.resolve(pathString).toAbsolutePath().normalize()
        if (!resolved.startsWith(root)) {
            throw IllegalArgumentException("Access denied: path is outside of applet directory")
        }
        return resolved.toFile()
    }

    fun handleLogsGet(limit: Int, offset: Int, tagFilter: String?): JsonElement {
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
