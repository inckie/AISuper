package com.damn.aisuper.widget

import android.content.Context
import androidx.annotation.Keep
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.damn.aisuper.modules.impl.platform.android.AndroidAppContextHolder
import kotlinx.serialization.json.Json

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
@Keep
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

        val applet = WidgetAppletManager.getOrCreateApplet(glanceId, featureId)
        applet.handleAction(actionName, actionArgs)
        
        // We don't need to manually serialize and save JSON snapshots anymore.
        // The live applet's state flows are observed by provideGlance,
        // so any state changes will automatically trigger a widget recomposition.
    }
}

