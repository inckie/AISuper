package com.damn.aisuper

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.damn.aisuper.engine.KeightJSEngine
import com.damn.aisuper.layout.RenderWidget
import com.damn.aisuper.layout.StyleSheet
import com.damn.aisuper.layout.parseColorOrNull
import com.damn.aisuper.runtime.Applet
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.compose.resources.ExperimentalResourceApi

@OptIn(ExperimentalResourceApi::class)
@Composable
@Preview
fun App() {
    MaterialTheme {
        // Instantiate the Applet with the Keight engine factory
        // wrapped in remember to survive recompositions.
        val applet = remember { Applet { KeightJSEngine() } }

        DisposableEffect(applet) {
            onDispose {
                applet.close()
            }
        }

        // Observe the current feature from the Applet
        val currentFeature by applet.currentFeature.collectAsState()
        val styleSheet by applet.currentStyleSheet.collectAsState()

        // Derive UI state from the current feature
        val layoutRoot by remember(currentFeature) {
            currentFeature?.layoutRoot ?: flowOf(null)
        }.collectAsState(initial = null)

        val layoutValues by remember(currentFeature) {
            currentFeature?.values ?: flowOf(emptyMap())
        }.collectAsState(initial = emptyMap())

        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            // Load the applet manifest
            applet.loadApplet("files/applet.json")
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = resolveAppBackground(styleSheet, MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeContentPadding()
            ) {
                if (layoutRoot != null) {
                    RenderWidget(
                        widget = layoutRoot!!.layout,
                        values = layoutValues,
                        styleSheet = styleSheet,
                        onValueChange = { id, value ->
                            applet.updateValue(id, JsonPrimitive(value))
                        },
                        onAction = { action, args ->
                            scope.launch {
                                applet.handleAction(action, args)
                            }
                        },
                        onModuleCommand = { moduleType, target, command, args ->
                            applet.handleModuleCommand(moduleType, target, command, args)
                        }
                    )
                } else {
                    Text(if (currentFeature == null) "Loading Applet..." else "Loading Feature...")
                }
            }
        }
    }
}

private fun resolveAppBackground(styleSheet: StyleSheet?, fallback: Color): Color {
    val colorHex = styleSheet?.classes?.get("screen")?.backgroundColor
    return parseColorOrNull(colorHex) ?: fallback
}

