package com.damn.aisuper.rpc

import com.damn.aisuper.toolbox.SimpleTool
import com.damn.aisuper.toolbox.Tool
import com.damn.aisuper.toolbox.ToolProvider
import com.damn.aisuper.toolbox.ToolProviderKind

/**
 * Uniform self-documenting capability surface implemented by native Android modules,
 * portable JS pods (QuickJS / Web Workers), and remote pods mounted over HTTP/WebSocket.
 */
interface RpcSurface {
    val name: String
    fun descriptor(): SurfaceDescriptor
    suspend fun invoke(
        method: String,
        params: RpcParams = RpcParams.Empty,
        context: RpcCallContext = RpcCallContext.LOCAL
    ): RpcResult
}

/**
 * Projects any [RpcSurface] into the legacy [ToolProvider] interface so existing UI
 * or callers reading [org.fluentos.foundation.toolbox.Toolbox] stay seamlessly compatible.
 */
class RpcSurfaceToolProviderAdapter(
    private val surface: RpcSurface
) : ToolProvider {
    override val name: String get() = surface.name
    override val description: String get() = surface.descriptor().description
    override val kind: ToolProviderKind get() = surface.descriptor().kind.toToolProviderKind()

    override fun tools(): List<Tool> {
        val desc = surface.descriptor()
        return desc.methods
            .filter { it.exposeInMcp }
            .map { methodDesc ->
                SimpleTool(
                    name = methodDesc.qualifiedName,
                    description = methodDesc.description,
                    parameters = methodDesc.parameters.map { it.toToolParameter() }
                ) { args ->
                    surface.invoke(
                        method = methodDesc.name,
                        params = RpcParams.Named(args.asJsonObject()),
                        context = RpcCallContext.MCP
                    ).toToolResult()
                }
            }
    }
}
