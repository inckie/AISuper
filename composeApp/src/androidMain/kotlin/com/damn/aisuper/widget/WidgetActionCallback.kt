package com.damn.aisuper.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.damn.aisuper.engine.KeightJSEngine
import com.damn.aisuper.layout.LayoutRoot
import com.damn.aisuper.modules.impl.platform.android.AndroidAppContextHolder
import com.damn.aisuper.runtime.Applet
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import androidx.glance.appwidget.state.updateAppWidgetState

/** ActionParameters key carrying the JS action name to invoke. */
val KEY_ACTION = ActionParameters.Key<String>("widget_action")
/** ActionParameters key carrying serialised JSON array of action args. */
val KEY_ARGS    = ActionParameters.Key<String>("widget_args")

/**
 * Generic Glance ActionCallback that:
 * 1. Reads the persisted featureId from widget state.
 * 2. Boots the Applet + feature.
 * 3. Calls the JS action named in [KEY_ACTION] with args from [KEY_ARGS].
 * 4. Waits for async work to settle.
 * 5. Snapshots the updated layout + values and pushes them back to the widget.
 */
class WidgetActionCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        if (AndroidAppContextHolder.appContext == null) {
            AndroidAppContextHolder.appContext = context.applicationContext
        }

        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        val featureId = prefs[PREF_FEATURE_ID] ?: return

        val actionName = parameters[KEY_ACTION] ?: return
        val argsJson   = parameters[KEY_ARGS]   ?: "[]"
        val actionArgs = try {
            val arr = Json.parseToJsonElement(argsJson)
            if (arr is kotlinx.serialization.json.JsonArray) arr.toList()
            else emptyList()
        } catch (_: Exception) { emptyList() }

        val applet = Applet { KeightJSEngine() }
        applet.loadApplet("files/applet.json")
        applet.launchFeature(featureId)

        // Let initialize() run
        delay(2000)

        // Invoke the specific action (e.g. "refresh", "loadWeather")
        applet.handleAction(actionName, actionArgs)

        // Wait for async work triggered by the action
        delay(6000)

        val feature    = applet.currentFeature.value
        val layoutRoot = feature?.layoutRoot?.value
        val valuesMap  = feature?.values?.value ?: emptyMap()

        val layoutJson = if (layoutRoot != null) {
            try { Json.encodeToString(LayoutRoot.serializer(), layoutRoot) } catch (_: Exception) { "" }
        } else ""

        val valuesJson = try {
            Json.encodeToString(JsonObject.serializer(), JsonObject(valuesMap))
        } catch (_: Exception) { "{}" }

        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { p ->
            p.toMutablePreferences().apply {
                this[PREF_LAYOUT_JSON] = layoutJson
                this[PREF_VALUES_JSON] = valuesJson
            }
        }

        AISuperWidget().update(context, glanceId)
        applet.close()
    }
}

