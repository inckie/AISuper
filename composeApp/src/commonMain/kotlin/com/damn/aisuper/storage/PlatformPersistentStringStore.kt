package com.damn.aisuper.storage

/**
 * Minimal platform-specific persistent string key-value store.
 */
internal expect class PlatformPersistentStringStore(namespace: String) {
    suspend fun get(key: String): String?
    suspend fun put(key: String, value: String)
    suspend fun remove(key: String)
    suspend fun keys(): List<String>
}

