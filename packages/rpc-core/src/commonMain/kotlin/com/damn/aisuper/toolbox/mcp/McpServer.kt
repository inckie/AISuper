package com.damn.aisuper.toolbox.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import com.damn.aisuper.rpc.MethodDescriptor
import com.damn.aisuper.rpc.RpcCallContext
import com.damn.aisuper.rpc.RpcParams
import com.damn.aisuper.rpc.RpcResult
import com.damn.aisuper.rpc.RpcRouter
import com.damn.aisuper.rpc.RpcScope
import com.damn.aisuper.rpc.Surfaces
import com.damn.aisuper.toolbox.ParameterType

/**
 * Thin, transport-agnostic Model Context Protocol (MCP) JSON-RPC 2.0 adapter.
 *
 * @wk-id mcp-server-adapter
 * @wk-tags kotlin, mcp, json-rpc, adapter, ai-tools, pods
 * @wk-categories engineering-specs, agent-architecture
 *
 * Wraps [RpcRouter] / [com.damn.aisuper.rpc.SurfaceRegistry] so every local native module,
 * JS pod, and mounted remote pod automatically appears as MCP tools without duplicating schemas.
 * See [[unified-rpc-surfaces]].
 */
class McpServer(
    private val router: RpcRouter,
    private val scope: RpcScope,
    private val serverName: String = scope.toolboxId?.takeIf { it.isNotBlank() }?.let { "fluentos-$it" } ?: "fluentos",
    private val serverVersion: String = "1.0.0"
) {

    /**
     * Convenience constructor wrapping [RpcRouter] / [com.damn.aisuper.rpc.SurfaceRegistry]
     * with a single-surface filter (or global when `surfaceFilter == null`).
     */
    constructor(
        router: RpcRouter = RpcRouter(Surfaces),
        surfaceFilter: String? = null,
        serverName: String = if (surfaceFilter.isNullOrBlank()) "fluentos" else "fluentos-$surfaceFilter",
        serverVersion: String = "1.0.0"
    ) : this(
        router = router,
        scope = RpcScope.fromSurfaceFilter(surfaceFilter),
        serverName = serverName,
        serverVersion = serverVersion
    )

    suspend fun handleMessage(message: String): String? {
        val root: JsonObject = try {
            jsonParser.parseToJsonElement(message) as? JsonObject
                ?: return error(JsonNull, INVALID_REQUEST, "JSON-RPC message must be a JSON object")
        } catch (e: Exception) {
            return error(JsonNull, PARSE_ERROR, "Parse error: ${e.message}")
        }

        val rawId = root["id"]
        val id: JsonElement = rawId ?: JsonNull
        val isNotification = rawId == null || rawId is JsonNull
        val method = (root["method"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
            ?: return if (isNotification) null else error(id, INVALID_REQUEST, "Missing 'method'")
        val params = when (val rawParams = root["params"]) {
            null, is JsonNull -> JsonObject(emptyMap())
            is JsonObject -> rawParams
            else -> return if (isNotification) null else error(id, INVALID_PARAMS, "'params' must be a JSON object")
        }

        return try {
            when (method) {
                "initialize" -> result(id, initialize())
                "notifications/initialized" -> null
                "ping" -> result(id, JsonObject(emptyMap()))
                "tools/list" -> result(id, listTools())
                "tools/call" -> result(id, callTool(params))
                else -> if (isNotification) null else error(id, METHOD_NOT_FOUND, "Method not found: $method")
            }
        } catch (e: IllegalArgumentException) {
            if (isNotification) null else error(id, INVALID_PARAMS, e.message ?: "Invalid params")
        } catch (e: Exception) {
            if (isNotification) null else error(id, INTERNAL_ERROR, "Internal error: ${e.message}")
        }
    }

    private fun initialize(): JsonObject = buildJsonObject {
        put("protocolVersion", PROTOCOL_VERSION)
        put("capabilities", buildJsonObject {
            put("tools", buildJsonObject {
                put("listChanged", true)
            })
        })
        put("serverInfo", buildJsonObject {
            put("name", serverName)
            put("version", serverVersion)
        })
    }

    private fun listTools(): JsonObject {
        val methods = router.registry.descriptors(scope)
            .flatMap { surface -> surface.methods.filter { it.exposeInMcp } }
        return buildJsonObject {
            put("tools", buildJsonArray {
                methods.forEach { add(methodToMcpTool(it)) }
            })
        }
    }

    private suspend fun callTool(params: JsonObject): JsonObject {
        val name = (params["name"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Missing tool 'name'")

        val argsObject = when (val rawArgs = params["arguments"]) {
            null, is JsonNull -> JsonObject(emptyMap())
            is JsonObject -> rawArgs
            else -> throw IllegalArgumentException("'arguments' must be a JSON object")
        }

        val rpcRes = router.call(
            method = name,
            params = RpcParams.Named(argsObject),
            scope = scope,
            context = RpcCallContext.MCP
        )
        val toolResult = rpcRes.toToolResult()
        val text = toolResult.asText ?: toolResult.value.toString()
        return buildJsonObject {
            put("content", buildJsonArray {
                add(
                    buildJsonObject {
                        put("type", "text")
                        put("text", text)
                    }
                )
            })
            if (toolResult.isStructured) {
                put("structuredContent", toolResult.value)
            }
            put("isError", toolResult.isError)
        }
    }

    private fun methodToMcpTool(method: MethodDescriptor): JsonObject {
        val requiredList = mutableListOf<String>()
        val properties = buildJsonObject {
            method.parameters.forEach { param ->
                put(param.name, buildJsonObject {
                    put("type", param.type.schemaType)
                    put("description", param.description)
                    param.enumValues?.takeIf { it.isNotEmpty() }?.let { values ->
                        put("enum", buildJsonArray {
                            values.forEach { add(JsonPrimitive(it)) }
                        })
                    }
                    if (param.type == ParameterType.ARRAY) {
                        val itemType = (param.itemsType ?: ParameterType.STRING).schemaType
                        put("items", buildJsonObject {
                            put("type", itemType)
                        })
                    }
                })
                if (param.required) {
                    requiredList.add(param.name)
                }
            }
        }

        val inputSchema = buildJsonObject {
            put("type", "object")
            put("properties", properties)
            if (requiredList.isNotEmpty()) {
                put("required", buildJsonArray {
                    requiredList.forEach { add(JsonPrimitive(it)) }
                })
            }
        }

        return buildJsonObject {
            put("name", method.qualifiedName)
            put("description", method.description)
            put("inputSchema", inputSchema)
        }
    }

    private fun result(id: JsonElement, resultObj: JsonObject): String =
        buildJsonObject {
            put("jsonrpc", JSON_RPC_VERSION)
            put("id", id)
            put("result", resultObj)
        }.toString()

    private fun error(id: JsonElement, code: Int, message: String): String =
        buildJsonObject {
            put("jsonrpc", JSON_RPC_VERSION)
            put("id", id)
            put("error", buildJsonObject {
                put("code", code)
                put("message", message)
            })
        }.toString()

    companion object {
        const val JSON_RPC_VERSION = "2.0"
        const val PROTOCOL_VERSION = "2024-11-05"

        const val PARSE_ERROR = -32700
        const val INVALID_REQUEST = -32600
        const val METHOD_NOT_FOUND = -32601
        const val INVALID_PARAMS = -32602
        const val INTERNAL_ERROR = -32603

        private val jsonParser = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}
