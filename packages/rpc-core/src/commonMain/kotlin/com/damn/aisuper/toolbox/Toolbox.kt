package com.damn.aisuper.toolbox

import com.damn.aisuper.rpc.RpcLock
import com.damn.aisuper.rpc.withRpcLock

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class ToolSummary(
    val name: String,
    val description: String,
    val parameterCount: Int
)

data class ProviderSummary(
    val name: String,
    val description: String,
    val kind: ToolProviderKind,
    val mcpEndpoint: String,
    val tools: List<ToolSummary>
)

data class ToolboxSnapshot(
    val providers: List<ProviderSummary> = emptyList(),
    val totalTools: Int = 0,
    val revision: Long = 0L
)

/**
 * Transport-agnostic central registry of [ToolProvider]s and their [Tool]s.
 */
open class ToolboxRegistry {

    fun interface Listener {
        fun onToolboxChanged()
    }

    private val lock = RpcLock()
    private val providersMap = linkedMapOf<String, ToolProvider>()
    private val listenersLock = RpcLock()
    private val listeners = mutableListOf<Listener>()
    private var revisionCounter = 0L

    private val _state = MutableStateFlow(ToolboxSnapshot())
    val state: StateFlow<ToolboxSnapshot> = _state.asStateFlow()

    fun register(provider: ToolProvider) {
        withRpcLock(lock) {
            providersMap[provider.name] = provider
            revisionCounter += 1L
            _state.value = buildSnapshotLocked()
        }
        notifyChanged()
    }

    fun unregister(name: String) {
        val removed = withRpcLock(lock) {
            val prev = providersMap.remove(name)
            if (prev != null) {
                revisionCounter += 1L
                _state.value = buildSnapshotLocked()
                true
            } else {
                false
            }
        }
        if (removed) notifyChanged()
    }

    fun unregister(provider: ToolProvider) {
        unregister(provider.name)
    }

    fun clear() {
        val hadAny = withRpcLock(lock) {
            if (providersMap.isNotEmpty()) {
                providersMap.clear()
                revisionCounter += 1L
                _state.value = buildSnapshotLocked()
                true
            } else {
                false
            }
        }
        if (hadAny) notifyChanged()
    }

    fun providers(): List<ToolProvider> = withRpcLock(lock) {
        providersMap.values.toList()
    }

    fun findProvider(name: String): ToolProvider? = withRpcLock(lock) {
        providersMap[name]
    }

    fun tools(providerFilter: String? = null): List<Tool> {
        val list = withRpcLock(lock) {
            if (providerFilter.isNullOrBlank()) {
                providersMap.values.toList()
            } else {
                listOfNotNull(providersMap[providerFilter])
            }
        }
        return list.flatMap { it.tools() }
    }

    fun findTool(name: String, providerFilter: String? = null): Tool? {
        val candidateTools = tools(providerFilter)
        candidateTools.firstOrNull { it.name == name }?.let { return it }
        if (!providerFilter.isNullOrBlank() && !name.contains('.')) {
            val qualified = "$providerFilter.$name"
            return candidateTools.firstOrNull { it.name == qualified }
        }
        return null
    }

    /**
     * Never throws: any tool or validation exception is caught and wrapped in [ToolResult.error].
     */
    suspend fun invoke(
        name: String,
        arguments: ToolArguments = ToolArguments.EMPTY,
        providerFilter: String? = null
    ): ToolResult {
        val tool = findTool(name, providerFilter)
            ?: return ToolResult.error("Unknown tool '$name'")
        return try {
            tool.invoke(arguments)
        } catch (e: Exception) {
            ToolResult.error("Tool '$name' failed: ${e.message ?: e::class.simpleName}")
        }
    }

    fun addListener(listener: Listener) {
        withRpcLock(listenersLock) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: Listener) {
        withRpcLock(listenersLock) {
            listeners.remove(listener)
        }
    }

    fun toDiscoveryJson(mcpEnabled: Boolean, baseUrl: String = ""): JsonObject {
        val snap = _state.value
        val cleanBase = baseUrl.trimEnd('/')
        return buildJsonObject {
            put("mcpEnabled", mcpEnabled)
            put("osMcpEndpoint", if (cleanBase.isNotEmpty()) "$cleanBase/api/mcp" else "/api/mcp")
            put("totalTools", snap.totalTools)
            put("revision", snap.revision)
            put("providers", buildJsonArray {
                snap.providers.forEach { p ->
                    add(
                        buildJsonObject {
                            put("name", p.name)
                            put("description", p.description)
                            put("kind", p.kind.wireName)
                            put(
                                "mcpEndpoint",
                                if (cleanBase.isNotEmpty()) "$cleanBase${p.mcpEndpoint}" else p.mcpEndpoint
                            )
                            put("toolCount", p.tools.size)
                            put("tools", buildJsonArray {
                                p.tools.forEach { t ->
                                    add(
                                        buildJsonObject {
                                            put("name", t.name)
                                            put("description", t.description)
                                            put("parameterCount", t.parameterCount)
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

    private fun buildSnapshotLocked(): ToolboxSnapshot {
        val providerSummaries = providersMap.values.map { provider ->
            val toolList = provider.tools().map { t ->
                ToolSummary(
                    name = t.name,
                    description = t.description,
                    parameterCount = t.parameters.size
                )
            }
            ProviderSummary(
                name = provider.name,
                description = provider.description,
                kind = provider.kind,
                mcpEndpoint = "/api/mcp/${provider.name}",
                tools = toolList
            )
        }
        return ToolboxSnapshot(
            providers = providerSummaries,
            totalTools = providerSummaries.sumOf { it.tools.size },
            revision = revisionCounter
        )
    }

    private fun notifyChanged() {
        val snapshot = withRpcLock(listenersLock) { listeners.toList() }
        snapshot.forEach { listener ->
            try {
                listener.onToolboxChanged()
            } catch (_: Exception) {
            }
        }
    }
}

object Toolbox : ToolboxRegistry()
