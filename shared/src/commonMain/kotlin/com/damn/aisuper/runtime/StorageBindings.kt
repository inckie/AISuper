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
 * Supported JS scopes: "applet", "feature", "module", "module.global".
 */
suspend fun AppJSEngine.registerStorageBindings(
    featureTransient: StateStorage,
    featurePersistent: StateStorage
) {
    registerSuspendFunction("storageGet") { args ->
        val request = args.parseScopeAndKey("storageGet")
        featureTransient.getString(request.scope, request.key)?.let { JsonPrimitive(it) } ?: JsonNull
    }

    registerSuspendFunction("storagePut") { args ->
        val request = args.parseScopeAndKey("storagePut")
        val value = args.stringArg(2) ?: throw IllegalArgumentException("storagePut requires a value string as 3rd argument")
        featureTransient.putString(request.scope, request.key, value)
        JsonPrimitive(value)
    }

    registerSuspendFunction("storageDelete") { args ->
        val request = args.parseScopeAndKey("storageDelete")
        featureTransient.delete(request.scope, request.key)
        JsonNull
    }

    registerSuspendFunction("persistentStorageGet") { args ->
        val request = args.parseScopeAndKey("persistentStorageGet")
        featurePersistent.getString(request.scope, request.key)?.let { JsonPrimitive(it) } ?: JsonNull
    }

    registerSuspendFunction("persistentStoragePut") { args ->
        val request = args.parseScopeAndKey("persistentStoragePut")
        val value = args.stringArg(2) ?: throw IllegalArgumentException("persistentStoragePut requires a value string as 3rd argument")
        featurePersistent.putString(request.scope, request.key, value)
        JsonPrimitive(value)
    }

    registerSuspendFunction("persistentStorageDelete") { args ->
        val request = args.parseScopeAndKey("persistentStorageDelete")
        featurePersistent.delete(request.scope, request.key)
        JsonNull
    }

    registerSuspendFunction("storageGetObject") { args ->
        val request = args.parseScopeAndKey("storageGetObject")
        featureTransient.getObject(request.scope, request.key) ?: JsonNull
    }

    registerSuspendFunction("storagePutObject") { args ->
        val request = args.parseScopeAndKey("storagePutObject")
        val value = args.getOrNull(2) ?: throw IllegalArgumentException("storagePutObject requires a value as 3rd argument")
        featureTransient.putObject(request.scope, request.key, value)
        value
    }

    registerSuspendFunction("persistentStorageGetObject") { args ->
        val request = args.parseScopeAndKey("persistentStorageGetObject")
        featurePersistent.getObject(request.scope, request.key) ?: JsonNull
    }

    registerSuspendFunction("persistentStoragePutObject") { args ->
        val request = args.parseScopeAndKey("persistentStoragePutObject")
        val value = args.getOrNull(2) ?: throw IllegalArgumentException("persistentStoragePutObject requires a value as 3rd argument")
        featurePersistent.putObject(request.scope, request.key, value)
        value
    }
}

private fun List<JsonElement>.parseScopeAndKey(functionName: String): ScopeKeyArgs {
    val scopeValue = stringArg(0) ?: throw IllegalArgumentException("Missing or invalid 'scope' argument for $functionName")
    val key = stringArg(1) ?: throw IllegalArgumentException("Missing or invalid 'key' argument for $functionName")
    val scope = parseJsScope(scopeValue) ?: throw IllegalArgumentException("Invalid 'scope' value: '$scopeValue' for $functionName. Valid scopes are: applet, feature, module, module.global")
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
    "module" -> StorageScope.Module
    "module.global" -> StorageScope.ModuleGlobal
    else -> {
        Logger.w("Storage") { "Unknown or unsupported JS scope: '$scope'" }
        null
    }
}
