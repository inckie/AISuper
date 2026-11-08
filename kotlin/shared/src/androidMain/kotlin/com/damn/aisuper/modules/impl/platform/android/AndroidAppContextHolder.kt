package com.damn.aisuper.modules.impl.platform.android

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CompletableDeferred

object AndroidAppContextHolder {
    var appContext: Context? = null
    var currentActivity: ComponentActivity? = null

    suspend fun requestPermissions(permissions: Array<String>): Boolean {
        val activity = currentActivity ?: return false
        val deferred = CompletableDeferred<Boolean>()

        val registry = activity.activityResultRegistry
        val key = "permission_request_${System.currentTimeMillis()}"

        var launcherRef: ActivityResultLauncher<Array<String>>? = null
        launcherRef = registry.register(key, ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val allGranted = results.values.all { it }
            deferred.complete(allGranted)
            launcherRef?.unregister()
        }

        try {
            launcherRef.launch(permissions)
        } catch (e: Exception) {
            launcherRef.unregister()
            deferred.complete(false)
        }

        return deferred.await()
    }
}


