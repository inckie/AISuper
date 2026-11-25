package com.damn.aisuper.rpc

@OptIn(kotlin.time.ExperimentalTime::class)
internal actual fun currentTimeMillis(): Long =
    kotlin.time.Clock.System.now().toEpochMilliseconds()

internal actual class RpcLock actual constructor()

internal actual inline fun <T> withRpcLock(lock: RpcLock, block: () -> T): T = block()
