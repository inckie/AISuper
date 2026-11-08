package com.damn.aisuper.storage

import android.content.Context
import com.damn.aisuper.modules.impl.platform.android.AndroidAppContextHolder

internal actual class PlatformPersistentStringStore actual constructor(
    private val namespace: String
) {
    // Fallback for edge-cases where app context is not initialized yet.
    private val fallback = mutableMapOf<String, String>()

    private fun prefs() = AndroidAppContextHolder.appContext
        ?.getSharedPreferences(namespace, Context.MODE_PRIVATE)

    actual suspend fun get(key: String): String? {
        val preferences = prefs()
        return if (preferences != null) preferences.getString(key, null) else fallback[key]
    }

    actual suspend fun put(key: String, value: String) {
        val preferences = prefs()
        if (preferences != null) {
            preferences.edit().putString(key, value).apply()
        } else {
            fallback[key] = value
        }
    }

    actual suspend fun remove(key: String) {
        val preferences = prefs()
        if (preferences != null) {
            preferences.edit().remove(key).apply()
        } else {
            fallback.remove(key)
        }
    }

    actual suspend fun keys(): List<String> {
        val preferences = prefs()
        return if (preferences != null) preferences.all.keys.toList() else fallback.keys.toList()
    }
}

