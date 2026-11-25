package com.damn.aisuper.rpc

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Proxy [RpcSurface] representing a remote Pod or module hosted over HTTP or WebSocket JSON-RPC 2.0.
 *
 * @wk-id rpc-remote-surface
 * @wk-tags kotlin, json-rpc, remote-pod, proxy, discovery, mcp
 * @wk-categories engineering-specs, agent-architecture
 *
 * Calls `rpc.discover` on the remote endpoint to self-populate its [SurfaceDescriptor],
 * making the remote Pod indistinguishable from local modules to in-process callers, Web UI, and MCP.
 * See [[unified-rpc-surfaces]] and [[pod-architecture]].
 */
class RemoteRpcSurface(
    override val name: String,
    private val transport: RpcTransport,
    private val remoteSurfaceName: String? = null,
    initialDescription: String = ""
) : RpcSurface {

    @kotlin.concurrent.Volatile
    private var cachedDescriptor: SurfaceDescriptor = SurfaceDescriptor(
        name = name,
        description = initialDescription.ifBlank { "Remote RPC surface at ${transport.endpointDescription}" },
        kind = SurfaceKind.REMOTE_POD,
        location = SurfaceLocation.REMOTE,
        endpoint = transport.endpointDescription,
        methods = emptyList()
    )

    override fun descriptor(): SurfaceDescriptor = cachedDescriptor

    /**
     * Queries `rpc.discover` on the remote endpoint and updates the cached [SurfaceDescriptor].
     */
    suspend fun refreshDiscovery(): Result<SurfaceDescriptor> {
        val discoverParams = if (!remoteSurfaceName.isNullOrBlank()) {
            RpcParams.Named(buildJsonObject { put("surface", remoteSurfaceName) })
        } else {
            RpcParams.Empty
        }

        val res = transport.call("rpc.discover", discoverParams)
        if (res is RpcResult.Error) {
            return Result.failure(RpcException(res.code, res.message, res.data))
        }
        val payload = (res as RpcResult.Success).data as? JsonObject
            ?: return Result.failure(IllegalStateException("Remote rpc.discover did not return a JSON object"))

        val parsed = extractDescriptorFromDiscovery(payload)
        cachedDescriptor = parsed
        return Result.success(parsed)
    }

    private fun extractDescriptorFromDiscovery(payload: JsonObject): SurfaceDescriptor {
        val surfacesArr = payload["surfaces"] as? JsonArray
        if (surfacesArr != null && surfacesArr.isNotEmpty()) {
            val targetObj = if (!remoteSurfaceName.isNullOrBlank()) {
                surfacesArr.mapNotNull { it as? JsonObject }.firstOrNull {
                    (it["name"] as? JsonPrimitive)?.content == remoteSurfaceName
                }
            } else {
                surfacesArr.firstOrNull() as? JsonObject
            }
            if (targetObj != null) {
                val parsed = SurfaceDescriptor.fromJson(
                    obj = targetObj,
                    overrideLocation = SurfaceLocation.REMOTE,
                    overrideEndpoint = transport.endpointDescription
                )
                if (parsed != null) {
                    val renamedMethods = parsed.methods.map { m ->
                        m.copy(qualifiedName = "$name.${m.name}")
                    }
                    return parsed.copy(
                        name = name,
                        kind = SurfaceKind.REMOTE_POD,
                        location = SurfaceLocation.REMOTE,
                        endpoint = transport.endpointDescription,
                        methods = renamedMethods
                    )
                }
            }
        }

        // Fallback: flat `methods` dictionary (matches rpc_reflection_solution.md format)
        val flatMethods = payload["methods"] as? JsonObject
        val extractedMethods = flatMethods?.entries?.mapNotNull { (rawKey, rawVal) ->
            val mObj = rawVal as? JsonObject ?: return@mapNotNull null
            val local = if (rawKey.contains('.')) rawKey.substringAfter('.') else rawKey
            MethodDescriptor.fromJson(
                surfaceName = name,
                obj = buildJsonObject {
                    put("name", local)
                    mObj.forEach { (k, v) -> if (k != "name") put(k, v) }
                }
            )
        } ?: emptyList()

        return cachedDescriptor.copy(
            methods = extractedMethods,
            location = SurfaceLocation.REMOTE,
            endpoint = transport.endpointDescription
        )
    }

    override suspend fun invoke(
        method: String,
        params: RpcParams,
        context: RpcCallContext
    ): RpcResult {
        val cleanLocal = if (method.startsWith("$name.")) method.removePrefix("$name.") else method
        val remoteMethod = if (!remoteSurfaceName.isNullOrBlank()) {
            "$remoteSurfaceName.$cleanLocal"
        } else {
            cleanLocal
        }
        return transport.call(
            method = remoteMethod,
            params = params,
            context = context.copy(transport = transport.id)
        )
    }

    suspend fun close() {
        transport.close()
    }
}
