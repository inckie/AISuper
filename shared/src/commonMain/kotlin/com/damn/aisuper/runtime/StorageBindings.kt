package com.damn.aisuper.runtime

import com.damn.aisuper.engine.AppJSEngine
import com.damn.aisuper.storage.StateStorage
import com.damn.aisuper.storage.StorageScope
import com.damn.aisuper.util.Logger
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private data class ScopeKeyArgs(
    val scope: StorageScope,
    val key: String
)

/**
 * Register JS storage bridge functions on the engine.
 * Supported JS scopes: "applet" and "feature".
 */
suspend fun AppJSEngine.registerStorageBindings(
    featureTransient: StateStorage,
    featurePersistent: StateStorage
) {
    registerSuspendFunction("storageGet") { args ->
        val request = args.parseScopeAndKey() ?: return@registerSuspendFunction JsonNull
        featureTransient.getString(request.scope, request.key)?.let { JsonPrimitive(it) } ?: JsonNull
    }

    registerSuspendFunction("storagePut") { args ->
        val request = args.parseScopeAndKey() ?: return@registerSuspendFunction JsonNull
        val value = args.stringArg(2) ?: return@registerSuspendFunction JsonNull
        featureTransient.putString(request.scope, request.key, value)
        JsonPrimitive(value)
    }

    registerSuspendFunction("storageDelete") { args ->
        val request = args.parseScopeAndKey() ?: return@registerSuspendFunction JsonNull
        featureTransient.delete(request.scope, request.key)
        JsonNull
    }

    registerSuspendFunction("persistentStorageGet") { args ->
        val request = args.parseScopeAndKey() ?: return@registerSuspendFunction JsonNull
        featurePersistent.getString(request.scope, request.key)?.let { JsonPrimitive(it) } ?: JsonNull
    }

    registerSuspendFunction("persistentStoragePut") { args ->
        val request = args.parseScopeAndKey() ?: return@registerSuspendFunction JsonNull
        val value = args.stringArg(2) ?: return@registerSuspendFunction JsonNull
        featurePersistent.putString(request.scope, request.key, value)
        JsonPrimitive(value)
    }

    registerSuspendFunction("persistentStorageDelete") { args ->
        val request = args.parseScopeAndKey() ?: return@registerSuspendFunction JsonNull
        featurePersistent.delete(request.scope, request.key)
        JsonNull
    }

    registerSuspendFunction("storageGetObject") { args ->
        val request = args.parseScopeAndKey() ?: return@registerSuspendFunction JsonNull
        featureTransient.getObject(request.scope, request.key) ?: JsonNull
    }

    registerSuspendFunction("storagePutObject") { args ->
        val request = args.parseScopeAndKey() ?: return@registerSuspendFunction JsonNull
        val value = args.getOrNull(2) ?: return@registerSuspendFunction JsonNull
        featureTransient.putObject(request.scope, request.key, value)
        value
    }

    registerSuspendFunction("persistentStorageGetObject") { args ->
        val request = args.parseScopeAndKey() ?: return@registerSuspendFunction JsonNull
        featurePersistent.getObject(request.scope, request.key) ?: JsonNull
    }

    registerSuspendFunction("persistentStoragePutObject") { args ->
        val request = args.parseScopeAndKey() ?: return@registerSuspendFunction JsonNull
        val value = args.getOrNull(2) ?: return@registerSuspendFunction JsonNull
        featurePersistent.putObject(request.scope, request.key, value)
        value
    }
}

private fun List<JsonElement>.parseScopeAndKey(): ScopeKeyArgs? {
    val scopeValue = stringArg(0) ?: return null
    val key = stringArg(1) ?: return null
    val scope = parseJsScope(scopeValue) ?: return null
    return ScopeKeyArgs(scope = scope, key = key)
}

private fun List<JsonElement>.stringArg(index: Int): String? {
    val element = getOrNull(index) ?: return null
    return try {
        element.jsonPrimitive.contentOrNull
    } catch (_: Exception) {
        null
    }
}

private fun parseJsScope(scope: String): StorageScope? = when (scope.lowercase()) {
    "applet" -> StorageScope.Applet
    "feature" -> StorageScope.Feature
    else -> {
        Logger.w("Storage") { "Unknown or unsupported JS scope: '$scope'" }
        null
    }
}
