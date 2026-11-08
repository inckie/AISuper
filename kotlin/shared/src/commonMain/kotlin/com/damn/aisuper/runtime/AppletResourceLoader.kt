package com.damn.aisuper.runtime

/**
 * Abstraction used by runtime/module code to load applet artifacts.
 * Compose UI can back this with resources; server mode can back this with file IO.
 */
fun interface AppletResourceLoader {
    suspend fun readBytes(path: String): ByteArray
}

