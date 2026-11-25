package com.damn.aisuper.rpc

internal expect fun currentTimeMillis(): Long

internal expect class RpcLock()

internal expect inline fun <T> withRpcLock(lock: RpcLock, block: () -> T): T
