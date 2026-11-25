package com.damn.aisuper.rpc

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class RpcNotificationEvent(
    val method: String,
    val params: JsonObject,
    val surface: String? = null,
    val timestampMs: Long = System.currentTimeMillis()
)

data class SurfaceRegistrySnapshot(
    val surfaces: List<SurfaceDescriptor> = emptyList(),
    val totalMethods: Int = 0,
    val mcpToolCount: Int = 0,
    val revision: Long = 0L
)

/**
 * Central transport-agnostic registry of all local and remote [RpcSurface]s
 * (OS controls, native Android modules, JS pods, and remote mounted pods).
 *
 * @wk-id rpc-surface-registry
 * @wk-tags kotlin, json-rpc, registry, discovery, notifications, pods
 * @wk-categories engineering-specs, agent-architecture
 *
 * Maintains live `StateFlow` snapshots for Compose UI, broadcasts push notifications via `SharedFlow`
 * to WebSocket clients, and builds unified discovery documents (`toDiscoveryJson`).
 * See [[unified-rpc-surfaces]].
 */
open class SurfaceRegistry {

    private val lock = Any()
    private val surfaceMap = linkedMapOf<String, RpcSurface>()
    private var revisionCounter = 0L

    private val _state = MutableStateFlow(SurfaceRegistrySnapshot())
    val state: StateFlow<SurfaceRegistrySnapshot> = _state.asStateFlow()

    private val _notifications = MutableSharedFlow<RpcNotificationEvent>(
        replay = 0,
        extraBufferCapacity = 64
    )
    val notifications: SharedFlow<RpcNotificationEvent> = _notifications.asSharedFlow()

    fun register(surface: RpcSurface) {
        val snap = synchronized(lock) {
            surfaceMap[surface.name] = surface
            revisionCounter += 1L
            buildSnapshotLocked().also { _state.value = it }
        }
        emitNotification(
            method = "notifications/surfacesChanged",
            params = buildJsonObject {
                put("action", "registered")
                put("surface", surface.name)
                put("revision", snap.revision)
            },
            surface = surface.name
        )
    }

    fun unregister(name: String): RpcSurface? {
        val (removed, snap) = synchronized(lock) {
            val prev = surfaceMap.remove(name)
            if (prev != null) {
                revisionCounter += 1L
                prev to buildSnapshotLocked().also { _state.value = it }
            } else {
                null to _state.value
            }
        }
        if (removed != null) {
            emitNotification(
                method = "notifications/surfacesChanged",
                params = buildJsonObject {
                    put("action", "unregistered")
                    put("surface", name)
                    put("revision", snap.revision)
                },
                surface = name
            )
        }
        return removed
    }

    fun clear() {
        val hadAny = synchronized(lock) {
            if (surfaceMap.isNotEmpty()) {
                surfaceMap.clear()
                revisionCounter += 1L
                _state.value = buildSnapshotLocked()
                true
            } else {
                false
            }
        }
        if (hadAny) {
            emitNotification(
                method = "notifications/surfacesChanged",
                params = buildJsonObject {
                    put("action", "cleared")
                    put("revision", _state.value.revision)
                }
            )
        }
    }

    fun refreshSnapshot() {
        synchronized(lock) {
            revisionCounter += 1L
            _state.value = buildSnapshotLocked()
        }
    }

    fun surfaces(): List<RpcSurface> = synchronized(lock) {
        surfaceMap.values.toList()
    }

    fun surfaceNames(): List<String> = synchronized(lock) {
        surfaceMap.keys.toList()
    }

    fun findSurface(name: String): RpcSurface? = synchronized(lock) {
        surfaceMap[name]
    }

    fun descriptors(surfaceFilter: String? = null): List<SurfaceDescriptor> =
        descriptors(RpcScope.fromSurfaceFilter(surfaceFilter))

    fun descriptors(scope: RpcScope): List<SurfaceDescriptor> {
        val targets = synchronized(lock) {
            surfaceMap.values.filter { scope.allowsSurface(it.name) }
        }
        return targets.map { surface ->
            val raw = surface.descriptor()
            if (!scope.readOnlyOnly && scope.disabledMethods.isEmpty()) {
                raw
            } else {
                raw.copy(methods = raw.methods.filter { scope.allowsMethod(raw.name, it) })
            }
        }
    }

    fun emitNotification(method: String, params: JsonObject, surface: String? = null) {
        _notifications.tryEmit(
            RpcNotificationEvent(
                method = method,
                params = params,
                surface = surface
            )
        )
    }

