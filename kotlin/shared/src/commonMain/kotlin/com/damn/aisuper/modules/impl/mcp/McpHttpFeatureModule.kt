package com.damn.aisuper.modules.impl.mcp

import com.damn.aisuper.modules.FeatureModule
import com.damn.aisuper.modules.FeatureModuleContext
import com.damn.aisuper.modules.FeatureModuleFactory
import com.damn.aisuper.runtime.ModuleDefinition
import com.damn.aisuper.util.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val mcpJson = Json { ignoreUnknownKeys = true }

class McpHttpFeatureModule(
    private val serverName: String,
    private val url: String,
    private val allowedGroups: Set<String>,
    private val customHeaders: Map<String, String> = emptyMap()
) : FeatureModule {

    private val client = HttpClient()

    override suspend fun attach(context: FeatureModuleContext) {
        context.registerFunction("mcpServerInfo") {
            buildJsonObject {
                put("name", JsonPrimitive(serverName))
                put("url", JsonPrimitive(url))
                put("groups", buildJsonArray { allowedGroups.forEach { add(JsonPrimitive(it)) } })
            }
        }

        context.registerSuspendFunction("mcpListTools") { args ->
            val group = args.stringAt(0)
            val tools = runCatching {
                listTools(groupFilter = group)
            }.getOrElse {
                Logger.e("MCP", serverName) { "tools/list failed: ${it.message}" }
                emptyList()
            }

            buildJsonArray {
                tools.forEach { add(it) }
            }
        }

        context.registerSuspendFunction("mcpCall") { args ->
            val group = args.stringAt(0) ?: return@registerSuspendFunction JsonPrimitive("Error: Missing group")
            val tool = args.stringAt(1) ?: return@registerSuspendFunction JsonPrimitive("Error: Missing tool")
            val payload = args.getOrNull(2) as? JsonObject ?: JsonObject(emptyMap())

            if (!groupAllowed(group)) {
                return@registerSuspendFunction JsonPrimitive("Error: MCP group '$group' is not allowed for server '$serverName'")
            }

            runCatching {
                callTool(group = group, tool = tool, arguments = payload)
            }.getOrElse {
                Logger.e("MCP", serverName) { "tools/call failed: ${it.message}" }
                JsonPrimitive("Error: ${it.message}")
            }
        }
    }

    override fun detach() {
        client.close()
    }

    private suspend fun listTools(groupFilter: String?): List<JsonObject> {
        val response = rpcCall(
            method = "tools/list",
            params = JsonObject(emptyMap()),
            id = 101
        )

        val tools = response.resultObject("result")
            ?.get("tools")
            ?.jsonArrayOrNull()
            ?: JsonArray(emptyList())

        return tools.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val fullName = obj.string("name") ?: return@mapNotNull null
            val group = fullName.substringBefore('.', missingDelimiterValue = "")
            if (!groupAllowed(group)) return@mapNotNull null
            if (!groupFilter.isNullOrBlank() && group != groupFilter) return@mapNotNull null

            buildJsonObject {
                put("name", JsonPrimitive(fullName.substringAfter('.', fullName)))
                put("group", JsonPrimitive(group))
                put("fullName", JsonPrimitive(fullName))
                put("description", obj["description"] ?: JsonPrimitive(""))
                put("inputSchema", obj["inputSchema"] ?: JsonObject(emptyMap()))
            }
        }
    }

    private suspend fun callTool(group: String, tool: String, arguments: JsonObject): JsonElement {
        val fullName = "$group.$tool"
        val response = rpcCall(
            method = "tools/call",
            params = buildJsonObject {
                put("name", JsonPrimitive(fullName))
                put("arguments", arguments)
            },
            id = 102
        )

        val result = response.resultObject("result") ?: return JsonNull
        val content = result["content"]?.jsonArrayOrNull() ?: JsonArray(emptyList())
        if (content.isEmpty()) return result

        val first = content.firstOrNull() as? JsonObject ?: return result
        val text = first.string("text")
        if (text.isNullOrBlank()) return result

        val parsed = runCatching { mcpJson.parseToJsonElement(text) }.getOrNull()
        return parsed ?: JsonPrimitive(text)
    }

    private suspend fun rpcCall(method: String, params: JsonObject, id: Int): JsonObject {
        val body = buildJsonObject {
            put("jsonrpc", JsonPrimitive("2.0"))
            put("id", JsonPrimitive(id))
            put("method", JsonPrimitive(method))
            put("params", params)
        }

        val responseText = client.post(url) {
            accept(ContentType.Application.Json)
            header("Content-Type", "application/json")
            // Add custom headers
            customHeaders.forEach { (key, value) ->
                header(key, value)
            }
            setBody(body.toString())
        }.bodyAsText()

        val envelope = mcpJson.parseToJsonElement(responseText).jsonObject
        val error = envelope["error"]
        if (error != null) {
            throw IllegalStateException("MCP error: $error")
        }
        return envelope
    }

    private fun groupAllowed(group: String): Boolean {
        return allowedGroups.isEmpty() || allowedGroups.contains(group)
    }
}

object McpHttpFeatureModuleFactory : FeatureModuleFactory {
    override val type: String = "mcpHttp"
    override val exposedFunctions: Set<String> = setOf("mcpServerInfo", "mcpListTools", "mcpCall")

    override suspend fun create(definition: ModuleDefinition): FeatureModule {
        val serverName = definition.name
        val config = definition.config
        val url = resolveServerUrl(config, serverName)
        val allowedGroups = config["groups"]
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            .orEmpty()

        // Parse custom headers from config
        val customHeaders = parseCustomHeaders(config)

        return McpHttpFeatureModule(
            serverName = serverName,
            url = url,
            allowedGroups = allowedGroups,
            customHeaders = customHeaders
        )
    }

    private fun parseCustomHeaders(config: Map<String, String>): Map<String, String> {
        val headersJson = config["headers"] ?: return emptyMap()
        return runCatching {
            val parsed = mcpJson.parseToJsonElement(headersJson).jsonObject
            parsed.mapValues { (_, value) ->
                try {
                    value.jsonPrimitive.contentOrNull ?: ""
                } catch (_: Exception) {
                    ""
                }
            }
        }.getOrElse { emptyMap() }
    }

    private fun resolveServerUrl(config: Map<String, String>, serverName: String): String {
        config["url"]?.takeIf { it.isNotBlank() }?.let { return it }

        val configJson = config["configJson"] ?: return ""
        val parsed = runCatching { mcpJson.parseToJsonElement(configJson).jsonObject }.getOrNull() ?: return ""

        val servers = parsed["mcpServers"] as? JsonObject ?: return ""
        val server = servers[serverName] as? JsonObject ?: return ""
        return server.string("url") ?: ""
    }
}

private fun JsonObject.string(key: String): String? {
    return this[key]?.let {
        try {
            it.jsonPrimitive.contentOrNull
        } catch (_: Exception) {
            null
        }
    }
}

private fun JsonElement.jsonArrayOrNull(): JsonArray? {
    return try {
        this.jsonArray
    } catch (_: Exception) {
        null
    }
}

private fun JsonObject.resultObject(key: String): JsonObject? {
    return this[key] as? JsonObject
}

private fun List<JsonElement>.stringAt(index: Int): String? {
    return getOrNull(index)?.let {
        try {
            it.jsonPrimitive.contentOrNull
        } catch (_: Exception) {
            null
        }
    }
}
