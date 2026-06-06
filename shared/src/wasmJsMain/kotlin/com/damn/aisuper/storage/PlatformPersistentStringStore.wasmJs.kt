package com.damn.aisuper.storage

import kotlinx.browser.window

internal actual class PlatformPersistentStringStore actual constructor(
    private val namespace: String
) {
    private val storage get() = window.localStorage

    private fun fullKey(key: String): String = "$namespace:$key"

    actual suspend fun get(key: String): String? = storage.getItem(fullKey(key))

    actual suspend fun put(key: String, value: String) {
        storage.setItem(fullKey(key), value)
    }

    actual suspend fun remove(key: String) {
        storage.removeItem(fullKey(key))
    }

    actual suspend fun keys(): List<String> {
        val prefix = "$namespace:"
        val result = mutableListOf<String>()
        val total = storage.length
        for (index in 0 until total) {
            val key = storage.key(index) ?: continue
            if (key.startsWith(prefix)) {
                result += key.removePrefix(prefix)
            }
        }
        return result
    }
}

