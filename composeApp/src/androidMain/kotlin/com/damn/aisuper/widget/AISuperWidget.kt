package com.damn.aisuper.widget

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.layout.*
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.damn.aisuper.engine.KeightJSEngine
import com.damn.aisuper.layout.LayoutRoot
import com.damn.aisuper.layout.frontend.glance.RenderWidget
import com.damn.aisuper.layout.parseLayout
import com.damn.aisuper.modules.impl.platform.android.AndroidAppContextHolder
import com.damn.aisuper.runtime.Applet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Preference key: which featureId this widget instance is bound to. */
val PREF_FEATURE_ID = stringPreferencesKey("feature_id")
/** Serialised key→value snapshot from the feature's value state. */
val PREF_VALUES_JSON = stringPreferencesKey("values_json")
/** Layout JSON snapshot for this feature. */
val PREF_LAYOUT_JSON = stringPreferencesKey("layout_json")

@OptIn(ExperimentalGlanceApi::class)
class AISuperWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun providePreview(context: Context, widgetCategory: Int) = provideContent {
        WidgetPreviewContent()
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs      = currentState<androidx.datastore.preferences.core.Preferences>()
            val featureId  = prefs[PREF_FEATURE_ID]  ?: ""
            val layoutJson = prefs[PREF_LAYOUT_JSON]  ?: ""
            val valuesJson = prefs[PREF_VALUES_JSON]  ?: "{}"

            if (featureId.isEmpty() || layoutJson.isEmpty()) {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(ColorProvider(Color(0xFF1565C0)))
                        .padding(12.dp),
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally
                ) {
                    Text(
                        "Tap + hold to configure",
                        style = TextStyle(color = ColorProvider(Color.White), fontSize = 13.sp)
                    )
                }
                return@provideContent
            }

            val values: Map<String, JsonElement> = try {
                (Json.parseToJsonElement(valuesJson) as? JsonObject)?.toMap() ?: emptyMap()
            } catch (_: Exception) { emptyMap() }

            val layoutRoot: LayoutRoot? = try { parseLayout(layoutJson) } catch (_: Exception) { null }

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(Color(0xFF1565C0)))
                    .padding(8.dp)
            ) {
                if (layoutRoot != null) {
                    RenderWidget(
                        widget = layoutRoot.layout,
                        values = values,
                        modifier = GlanceModifier.fillMaxSize()
                    )
                } else {
                    Text(
                        "Layout error",
                        style = TextStyle(color = ColorProvider(Color(0xFFFFCC80)), fontSize = 12.sp)
                    )
                }
            }
        }
    }
}

/** Static preview shown in the Android widget picker. */
@androidx.compose.runtime.Composable
private fun WidgetPreviewContent() {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFF1565C0)))
            .padding(12.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally
    ) {
        Text(
            "AISuper Widget",
            style = TextStyle(color = ColorProvider(Color.White), fontSize = 16.sp)
        )
        Text(
            "Tap + hold to configure",
            style = TextStyle(color = ColorProvider(Color(0xFFBBDEFB)), fontSize = 12.sp)
        )
    }
}

class AISuperWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AISuperWidget()
}

/**
 * Run the feature's normal script, wait for async initialisation to settle,
 * then persist a snapshot of the layout JSON + value map into widget prefs.
 */
suspend fun refreshWidgetData(context: Context, glanceId: GlanceId, featureId: String) {
    withContext(Dispatchers.IO) {
        try {
            if (AndroidAppContextHolder.appContext == null) {
                AndroidAppContextHolder.appContext = context.applicationContext
            }

            val applet = Applet { KeightJSEngine() }
            applet.loadApplet("files/applet.json")
            applet.launchFeature(featureId)

            // Allow initialize() + first async fetch to complete
            delay(8000)

            val feature    = applet.currentFeature.value
            val layoutRoot = feature?.layoutRoot?.value
            val valuesMap  = feature?.values?.value ?: emptyMap()

            val layoutJson = if (layoutRoot != null) {
                try { Json.encodeToString(LayoutRoot.serializer(), layoutRoot) } catch (_: Exception) { "" }
            } else ""

            val valuesJson = try {
                Json.encodeToString(JsonObject.serializer(), JsonObject(valuesMap))
            } catch (_: Exception) { "{}" }

            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[PREF_FEATURE_ID]  = featureId
                    this[PREF_LAYOUT_JSON] = layoutJson
                    this[PREF_VALUES_JSON] = valuesJson
                }
            }

            AISuperWidget().update(context, glanceId)
            applet.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
