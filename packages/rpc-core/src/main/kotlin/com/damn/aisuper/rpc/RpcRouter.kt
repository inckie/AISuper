package com.damn.aisuper.rpc

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * Transport-agnostic JSON-RPC 2.0 Router over [SurfaceRegistry].
 *
 * @wk-id rpc-router
 * @wk-tags kotlin, json-rpc, router, discovery, batch, pods
 * @wk-categories engineering-specs, agent-architecture
 *
 * Handles:
 * - Built-in introspection (`rpc.discover`, `rpc.listSurfaces`, `rpc.ping`)
 * - Direct in-process calls ([call]) without JSON envelope serialization
 * - Full JSON-RPC 2.0 single and batch message envelopes ([handleJsonRpc]) with named or positional params
 * - Surface-scoped routing (`surfaceScope != null`) for per-pod endpoints
 * See [[unified-rpc-surfaces]].
 */
class RpcRouter(
    val registry: SurfaceRegistry = Surfaces,
    private val mcpEnabledSupplier: () -> Boolean = { true },
    private val baseUrlSupplier: () -> String = { "" }
) {

    suspend fun call(
        method: String,
        params: RpcParams = RpcParams.Empty,
        surfaceScope: String? = null,
        context: RpcCallContext = RpcCallContext.LOCAL
    ): RpcResult = call(
        method = method,
        params = params,
        scope = RpcScope.fromSurfaceFilter(surfaceScope),
        context = context
    )

    suspend fun call(
        method: String,
        params: RpcParams = RpcParams.Empty,
        scope: RpcScope,
        context: RpcCallContext = RpcCallContext.LOCAL
    ): RpcResult {
        val trimmed = method.trim()
        if (trimmed.isEmpty()) {
            return RpcResult.Error(RpcErrorCodes.INVALID_REQUEST, "Method name must be non-empty")
        }

        // Built-in JSON-RPC meta-methods
        when (trimmed) {
            "rpc.discover" -> {
                val filterFromParams = when (params) {
                    is RpcParams.Named -> (params.values["surface"] as? JsonPrimitive)?.contentOrNull?.trim()
                        ?.takeIf { it.isNotEmpty() }
                    is RpcParams.Positional -> (params.values.firstOrNull() as? JsonPrimitive)?.contentOrNull?.trim()
                        ?.takeIf { it.isNotEmpty() }
                    RpcParams.Empty -> null
                }
                val effectiveScope = scope.intersectSurface(filterFromParams)
                return RpcResult.Success(
                    registry.toDiscoveryJson(
                        mcpEnabled = mcpEnabledSupplier(),
                        baseUrl = baseUrlSupplier(),
                        scope = effectiveScope
                    )
                )
            }
            "rpc.listSurfaces", "rpc.list_surfaces" -> {
                val descs = registry.descriptors(scope)
                return RpcResult.Success(
                    buildJsonObject {
                        scope.toolboxId?.let { put("toolboxId", it) }
                        put("surfaces", buildJsonArray {
                            descs.forEach { d ->
                                add(buildJsonObject {
                                    put("name", d.name)
                                    put("description", d.description)
                                    put("kind", d.kind.wireName)
                                    put("location", d.location.wireName)
                                    put("methodCount", d.methods.size)
                                    put("mcpToolCount", d.methods.count { it.exposeInMcp })
                                })
                            }
                        })
                    }
                )
            }
            "rpc.ping" -> {
                return RpcResult.Success(
                    buildJsonObject {
                        put("ok", true)
                        put("timestamp", System.currentTimeMillis())
                        scope.toolboxId?.let { put("toolboxId", it) }
                        scope.defaultSurface?.takeIf { it.isNotBlank() }?.let { put("surface", it) }
                    }
                )
            }
        }

        val (targetSurfaceName, localMethod) = if (trimmed.contains('.')) {
            val dotIdx = trimmed.indexOf('.')
            if (dotIdx <= 0 || dotIdx == trimmed.lastIndex) {
                return RpcResult.Error(
                    RpcErrorCodes.METHOD_NOT_FOUND,
                    "Method '$trimmed' must be qualified as '<surface>.<method>' (or call 'rpc.discover')"
                )
            }
            trimmed.substring(0, dotIdx) to trimmed.substring(dotIdx + 1)
        } else if (!scope.defaultSurface.isNullOrBlank()) {
            scope.defaultSurface to trimmed
        } else {
            return RpcResult.Error(
                RpcErrorCodes.METHOD_NOT_FOUND,
                "Method '$trimmed' must be qualified as '<surface>.<method>' (or call 'rpc.discover')"
            )
        }

        if (!scope.allowsSurface(targetSurfaceName)) {
            val scopeLabel = scope.toolboxId?.let { "toolbox '$it'" }
                ?: scope.defaultSurface?.let { "surface '$it'" }
                ?: "current scope"
            return RpcResult.Error(
                RpcErrorCodes.METHOD_NOT_FOUND,
                "Surface '$targetSurfaceName' is not enabled in $scopeLabel"
            )
        }

        val surface = registry.findSurface(targetSurfaceName)
            ?: return RpcResult.Error(
                RpcErrorCodes.METHOD_NOT_FOUND,
                "Surface '$targetSurfaceName' not found"
            )

        // Enforce read-only / disabled-method policies if active on this scope
        if (scope.readOnlyOnly || scope.disabledMethods.isNotEmpty()) {
            val methodDesc = surface.descriptor().methods.firstOrNull { m ->
                m.name == localMethod || localMethod in m.aliases
            }
            if (methodDesc != null && !scope.allowsMethod(targetSurfaceName, methodDesc)) {
                val reason = if (scope.readOnlyOnly && !methodDesc.readOnly) {
                    "Method '${methodDesc.qualifiedName}' is blocked because toolbox '${scope.toolboxId ?: "scoped"}' is read-only"
                } else {
                    "Method '${methodDesc.qualifiedName}' is disabled in toolbox '${scope.toolboxId ?: "scoped"}'"
                }
                return RpcResult.Error(RpcErrorCodes.METHOD_NOT_FOUND, reason)
            }
        }

        return try {
            surface.invoke(localMethod, params, context)
        } catch (e: RpcException) {
            RpcResult.Error(e.code, e.message, e.data)
        } catch (e: Exception) {
            RpcResult.Error(
                RpcErrorCodes.INTERNAL_ERROR,
                e.message ?: "Internal error calling '$targetSurfaceName.$localMethod'"
            )
        }
    }

    /**
     * Handles raw JSON-RPC 2.0 single requests or batch arrays.
     * Returns `null` when every item in the payload is a notification (no `id`).
     */
    suspend fun handleJsonRpc(
        rawMessage: String,
        surfaceScope: String? = null,
        context: RpcCallContext = RpcCallContext.LOCAL
    ): String? = handleJsonRpc(
        rawMessage = rawMessage,
        scope = RpcScope.fromSurfaceFilter(surfaceScope),
        context = context
    )

    suspend fun handleJsonRpc(
        rawMessage: String,
        scope: RpcScope,
        context: RpcCallContext = RpcCallContext.LOCAL
    ): String? {
        val parsed = try {
            jsonParser.parseToJsonElement(rawMessage)
        } catch (e: Exception) {
            return formatError(JsonNull, RpcErrorCodes.PARSE_ERROR, "Parse error: ${e.message}").toString()
        }

        return when (parsed) {
            is JsonObject -> handleSingleObject(parsed, scope, context)?.toString()
            is JsonArray -> {
                if (parsed.isEmpty()) {
                    return formatError(JsonNull, RpcErrorCodes.INVALID_REQUEST, "Empty JSON-RPC batch").toString()
                }
                val replies = mutableListOf<JsonElement>()
                for (entry in parsed) {
                    val reply = if (entry is JsonObject) {
                        handleSingleObject(entry, scope, context)
                    } else {
                        formatError(JsonNull, RpcErrorCodes.INVALID_REQUEST, "Batch entry must be a JSON object")
                    }
                    if (reply != null) {
                        replies.add(reply)
                    }
                }
                if (replies.isEmpty()) null else JsonArray(replies).toString()
            }
            else -> formatError(
                JsonNull,
                RpcErrorCodes.INVALID_REQUEST,
                "JSON-RPC message must be a JSON object or array"
            ).toString()
        }
    }

    private suspend fun handleSingleObject(
        obj: JsonObject,
        scope: RpcScope,
        context: RpcCallContext
    ): JsonObject? {
        val hasId = obj.containsKey("id")
        val rawId = obj["id"]
        val isNotification = !hasId || rawId == null || rawId is JsonNull
        val id: JsonElement = rawId ?: JsonNull

        val method = (obj["method"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return if (isNotification) null else formatError(id, RpcErrorCodes.INVALID_REQUEST, "Missing 'method'")

        val params = try {
            RpcParams.fromJsonElement(obj["params"])
        } catch (e: RpcException) {
            return if (isNotification) null else formatError(id, e.code, e.message, e.data)
        }

        val outcome = call(
            method = method,
            params = params,
            scope = scope,
            context = context
        )

        if (isNotification) return null

        return when (outcome) {
            is RpcResult.Success -> buildJsonObject {
                put("jsonrpc", JSON_RPC_VERSION)
                put("id", id)
                put("result", outcome.data)
            }
            is RpcResult.Error -> formatError(id, outcome.code, outcome.message, outcome.data)
        }
    }

    private fun formatError(
        id: JsonElement,
        code: Int,
        message: String,
        data: JsonElement? = null
    ): JsonObject = buildJsonObject {
        put("jsonrpc", JSON_RPC_VERSION)
        put("id", id)
        put("error", buildJsonObject {
            put("code", code)
            put("message", message)
            if (data != null) put("data", data)
        })
    }

    companion object {
        const val JSON_RPC_VERSION = "2.0"

        private val jsonParser = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}
