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
import com.damn.aisuper.modules.impl.platform.android.AndroidAppContextHolder
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
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras
            ?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        AndroidAppContextHolder.appContext = applicationContext
        AndroidAppContextHolder.currentActivity = this

        setContent {
            MaterialTheme {
                WidgetConfigScreen(
                    onConfirm = { featureId, styleId ->
                        confirmSelection(featureId, styleId)
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        if (AndroidAppContextHolder.currentActivity === this) AndroidAppContextHolder.currentActivity = null
        super.onDestroy()
    }

    @OptIn(ExperimentalResourceApi::class)
    private fun confirmSelection(featureId: String, styleId: String?) {
        val scope = kotlinx.coroutines.MainScope()
        val resultValue = Intent().apply { putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId) }
        setResult(RESULT_OK, resultValue)
        finish()
        scope.launch {
            val glanceId = GlanceAppWidgetManager(applicationContext).getGlanceIdBy(appWidgetId)
            refreshWidgetData(applicationContext, glanceId, featureId, styleId)
        }
    }
}

@OptIn(ExperimentalResourceApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun WidgetConfigScreen(onConfirm: (featureId: String, styleId: String?) -> Unit) {
    var widgetFeatures by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var availableStyles by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var selectedStyleId by remember { mutableStateOf<String?>(null) }
    var styleDropdownExpanded by remember { mutableStateOf(false) }
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
                availableStyles = manifest.styles.map { (id, def) -> id to (def.name ?: id) }
                selectedStyleId = manifest.defaultStyle ?: availableStyles.firstOrNull()?.first
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
                text = "Add AISuper Widget",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Style dropdown
            if (availableStyles.isNotEmpty()) {
                Text(
                    "Style",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                ExposedDropdownMenuBox(
                    expanded = styleDropdownExpanded,
                    onExpandedChange = { styleDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    val selectedLabel = availableStyles.firstOrNull { it.first == selectedStyleId }?.second ?: "Default"
                    OutlinedTextField(
                        value = selectedLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Theme") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = styleDropdownExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = styleDropdownExpanded,
                        onDismissRequest = { styleDropdownExpanded = false }
                    ) {
                        availableStyles.forEach { (id, name) ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = { selectedStyleId = id; styleDropdownExpanded = false }
                            )
                        }
                    }
                }
            }

            Text(
                text = "Choose Feature",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
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
                                .clickable { onConfirm(featureId, selectedStyleId) }
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
