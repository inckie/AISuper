package com.damn.aisuper.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.damn.aisuper.engine.KeightJSEngine
import com.damn.aisuper.modules.impl.platform.android.AndroidAppContextHolder
import com.damn.aisuper.runtime.Applet
import com.damn.aisuper.runtime.AppletManifest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import aisuper.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

class WidgetConfigurationActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    @OptIn(ExperimentalResourceApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Default result is CANCELED (user pressed back)
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras
            ?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        AndroidAppContextHolder.appContext = applicationContext
        AndroidAppContextHolder.currentActivity = this

        setContent {
            MaterialTheme {
                WidgetConfigScreen(
                    onFeatureSelected = { featureId ->
                        confirmSelection(featureId)
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        if (AndroidAppContextHolder.currentActivity === this) {
            AndroidAppContextHolder.currentActivity = null
        }
        super.onDestroy()
    }

    @OptIn(ExperimentalResourceApi::class)
    private fun confirmSelection(featureId: String) {
        val scope = kotlinx.coroutines.MainScope()
        scope.launch {
            val glanceId = GlanceAppWidgetManager(this@WidgetConfigurationActivity)
                .getGlanceIdBy(appWidgetId)

            // Store feature choice and trigger first data load
            refreshWidgetData(applicationContext, glanceId, featureId)

            val resultValue = Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            setResult(RESULT_OK, resultValue)
            finish()
        }
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun WidgetConfigScreen(onFeatureSelected: (String) -> Unit) {
    var widgetFeatures by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val bytes = Res.readBytes("files/applet.json")
                val json = Json { ignoreUnknownKeys = true }
                val manifest = json.decodeFromString<AppletManifest>(bytes.decodeToString())
                widgetFeatures = manifest.features
                    .filter { (_, def) -> def.supportsWidget }
                    .map { (id, def) -> id to (def.name ?: id) }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                loading = false
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Choose Widget Feature",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            if (loading) {
                CircularProgressIndicator()
            } else if (widgetFeatures.isEmpty()) {
                Text("No features support widget mode.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(widgetFeatures) { (featureId, featureName) ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onFeatureSelected(featureId) }
                        ) {
                            Text(
                                text = featureName,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

