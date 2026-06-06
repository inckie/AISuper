package com.damn.aisuper.storage

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import ru.astrainteractive.klibs.kstorage.suspend.SuspendMutableKrate
import ru.astrainteractive.klibs.kstorage.suspend.impl.DefaultSuspendMutableKrate

/**
 * Persistent storage backend backed by a platform-specific key-value store.
 *
 * Namespacing is handled by [ScopedStateStorage]; this backend stores scoped keys as-is.
 * Each key is wrapped in a kstorage Krate to keep operations thread-safe.
 */
class PersistentStateStorage : StateStorage {
    private val store = PlatformPersistentStringStore(namespace = "aisuper.persistent.state")
    private val krates = mutableMapOf<String, SuspendMutableKrate<String?>>()

    private fun krateFor(scopedKey: String): SuspendMutableKrate<String?> {
        return krates.getOrPut(scopedKey) {
            DefaultSuspendMutableKrate(
                factory = { null },
                loader = { store.get(scopedKey) },
                saver = { value ->
                    if (value == null) store.remove(scopedKey) else store.put(scopedKey, value)
                }
            )
        }
    }

    override suspend fun putString(scope: StorageScope, key: String, value: String) {
        val scopedKey = makeScopedKey(scope, key)
        krateFor(scopedKey).save(value)
    }

    override suspend fun getString(scope: StorageScope, key: String): String? {
        val scopedKey = makeScopedKey(scope, key)
        return krateFor(scopedKey).getValue()
    }

    override suspend fun putInt(scope: StorageScope, key: String, value: Int) =
        putString(scope, key, value.toString())

    override suspend fun getInt(scope: StorageScope, key: String): Int? =
        getString(scope, key)?.toIntOrNull()

    override suspend fun putLong(scope: StorageScope, key: String, value: Long) =
        putString(scope, key, value.toString())

    override suspend fun getLong(scope: StorageScope, key: String): Long? =
        getString(scope, key)?.toLongOrNull()

    override suspend fun putDouble(scope: StorageScope, key: String, value: Double) =
        putString(scope, key, value.toString())

    override suspend fun getDouble(scope: StorageScope, key: String): Double? =
        getString(scope, key)?.toDoubleOrNull()

    override suspend fun putBoolean(scope: StorageScope, key: String, value: Boolean) =
        putString(scope, key, value.toString())

    override suspend fun getBoolean(scope: StorageScope, key: String): Boolean? =
        when (getString(scope, key)?.lowercase()) {
            "true" -> true
            "false" -> false
            else -> null
        }

    override suspend fun putObject(scope: StorageScope, key: String, value: JsonElement) =
        putString(scope, key, value.toString())

    override suspend fun getObject(scope: StorageScope, key: String): JsonElement? {
        val raw = getString(scope, key) ?: return null
        return try { Json.parseToJsonElement(raw) } catch (_: Exception) { null }
    }

    override suspend fun delete(scope: StorageScope, key: String) {
        val scopedKey = makeScopedKey(scope, key)
        krateFor(scopedKey).save(null)
        krates.remove(scopedKey)
    }

    override suspend fun clearScope(scope: StorageScope) {
        val prefix = "${scope.key}:"
        val scopedKeys = store.keys().filter { it.startsWith(prefix) }
        scopedKeys.forEach { scopedKey ->
            krateFor(scopedKey).save(null)
            krates.remove(scopedKey)
        }
    }

    override suspend fun contains(scope: StorageScope, key: String): Boolean =
        getString(scope, key) != null

    override suspend fun keys(scope: StorageScope): List<String> {
        val prefix = "${scope.key}:"
        return store.keys()
            .filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix) }
    }
}