    /**
     * Produces a unified OpenRPC-style + backward-compatible Toolbox discovery payload.
     */
    fun toDiscoveryJson(
        mcpEnabled: Boolean,
        baseUrl: String = "",
        surfaceFilter: String? = null
    ): JsonObject = toDiscoveryJson(
        mcpEnabled = mcpEnabled,
        baseUrl = baseUrl,
        scope = RpcScope.fromSurfaceFilter(surfaceFilter)
    )

    fun toDiscoveryJson(
        mcpEnabled: Boolean,
        baseUrl: String = "",
        scope: RpcScope
    ): JsonObject {
        val cleanBase = baseUrl.trimEnd('/')
        val wsBase = if (cleanBase.isNotEmpty()) cleanBase.replaceFirst("http", "ws") else ""
        val descs = descriptors(scope)
        val totalMethods = descs.sumOf { it.methods.size }
        val totalMcpTools = descs.sumOf { d -> d.methods.count { it.exposeInMcp } }
        val toolboxId = scope.toolboxId?.takeIf { it.isNotBlank() && it != "global" }
        val rpcPath = if (toolboxId != null) "/api/toolboxes/$toolboxId/rpc" else "/api/rpc"
        val wsPath = if (toolboxId != null) "/api/toolboxes/$toolboxId/rpc/ws" else "/api/rpc/ws"
        val mcpPath = if (toolboxId != null) "/api/toolboxes/$toolboxId/mcp" else "/api/mcp"

        return buildJsonObject {
            put("protocol", "fluentos-jrpc-2.0")
            put("mcpEnabled", mcpEnabled)
            put("toolboxId", scope.toolboxId ?: "global")
            put("readOnlyOnly", scope.readOnlyOnly)
            put("osRpcEndpoint", if (cleanBase.isNotEmpty()) "$cleanBase$rpcPath" else rpcPath)
            put("osWsEndpoint", if (wsBase.isNotEmpty()) "$wsBase$wsPath" else wsPath)
            put("osMcpEndpoint", if (cleanBase.isNotEmpty()) "$cleanBase$mcpPath" else mcpPath)
            put("revision", _state.value.revision)
            put("surfaceCount", descs.size)
            put("totalMethods", totalMethods)
            put("totalTools", totalMcpTools)
            put("surfaces", buildJsonArray {
                descs.forEach { add(it.toJson(cleanBase)) }
            })
            // Flat method lookup table (mirrors rpc_reflection_solution.md `rpc.discover`)
            put("methods", buildJsonObject {
                descs.forEach { surface ->
                    surface.methods.forEach { m ->
                        put(m.qualifiedName, buildJsonObject {
                            put("surface", surface.name)
                            put("name", m.name)
                            put("description", m.description)
                            put("location", surface.location.wireName)
                            put("exposeInMcp", m.exposeInMcp)
                            if (m.aliases.isNotEmpty()) {
                                put("aliases", buildJsonArray {
                                    m.aliases.forEach { add(JsonPrimitive(it)) }
                                })
                            }
                            put("parameters", buildJsonArray {
                                m.parameters.forEach { add(it.toJson()) }
                            })
                        })
                    }
                }
            })
            // Backward-compatible `providers` view for existing Web/Android consumers
            put("providers", buildJsonArray {
                descs.forEach { surface ->
                    val mcpMethods = surface.methods.filter { it.exposeInMcp }
                    val mcpPath = "/api/mcp/${surface.name}"
                    add(
                        buildJsonObject {
                            put("name", surface.name)
                            put("description", surface.description)
                            put("kind", surface.kind.wireName)
                            put("location", surface.location.wireName)
                            surface.endpoint?.let { put("remoteEndpoint", it) }
                            put("mcpEndpoint", if (cleanBase.isNotEmpty()) "$cleanBase$mcpPath" else mcpPath)
                            put("rpcEndpoint", if (cleanBase.isNotEmpty()) "$cleanBase/api/rpc/${surface.name}" else "/api/rpc/${surface.name}")
                            put("toolCount", mcpMethods.size)
                            put("methodCount", surface.methods.size)
                            put("tools", buildJsonArray {
                                mcpMethods.forEach { t ->
                                    add(
                                        buildJsonObject {
                                            put("name", t.qualifiedName)
                                            put("description", t.description)
                                            put("parameterCount", t.parameters.size)
                                        }
                                    )
                                }
                            })
                        }
                    )
                }
            })
        }
    }

    private fun buildSnapshotLocked(): SurfaceRegistrySnapshot {
        val list = surfaceMap.values.map { it.descriptor() }
        return SurfaceRegistrySnapshot(
            surfaces = list,
            totalMethods = list.sumOf { it.methods.size },
            mcpToolCount = list.sumOf { d -> d.methods.count { it.exposeInMcp } },
            revision = revisionCounter
        )
    }
}

object Surfaces : SurfaceRegistry()
