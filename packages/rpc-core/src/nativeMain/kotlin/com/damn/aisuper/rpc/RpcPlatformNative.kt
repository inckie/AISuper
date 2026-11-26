package com.damn.aisuper.rpc

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(kotlin.time.ExperimentalTime::class)
internal actual fun currentTimeMillis(): Long =
    kotlin.time.Clock.System.now().toEpochMilliseconds()

@OptIn(ExperimentalAtomicApi::class)
internal actual class RpcLock actual constructor() {
    @PublishedApi
    internal val locked = AtomicInt(0)
}

@OptIn(ExperimentalAtomicApi::class)
internal actual inline fun <T> withRpcLock(lock: RpcLock, block: () -> T): T {
    while (!lock.locked.compareAndSet(0, 1)) {
        // Spin briefly until lock is released
    }
    try {
        return block()
    } finally {
        lock.locked.store(0)
    }
}
