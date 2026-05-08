package com.damn.aisuper

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.text.BasicText
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
import com.damn.aisuper.layout.frontend.LayoutFrontend
import com.damn.aisuper.layout.StyleSheet
import com.damn.aisuper.layout.frontend.material3.Material3FrontendTheme
import com.damn.aisuper.layout.frontend.material3.RenderWidget as Material3RenderWidget
import com.damn.aisuper.layout.parseColorOrNull
import com.damn.aisuper.layout.frontend.rikka.RikkaFrontendTheme
import com.damn.aisuper.layout.frontend.rikka.RenderWidget as RikkaRenderWidget
import com.damn.aisuper.runtime.Applet
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.compose.resources.ExperimentalResourceApi

@OptIn(ExperimentalResourceApi::class)
@Composable
@Preview
fun App() {
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
        val frameworkName by applet.currentFramework.collectAsState()
        val frontend = try {
            LayoutFrontend.valueOf(frameworkName)
        } catch (_: Exception) {
            LayoutFrontend.Rikka
        }

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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(resolveAppBackground(styleSheet, Color.White))
        ) {
            if (layoutRoot == null) {
                BasicText(
                    if (currentFeature == null) "Loading Applet..." else "Loading Feature...",
                    modifier = Modifier.safeContentPadding()
                )
            } else {
                val widget = layoutRoot!!.layout
                val renderModifier = Modifier.fillMaxSize().safeContentPadding()

                when (frontend) {
                    LayoutFrontend.Material3 -> {
                        Material3FrontendTheme(styleSheet = styleSheet) {
                            Material3RenderWidget(
                                widget = widget,
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
                                },
                                modifier = renderModifier
                            )
                        }
                    }

                    LayoutFrontend.Rikka -> {
                        RikkaFrontendTheme(styleSheet = styleSheet) {
                            RikkaRenderWidget(
                                widget = widget,
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
                                },
                                modifier = renderModifier
                            )
                        }
                    }
                }
            }
        }
}

private fun resolveAppBackground(styleSheet: StyleSheet?, fallback: Color): Color {
    val colorHex = styleSheet?.classes?.get("screen")?.backgroundColor
    return parseColorOrNull(colorHex) ?: fallback
}

