package com.damn.aisuper.widget

import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.appwidget.AppWidgetId
import com.damn.aisuper.applet.ComposeAppletProvider
import com.damn.aisuper.engine.createAppJSEngine
import com.damn.aisuper.runtime.Applet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object WidgetAppletManager {
    private const val TAG = "WidgetAppletManager"
    private val activeApplets = mutableMapOf<Int, Applet>()
    private val mutex = Mutex()

    suspend fun getOrCreateApplet(glanceId: GlanceId, featureId: String): Applet {
        val appWidgetId = (glanceId as? AppWidgetId)?.appWidgetId
            ?: throw IllegalArgumentException("Invalid GlanceId: $glanceId")

        val applet = mutex.withLock {
            activeApplets[appWidgetId] ?: run {
                Log.d(TAG, "Creating new Applet for widget $appWidgetId with feature $featureId")
                val newApplet = Applet(
                    engineFactory = { createAppJSEngine("widget-$appWidgetId") },
                    resourceLoader = ComposeAppletProvider().createLoader()
                )
                activeApplets[appWidgetId] = newApplet
                newApplet
            }
        }

        withContext(Dispatchers.IO) {
            if (applet.currentFeature.value?.id != featureId) {
                try {
                    Log.d(TAG, "Loading feature $featureId into Applet for widget $appWidgetId")
                    applet.loadApplet("files/applet.json", featureId)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load applet for widget $appWidgetId: ${e.message}")
                }
            }
        }

        return applet
    }

    suspend fun removeApplet(glanceId: GlanceId) {
        val appWidgetId = (glanceId as? AppWidgetId)?.appWidgetId ?: return
        Log.d(TAG, "Removing Applet for widget $appWidgetId")
        mutex.withLock {
            activeApplets.remove(appWidgetId)?.close()
        }
    }

    suspend fun clearAll() {
        mutex.withLock {
            activeApplets.values.forEach { it.close() }
            activeApplets.clear()
        }
    }
}
