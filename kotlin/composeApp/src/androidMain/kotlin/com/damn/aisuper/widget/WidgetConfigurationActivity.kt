package com.damn.aisuper.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.damn.aisuper.R
import com.damn.aisuper.applet.AndroidAppletScanner
import com.damn.aisuper.applet.AppletEntry
import com.damn.aisuper.applet.decodeAndroidAppletRef
import com.damn.aisuper.applet.parseAppletManifestBytes
import com.damn.aisuper.modules.impl.platform.android.AndroidAppContextHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class WidgetFeatureChoice(
    val entry: AppletEntry,
    val featureId: String,
    val featureName: String,
    val styles: List<Pair<String, String>>,
    val defaultStyleId: String?
)

class WidgetConfigurationActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

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
                    onConfirm = { choice, styleId ->
                        confirmSelection(choice, styleId)
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        if (AndroidAppContextHolder.currentActivity === this) AndroidAppContextHolder.currentActivity = null
        super.onDestroy()
    }

    private fun confirmSelection(choice: WidgetFeatureChoice, styleId: String?) {
        val resultValue = Intent().apply { putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId) }

        lifecycleScope.launch {
            try {
                val glanceId = GlanceAppWidgetManager(applicationContext).getGlanceIdBy(appWidgetId)

                val styleJson: String? = withContext(Dispatchers.IO) {
                    val chosenStyleId = styleId?.takeIf { it.isNotBlank() } ?: choice.defaultStyleId
                    if (chosenStyleId.isNullOrBlank()) return@withContext null
                    runCatching {
                        val ref = decodeAndroidAppletRef(choice.entry.sourceRef)
                        val resolved = AndroidAppletScanner.resolveRef(applicationContext, ref)
                        val loader = resolved.provider.createLoader()
                        val manifest = parseAppletManifestBytes(loader.readBytes(resolved.entry.entryPath))
                        val styleFile = manifest.styles[chosenStyleId]?.file ?: return@runCatching null
                        loader.readBytes(styleFile).decodeToString()
                    }.getOrNull()
                }

                updateAppWidgetState(applicationContext, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    prefs.toMutablePreferences().apply {
                        this[PREF_APPLET_REF] = choice.entry.sourceRef
                        this[PREF_FEATURE_ID] = choice.featureId
                        if (styleJson != null) {
                            this[PREF_STYLE_JSON] = styleJson
                        } else {
                            remove(PREF_STYLE_JSON)
                        }
                    }
                }

                WidgetAppletManager.getOrCreateApplet(
                    context = applicationContext,
                    glanceId = glanceId,
                    appletRefJson = choice.entry.sourceRef,
                    featureId = choice.featureId
                )

                AISuperWidget().update(applicationContext, glanceId)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            setResult(RESULT_OK, resultValue)
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetConfigScreen(onConfirm: (choice: WidgetFeatureChoice, styleId: String?) -> Unit) {
    val context = LocalContext.current
    var widgetChoices by remember { mutableStateOf<List<WidgetFeatureChoice>>(emptyList()) }
    var availableStyles by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var selectedStyleId by remember { mutableStateOf<String?>(null) }
    var styleDropdownExpanded by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            val choices = mutableListOf<WidgetFeatureChoice>()
            val applets = runCatching { AndroidAppletScanner.scanDefaultDirectory(context) }.getOrDefault(emptyList())
            for (entry in applets) {
                runCatching {
                    val resolved = AndroidAppletScanner.resolveRef(context, decodeAndroidAppletRef(entry.sourceRef))
                    val bytes = resolved.provider.createLoader().readBytes(entry.entryPath)
                    val manifest = parseAppletManifestBytes(bytes)
                    val styles = manifest.styles.map { (id, def) -> id to (def.name ?: id) }
                    manifest.features
                        .filter { (_, def) -> def.supportsWidget }
                        .forEach { (featureId, def) ->
                            choices.add(
                                WidgetFeatureChoice(
                                    entry = entry,
                                    featureId = featureId,
                                    featureName = def.name ?: featureId,
                                    styles = styles,
                                    defaultStyleId = manifest.defaultStyle ?: styles.firstOrNull()?.first
                                )
                            )
                        }
                }
            }
            choices
        }

        widgetChoices = loaded
        val firstStyles = loaded.firstOrNull()?.styles.orEmpty()
        availableStyles = firstStyles
        selectedStyleId = loaded.firstOrNull()?.defaultStyleId ?: firstStyles.firstOrNull()?.first
        loading = false
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.lbl_add_aisuper_widget),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

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
                        label = { Text(stringResource(R.string.lbl_theme)) },
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
                text = stringResource(R.string.lbl_choose_feature),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (loading) {
                CircularProgressIndicator()
            } else if (widgetChoices.isEmpty()) {
                Text("No applets in the default directory expose a widget-capable feature.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(widgetChoices) { choice ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onConfirm(choice, selectedStyleId) }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = choice.featureName,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = choice.entry.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
