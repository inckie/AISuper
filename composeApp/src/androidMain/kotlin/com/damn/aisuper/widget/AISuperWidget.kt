package com.damn.aisuper.widget

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
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
import com.damn.aisuper.engine.KeightJSEngine
import com.damn.aisuper.layout.ImageWidget
import com.damn.aisuper.layout.LayoutRoot
import com.damn.aisuper.layout.StyleSheet
import com.damn.aisuper.layout.Widget
import com.damn.aisuper.layout.frontend.glance.RenderWidget
import com.damn.aisuper.layout.parseLayout
import com.damn.aisuper.modules.impl.platform.android.AndroidAppContextHolder
import com.damn.aisuper.runtime.Applet
import com.damn.aisuper.runtime.AppletManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URL

/** Preference key: which featureId this widget instance is bound to. */
val PREF_FEATURE_ID = stringPreferencesKey("feature_id")
/** Serialised key→value snapshot from the feature's value state. */
val PREF_VALUES_JSON = stringPreferencesKey("values_json")
/** Layout JSON snapshot for this feature. */
val PREF_LAYOUT_JSON = stringPreferencesKey("layout_json")
/** Serialised StyleSheet JSON for the selected style. */
val PREF_STYLE_JSON = stringPreferencesKey("style_json")

private val json = Json { ignoreUnknownKeys = true }

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
            val styleJson  = prefs[PREF_STYLE_JSON]   ?: ""

            val styleSheet: StyleSheet? = styleJson.takeIf { it.isNotBlank() }?.let {
                try {
                    json.decodeFromString<StyleSheet>(it)
                } catch (e: Exception) {
                    Log.e("AISuperWidget", "Failed to parse style JSON: ${e.message}")
                    null
                }
            }

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

            // Use style's "screen" class background if available, else dark fallback
            val screenBg = com.damn.aisuper.layout.parseColorOrNull(
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
                        widget = layoutRoot.layout,
                        values = values,
                        styleSheet = styleSheet,
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
suspend fun refreshWidgetData(context: Context, glanceId: GlanceId, featureId: String, styleId: String? = null) {
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
            val valuesMap  = feature?.values?.value?.toMutableMap() ?: mutableMapOf()

            cacheImages(layoutRoot, valuesMap, context)

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
                    // Load and persist stylesheet if styleId provided
                    if (!styleId.isNullOrBlank()) {
                        val styleJson = loadStyleJson(context, styleId)
                        if (styleJson != null) this[PREF_STYLE_JSON] = styleJson
                    }
                }
            }

            AISuperWidget().update(context, glanceId)
            applet.close()
        } catch (e: Exception) {
            e.printStackTrace()
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
            try { Json.parseToJsonElement(str) as? JsonArray } catch (_: Exception) { null }
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
        val manifestBytes = aisuper.composeapp.generated.resources.Res.readBytes("files/applet.json")
        val manifest = json
            .decodeFromString<AppletManifest>(manifestBytes.decodeToString())
        val styleFile = manifest.styles[styleId]?.file ?: run {
            Log.w("AISuperWidget", "Style '$styleId' not found in manifest")
            return null
        }
        aisuper.composeapp.generated.resources.Res.readBytes(styleFile).decodeToString()
    } catch (e: Exception) {
        Log.e("AISuperWidget", "Failed to load style '$styleId': ${e.message}")
        null
    }
}
