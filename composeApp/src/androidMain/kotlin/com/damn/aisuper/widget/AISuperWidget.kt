package com.damn.aisuper.widget

import aisuper.composeapp.generated.resources.Res
import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.damn.aisuper.R
import com.damn.aisuper.layout.ImageWidget
import com.damn.aisuper.layout.LayoutRoot
import com.damn.aisuper.layout.StyleSheet
import com.damn.aisuper.layout.Widget
import com.damn.aisuper.layout.frontend.glance.RenderWidget
import com.damn.aisuper.layout.parseColorOrNull
import com.damn.aisuper.modules.impl.platform.android.AndroidAppContextHolder
import com.damn.aisuper.runtime.Applet
import com.damn.aisuper.runtime.AppletManifest
import com.damn.aisuper.runtime.Feature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URL

/** Preference key: which featureId this widget instance is bound to. */
val PREF_FEATURE_ID = stringPreferencesKey("feature_id")
/** Serialised StyleSheet JSON for the selected style. */
val PREF_STYLE_JSON = stringPreferencesKey("style_json")

private val json = Json { ignoreUnknownKeys = true }

private const val TAG = "AISuperWidget"

@OptIn(ExperimentalGlanceApi::class)
class AISuperWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun providePreview(context: Context, widgetCategory: Int) = provideContent {
        WidgetPreviewContent()
    }

    @SuppressLint("RestrictedApi")
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs      = currentState<Preferences>()
            val featureId  = prefs[PREF_FEATURE_ID]  ?: ""
            val styleJson  = prefs[PREF_STYLE_JSON]   ?: ""

            val styleSheet: StyleSheet? = styleJson.takeIf { it.isNotBlank() }?.let {
                try {
                    json.decodeFromString<StyleSheet>(it)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse style JSON: ${e.message}")
                    null
                }
            }

            if (featureId.isEmpty()) {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(ColorProvider(Color(0xFF1565C0)))
                        .padding(12.dp),
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally
                ) {
                    Text(
                        androidx.glance.LocalContext.current.getString(R.string.lbl_tap_hold_to_configure),
                        style = TextStyle(color = ColorProvider(Color.White), fontSize = 13.sp)
                    )
                }
                return@provideContent
            }

            var applet by remember { mutableStateOf<Applet?>(null) }

            LaunchedEffect(featureId) {
                if (featureId.isNotEmpty()) {
                    if (AndroidAppContextHolder.appContext == null) {
                        AndroidAppContextHolder.appContext = context.applicationContext
                    }
                    applet = WidgetAppletManager.getOrCreateApplet(id, featureId)
                }
            }

            val currentFeatureFlow = applet?.currentFeature ?: MutableStateFlow<Feature?>(null)
            val currentFeature by currentFeatureFlow.collectAsState()
            
            val layoutRootFlow = currentFeature?.layoutRoot ?: MutableStateFlow<LayoutRoot?>(null)
            val layoutRoot by layoutRootFlow.collectAsState()
            
            val valuesFlow = currentFeature?.values ?: MutableStateFlow(emptyMap())
            val valuesMap by valuesFlow.collectAsState()

            LaunchedEffect(layoutRoot, valuesMap) {
                if (layoutRoot != null) {
                    withContext(Dispatchers.IO) {
                        cacheImages(layoutRoot, valuesMap.toMutableMap(), context)
                    }
                }
            }

            // Use style's "screen" class background if available, else dark fallback
            val screenBg = parseColorOrNull(
                styleSheet?.classes?.get("screen")?.backgroundColor
            ) ?: Color(0xFF121212.toInt())

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(screenBg))
                    .padding(4.dp)
            ) {
                if (layoutRoot != null) {
                    RenderWidget(
                        widget = layoutRoot!!.layout,
                        values = valuesMap,
                        styleSheet = styleSheet,
                        modifier = GlanceModifier.fillMaxSize()
                    )
                } else {
                    Column(
                        modifier = GlanceModifier.fillMaxSize(),
                        verticalAlignment = Alignment.Vertical.CenterVertically,
                        horizontalAlignment = Alignment.Horizontal.CenterHorizontally
                    ) {
                        Text(
                            "Loading...",
                            style = TextStyle(color = ColorProvider(Color.White), fontSize = 13.sp)
                        )
                    }
                }
            }
        }
    }
}

