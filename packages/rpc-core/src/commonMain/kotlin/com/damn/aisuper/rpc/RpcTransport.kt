package com.damn.aisuper.rpc

/**
 * Pluggable transport abstraction for calling any local or remote JSON-RPC surface.
 */
interface RpcTransport {
    val id: String
    val endpointDescription: String
    suspend fun call(
        method: String,
        params: RpcParams = RpcParams.Empty,
        context: RpcCallContext = RpcCallContext.LOCAL
    ): RpcResult
    suspend fun close() {}
}

/**
 * Zero-serialization in-process transport calling [RpcRouter] directly within the same process.
 */
class LocalRpcTransport(
    private val router: RpcRouter,
    private val surfaceScope: String? = null
) : RpcTransport {
    override val id: String = "local"
    override val endpointDescription: String =
        if (surfaceScope.isNullOrBlank()) "local://in-process" else "local://$surfaceScope"

    override suspend fun call(
        method: String,
        params: RpcParams,
        context: RpcCallContext
    ): RpcResult = router.call(
        method = method,
        params = params,
        context = context,
        surfaceScope = surfaceScope
    )
}
