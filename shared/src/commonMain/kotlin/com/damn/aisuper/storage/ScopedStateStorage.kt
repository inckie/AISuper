package com.damn.aisuper.storage

import kotlinx.serialization.json.JsonElement

/**
 * Wraps a raw storage backend and provides:
 *  1. **Namespace isolation** – all keys are prefixed with appletId, featureId,
 *     and (for module scopes) moduleName so data from different applets/features/modules
 *     never collide.
 *  2. **Access control** – prevents a feature script (no module name) from using
 *     module-private scopes, and prevents reading outside the permitted scope hierarchy.
 *
 * Scope access rules:
 *  - Feature context (moduleName == null, maxScope == Feature):
 *      allowed scopes → Applet, Feature
 *  - Module context (moduleName set, maxScope == ModuleGlobal):
 *      allowed scopes → Applet, Feature, Module, ModuleGlobal
 *
 * Key format in the underlying backend:
 *  Applet       → "applet:{appletId}/{key}"
 *  Feature      → "feature:{appletId}/{featureId}/{key}"
 *  Module       → "module:{appletId}/{featureId}/{moduleName}/{key}"
 *  ModuleGlobal → "module.global:{appletId}/{moduleName}/{key}"
 */
class ScopedStateStorage(
    private val delegate: StateStorage,
    private val context: StorageContext,
    private val maxScope: StorageScope
) : StateStorage {

    // ── access control ───────────────────────────────────────────────────────

    private val allowedScopes: Set<StorageScope> = when (maxScope) {
        StorageScope.Applet -> setOf(StorageScope.Applet)
        StorageScope.Feature -> setOf(StorageScope.Applet, StorageScope.Feature)
        StorageScope.Module, StorageScope.ModuleGlobal ->
            setOf(StorageScope.Applet, StorageScope.Feature,
                StorageScope.Module, StorageScope.ModuleGlobal)
    }

    private fun checkScope(scope: StorageScope) {
        if (scope !in allowedScopes) {
            throw Exception("Cannot access '${scope.key}' scope from '${maxScope.key}' context")
        }
        if ((scope == StorageScope.Module || scope == StorageScope.ModuleGlobal)
            && context.moduleName == null
        ) {
            throw Exception("'${scope.key}' scope requires a module name in StorageContext")
        }
    }

    // ── namespacing ──────────────────────────────────────────────────────────

    /**
     * Namespace prefix that uniquely identifies this context within the given scope.
     * The prefix is prepended to every user-visible key before delegating to the backend.
     */
    private fun namespacePrefix(scope: StorageScope): String = when (scope) {
        StorageScope.Applet ->
            "${context.appletId}/"
        StorageScope.Feature ->
            "${context.appletId}/${context.featureId}/"
        StorageScope.Module ->
            "${context.appletId}/${context.featureId}/${context.moduleName}/"
        StorageScope.ModuleGlobal ->
            "${context.appletId}/${context.moduleName}/"
    }

    private fun nsKey(scope: StorageScope, key: String) = namespacePrefix(scope) + key

    // ── StateStorage implementation ──────────────────────────────────────────

    override suspend fun putString(scope: StorageScope, key: String, value: String) {
        checkScope(scope); delegate.putString(scope, nsKey(scope, key), value)
    }

    override suspend fun getString(scope: StorageScope, key: String): String? {
        checkScope(scope); return delegate.getString(scope, nsKey(scope, key))
    }

    override suspend fun putInt(scope: StorageScope, key: String, value: Int) {
        checkScope(scope); delegate.putInt(scope, nsKey(scope, key), value)
    }

    override suspend fun getInt(scope: StorageScope, key: String): Int? {
        checkScope(scope); return delegate.getInt(scope, nsKey(scope, key))
    }

    override suspend fun putLong(scope: StorageScope, key: String, value: Long) {
        checkScope(scope); delegate.putLong(scope, nsKey(scope, key), value)
    }

    override suspend fun getLong(scope: StorageScope, key: String): Long? {
        checkScope(scope); return delegate.getLong(scope, nsKey(scope, key))
    }

    override suspend fun putDouble(scope: StorageScope, key: String, value: Double) {
        checkScope(scope); delegate.putDouble(scope, nsKey(scope, key), value)
    }

    override suspend fun getDouble(scope: StorageScope, key: String): Double? {
        checkScope(scope); return delegate.getDouble(scope, nsKey(scope, key))
    }

    override suspend fun putBoolean(scope: StorageScope, key: String, value: Boolean) {
        checkScope(scope); delegate.putBoolean(scope, nsKey(scope, key), value)
    }

    override suspend fun getBoolean(scope: StorageScope, key: String): Boolean? {
        checkScope(scope); return delegate.getBoolean(scope, nsKey(scope, key))
    }

    override suspend fun putObject(scope: StorageScope, key: String, value: JsonElement) {
        checkScope(scope); delegate.putObject(scope, nsKey(scope, key), value)
    }

    override suspend fun getObject(scope: StorageScope, key: String): JsonElement? {
        checkScope(scope); return delegate.getObject(scope, nsKey(scope, key))
    }

    override suspend fun delete(scope: StorageScope, key: String) {
        checkScope(scope); delegate.delete(scope, nsKey(scope, key))
    }

    /**
     * Clears only the keys owned by this context's namespace within [scope].
     * Does NOT clear data belonging to other features or modules.
     */
    override suspend fun clearScope(scope: StorageScope) {
        checkScope(scope)
        val prefix = namespacePrefix(scope)
        // delegate.keys() returns keys with the scope bucket prefix already stripped
        delegate.keys(scope)
            .filter { it.startsWith(prefix) }
            .forEach { delegate.delete(scope, it) }
    }

    override suspend fun contains(scope: StorageScope, key: String): Boolean {
        checkScope(scope); return delegate.contains(scope, nsKey(scope, key))
    }

    /**
     * Returns user-visible keys within this context's namespace (namespace prefix stripped).
     */
    override suspend fun keys(scope: StorageScope): List<String> {
        checkScope(scope)
        val prefix = namespacePrefix(scope)
        return delegate.keys(scope)
            .filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix) }
    }
}