/** Static preview shown in the Android widget picker. */
@SuppressLint("RestrictedApi")
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
            androidx.glance.LocalContext.current.getString(R.string.lbl_aisuper_widget),
            style = TextStyle(color = ColorProvider(Color.White), fontSize = 16.sp)
        )
        Text(
            androidx.glance.LocalContext.current.getString(R.string.lbl_tap_hold_to_configure),
            style = TextStyle(color = ColorProvider(Color(0xFFBBDEFB)), fontSize = 12.sp)
        )
    }
}

class AISuperWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AISuperWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        // Clean up per-instance DataStore state for each removed widget
        MainScope().launch {
            val manager = GlanceAppWidgetManager(context)
            appWidgetIds.forEach { appWidgetId ->
                try {
                    val glanceId = manager.getGlanceIdBy(appWidgetId)
                    // Clear all prefs for this widget instance
                    updateAppWidgetState(
                        context,
                        PreferencesGlanceStateDefinition,
                        glanceId
                    ) { prefs ->
                        prefs.toMutablePreferences().apply { clear() }
                    }
                    WidgetAppletManager.removeApplet(glanceId)
                    Log.d(TAG, "Cleared state for widget $appWidgetId")
                } catch (e: Exception) {
                    Log.w(
                        TAG,
                        "Could not delete state for widget $appWidgetId: ${e.message}"
                    )
                }
            }
        }
    }
}


private fun cacheImages(
    layoutRoot: LayoutRoot?,
    valuesMap: MutableMap<String, JsonElement>,
    context: Context
) {
    // Pre-warm image cache: download images in the background so the content provider
    // can serve them instantly without blocking the RemoteViews host process.
    val imageUrls = mutableSetOf<String>()
    if (layoutRoot != null) imageUrls += collectImageUrls(layoutRoot.layout)
    valuesMap.values.forEach { element -> imageUrls += collectImageUrlsFromValue(element) }

    imageUrls.forEach { url ->
        try {
            val file = WidgetImageCache.cacheFile(context, url)
            if (!file.exists()) {
                file.parentFile?.mkdirs()
                val bytes = URL(url).openStream().use { it.readBytes() }
                file.writeBytes(bytes)
            }
        } catch (_: Exception) { /* non-fatal: provider will retry on demand */
        }
    }
}

/**
 * Recursively collect all non-blank image URLs from [ImageWidget] nodes in the static layout tree.
 */
private fun collectImageUrls(widget: Widget): List<String> = when (widget) {
    is ImageWidget -> listOfNotNull(widget.url.takeIf { it.isNotBlank() })
    is com.damn.aisuper.layout.ColumnWidget ->
        widget.children.flatMap { collectImageUrls(it) }
    is com.damn.aisuper.layout.RowWidget ->
        widget.children.flatMap { collectImageUrls(it) }
    else -> emptyList()
}

/**
 * Extract image URLs from a [JsonElement] that may be a dynamic widget array value
 * (e.g. `imageList = [{"type":"Image","url":"..."},...]`).
 * Handles both raw JsonArray and a string-encoded JSON array.
 */
private fun collectImageUrlsFromValue(element: JsonElement): List<String> {
    val array: JsonArray = when (element) {
        is JsonArray -> element
        is JsonPrimitive -> {
            val str = element.content
            try {
                Json.parseToJsonElement(str) as? JsonArray
            } catch (_: Exception) {
                null
            }
        }

        else -> null
    } ?: return emptyList()

    return array.mapNotNull { item ->
        if (item is JsonObject) {
            val type = (item["type"] as? JsonPrimitive)?.content
            if (type == "Image") {
                (item["url"] as? JsonPrimitive)?.content
                    ?.takeIf { it.isNotBlank() }
            } else null
        } else null
    }
}

/**
 * Load the raw JSON string for a style by ID from applet.json + the style file path.
 * Returns null if the style cannot be found or loaded.
 */
@androidx.annotation.WorkerThread
private suspend fun loadStyleJson(context: Context, styleId: String): String? {
    return try {
        val manifestBytes = Res.readBytes("files/applet.json")
        val manifest = json
            .decodeFromString<AppletManifest>(manifestBytes.decodeToString())
        val styleFile = manifest.styles[styleId]?.file ?: run {
            Log.w(TAG, "Style '$styleId' not found in manifest")
            return null
        }
        Res.readBytes(styleFile).decodeToString()
    } catch (e: Exception) {
        Log.e(TAG, "Failed to load style '$styleId': ${e.message}")
        null
    }
}
