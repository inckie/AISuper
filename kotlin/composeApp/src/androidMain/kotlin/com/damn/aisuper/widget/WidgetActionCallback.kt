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
        val appletRef = prefs[PREF_APPLET_REF] ?: return
        val featureId = prefs[PREF_FEATURE_ID] ?: return

        val actionName = parameters[KEY_ACTION] ?: return
        val argsJson   = parameters[KEY_ARGS]   ?: "[]"
        val actionArgs = try {
            val arr = Json.parseToJsonElement(argsJson)
            if (arr is kotlinx.serialization.json.JsonArray) arr.toList()
            else emptyList()
        } catch (_: Exception) { emptyList() }

        val applet = WidgetAppletManager.getOrCreateApplet(context, glanceId, appletRef, featureId) ?: return
        applet.handleAction(actionName, actionArgs)
    }
}
