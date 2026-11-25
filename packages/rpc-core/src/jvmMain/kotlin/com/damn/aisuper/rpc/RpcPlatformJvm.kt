package com.damn.aisuper.rpc

internal actual fun currentTimeMillis(): Long = System.currentTimeMillis()

internal actual class RpcLock actual constructor()

internal actual inline fun <T> withRpcLock(lock: RpcLock, block: () -> T): T =
    synchronized(lock, block)
