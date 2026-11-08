package com.damn.aisuper.util

@Suppress("UNUSED_PARAMETER")
@JsFun("() => Date.now()")
private external fun dateNow(): Double

internal actual fun currentTimeMillis(): Long = dateNow().toLong()
