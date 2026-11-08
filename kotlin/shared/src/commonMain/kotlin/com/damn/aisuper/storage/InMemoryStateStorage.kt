package com.damn.aisuper.storage

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.Json

/**
 * In-memory implementation of StateStorage.
 * Acts as a flat key-value backend; all namespacing is handled by [ScopedStateStorage].
 */
class InMemoryStateStorage : StateStorage {
    private val data = mutableMapOf<String, String>()

    override suspend fun putString(scope: StorageScope, key: String, value: String) {
        data[makeScopedKey(scope, key)] = value
    }

    override suspend fun getString(scope: StorageScope, key: String): String? =
        data[makeScopedKey(scope, key)]

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
        data.remove(makeScopedKey(scope, key))
    }

    /**
     * Clears ALL keys under this scope prefix (across all namespaces).
     * Typically called only via [ScopedStateStorage] which first filters to the correct namespace.
     */
    override suspend fun clearScope(scope: StorageScope) {
        val prefix = "${scope.key}:"
        data.keys.filter { it.startsWith(prefix) }.forEach { data.remove(it) }
    }

    override suspend fun contains(scope: StorageScope, key: String): Boolean =
        makeScopedKey(scope, key) in data

    /**
     * Returns all keys under this scope, with the scope prefix stripped.
     * Keys still contain the namespace segment (e.g. "appletId/featureId/myKey").
     */
    override suspend fun keys(scope: StorageScope): List<String> {
        val prefix = "${scope.key}:"
        return data.keys.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }
    }
}
