package com.damn.aisuper.storage

import platform.Foundation.NSUserDefaults

internal actual class PlatformPersistentStringStore actual constructor(
    private val namespace: String
) {
    private val defaults = NSUserDefaults.standardUserDefaults

    private fun fullKey(key: String): String = "$namespace:$key"

    actual suspend fun get(key: String): String? = defaults.stringForKey(fullKey(key))

    actual suspend fun put(key: String, value: String) {
        defaults.setObject(value, forKey = fullKey(key))
    }

    actual suspend fun remove(key: String) {
        defaults.removeObjectForKey(fullKey(key))
    }

    actual suspend fun keys(): List<String> {
        val prefix = "$namespace:"
        return defaults.dictionaryRepresentation().keys
            .mapNotNull { it as? String }
            .filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix) }
    }
}

