package com.damn.aisuper.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.appwidget.AppWidgetId
import com.damn.aisuper.applet.AndroidAppletScanner
import com.damn.aisuper.applet.decodeAndroidAppletRef
import com.damn.aisuper.engine.createAppJSEngine
import com.damn.aisuper.runtime.Applet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object WidgetAppletManager {
    private const val TAG = "WidgetAppletManager"

    private data class WidgetRuntime(
        val appletRef: String,
        val entryPath: String,
        val applet: Applet
    )

    private val activeApplets = mutableMapOf<Int, WidgetRuntime>()
    private val mutex = Mutex()

    suspend fun getOrCreateApplet(
        context: Context,
        glanceId: GlanceId,
        appletRefJson: String,
        featureId: String
    ): Applet? {
        val appWidgetId = (glanceId as? AppWidgetId)?.appWidgetId
            ?: throw IllegalArgumentException("Invalid GlanceId: $glanceId")

        val resolved = withContext(Dispatchers.IO) {
            runCatching {
                val ref = decodeAndroidAppletRef(appletRefJson)
                AndroidAppletScanner.resolveRef(context.applicationContext, ref)
            }.onFailure {
                Log.e(TAG, "Failed to resolve applet ref for widget $appWidgetId: ${it.message}")
            }.getOrNull()
        } ?: return null

        val runtime = mutex.withLock {
            val existing = activeApplets[appWidgetId]
            if (existing != null && existing.appletRef == appletRefJson) {
                existing
            } else {
                existing?.applet?.close()
                Log.d(TAG, "Creating new Applet for widget $appWidgetId (${resolved.entry.name} / $featureId)")
                val newApplet = Applet(
                    engineFactory = { createAppJSEngine("widget-$appWidgetId") },
                    resourceLoader = resolved.provider.createLoader()
                )
                WidgetRuntime(appletRefJson, resolved.entry.entryPath, newApplet).also {
                    activeApplets[appWidgetId] = it
                }
            }
        }

        withContext(Dispatchers.IO) {
            if (runtime.applet.currentFeature.value?.id != featureId) {
                try {
                    Log.d(TAG, "Loading feature $featureId into Applet for widget $appWidgetId")
                    runtime.applet.loadApplet(runtime.entryPath, featureId)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load applet for widget $appWidgetId: ${e.message}")
                }
            }
        }

        return runtime.applet
    }

    suspend fun removeApplet(glanceId: GlanceId) {
        val appWidgetId = (glanceId as? AppWidgetId)?.appWidgetId ?: return
        Log.d(TAG, "Removing Applet for widget $appWidgetId")
        mutex.withLock {
            activeApplets.remove(appWidgetId)?.applet?.close()
        }
    }

    suspend fun clearAll() {
        mutex.withLock {
            activeApplets.values.forEach { it.applet.close() }
            activeApplets.clear()
        }
    }
}
