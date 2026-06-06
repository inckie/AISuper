package com.damn.aisuper.storage

import kotlinx.serialization.json.JsonElement

/**
 * Scope for state storage access. Controls both visibility and key namespacing.
 *
 * Key format stored in backends:
 *   Applet       → "applet:{appletId}/{key}"
 *   Feature      → "feature:{appletId}/{featureId}/{key}"
 *   Module       → "module:{appletId}/{featureId}/{moduleName}/{key}"
 *   ModuleGlobal → "module.global:{appletId}/{moduleName}/{key}"
 */
enum class StorageScope(val key: String) {
    /** Applet-wide: readable/writable by all features and modules in the applet. */
    Applet("applet"),

    /** Feature-scoped: readable/writable by all modules within the same feature. */
    Feature("feature"),

    /**
     * Module-private, feature-isolated: storage is unique per module+feature pair.
     * A "weather" module in "weatherLocal" and the same module type in another feature
     * have completely separate data here.
     */
    Module("module"),

    /**
     * Module-private, applet-wide: storage is unique per module type regardless of which
     * feature it runs in.  Useful for module-level preferences or caches that should
     * survive the user switching between features.
     * JS scope string: "module.global"
     */
    ModuleGlobal("module.global")
}

/**
 * Identifies the runtime context for storage namespacing.
 * All three IDs are used to construct collision-free storage keys.
 */
data class StorageContext(
    val appletId: String,
    val featureId: String,
    /** Null when the context belongs to a feature script rather than a native module. */
    val moduleName: String? = null
)

/**
 * Base interface for state storage with type-based API.
 * All functions return null if the value is missing (no error on missing keys).
 */
interface StateStorage {
    suspend fun putString(scope: StorageScope, key: String, value: String)
    suspend fun getString(scope: StorageScope, key: String): String?

    suspend fun putInt(scope: StorageScope, key: String, value: Int)
    suspend fun getInt(scope: StorageScope, key: String): Int?

    suspend fun putLong(scope: StorageScope, key: String, value: Long)
    suspend fun getLong(scope: StorageScope, key: String): Long?

    suspend fun putDouble(scope: StorageScope, key: String, value: Double)
    suspend fun getDouble(scope: StorageScope, key: String): Double?

    suspend fun putBoolean(scope: StorageScope, key: String, value: Boolean)
    suspend fun getBoolean(scope: StorageScope, key: String): Boolean?

    suspend fun putObject(scope: StorageScope, key: String, value: JsonElement)
    suspend fun getObject(scope: StorageScope, key: String): JsonElement?

    suspend fun delete(scope: StorageScope, key: String)
    suspend fun clearScope(scope: StorageScope)
    suspend fun contains(scope: StorageScope, key: String): Boolean
    suspend fun keys(scope: StorageScope): List<String>
}

/**
 * Construct a flat backend key from scope and logical key.
 * The [key] passed here is already namespaced by [ScopedStateStorage].
 */
internal fun makeScopedKey(scope: StorageScope, key: String): String = "${scope.key}:$key"
