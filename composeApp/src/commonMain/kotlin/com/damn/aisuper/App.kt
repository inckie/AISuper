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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.damn.aisuper.applet.AppletProvider
import com.damn.aisuper.applet.ComposeAppletProvider
import com.damn.aisuper.engine.createAppJSEngine
import com.damn.aisuper.layout.StyleSheet
import com.damn.aisuper.layout.frontend.LayoutFrontend
import com.damn.aisuper.layout.frontend.material3.Material3FrontendTheme
import com.damn.aisuper.layout.frontend.rikka.RikkaFrontendTheme
import com.damn.aisuper.layout.parseColorOrNull
import com.damn.aisuper.runtime.Applet
import com.damn.aisuper.util.Logger
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import com.damn.aisuper.layout.frontend.material3.RenderWidget as Material3RenderWidget
import com.damn.aisuper.layout.frontend.rikka.RenderWidget as RikkaRenderWidget

@Composable
@Preview
fun App(
    customProvider: AppletProvider? = null,
    entryPath: String = "files/applet.json",
    initialFeatureId: String? = null,
    providedApplet: Applet? = null
) {
    // Local in-process runtime.
    val applet = remember(customProvider, entryPath, providedApplet) {
        providedApplet ?: Applet(
            engineFactory = { createAppJSEngine("app-ui") },
            resourceLoader = (customProvider ?: ComposeAppletProvider()).createLoader()
        )
    }
    var localAppletError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(applet) {
        onDispose {
            applet.close()
        }
    }

    val currentFeature by applet.currentFeature.collectAsState()
    val styleSheet by applet.currentStyleSheet.collectAsState()
    val frameworkName by applet.currentFramework.collectAsState()

    val frontend = try {
        LayoutFrontend.valueOf(frameworkName)
    } catch (_: Exception) {
        LayoutFrontend.Rikka
    }

    // Derive UI state from the current feature.
    val layoutRootSnapshot by remember(currentFeature) {
        currentFeature?.layoutRoot ?: flowOf(null)
    }.collectAsState(initial = null)

    val layoutValues by remember(currentFeature) {
        currentFeature?.values ?: flowOf(emptyMap())
    }.collectAsState(initial = emptyMap())

    val layoutRoot = layoutRootSnapshot
    val scope = rememberCoroutineScope()

    LaunchedEffect(applet, entryPath, initialFeatureId) {
        localAppletError = null
        try {
            applet.loadApplet(entryPath, initialFeatureId)
        } catch (e: Exception) {
            val errMsg = "Failed to load custom applet from '$entryPath':\n${e.message}"
            Logger.e("Runtime") { errMsg }
            localAppletError = errMsg
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(resolveAppBackground(styleSheet, Color.White))
    ) {
        if (localAppletError != null) {
            BasicText(
                localAppletError!!,
                modifier = Modifier.safeContentPadding()
            )
            return@Column
        }

        if (layoutRoot == null) {
            BasicText(
                if (currentFeature == null) "Loading Applet..." else "Loading Feature...",
                modifier = Modifier.safeContentPadding()
            )
        } else {
            val widget = layoutRoot.layout
            val renderModifier = Modifier.fillMaxSize().safeContentPadding()

            when (frontend) {
                LayoutFrontend.Material3 -> {
                    Material3FrontendTheme(styleSheet = styleSheet) {
                        Material3RenderWidget(
                            widget = widget,
                            values = layoutValues,
                            styleSheet = styleSheet,
                            onValueChange = { id, value ->
                                applet.updateValue(id, value)
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
                                applet.updateValue(id, value)
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
