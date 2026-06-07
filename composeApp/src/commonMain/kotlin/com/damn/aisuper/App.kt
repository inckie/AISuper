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
import com.damn.aisuper.headless.HeadlessSessionSnapshot
import com.damn.aisuper.headless.RemoteAppletClient
import com.damn.aisuper.layout.StyleSheet
import com.damn.aisuper.layout.frontend.LayoutFrontend
import com.damn.aisuper.layout.frontend.material3.Material3FrontendTheme
import com.damn.aisuper.layout.frontend.rikka.RikkaFrontendTheme
import com.damn.aisuper.layout.parseColorOrNull
import com.damn.aisuper.runtime.Applet
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import com.damn.aisuper.layout.frontend.material3.RenderWidget as Material3RenderWidget
import com.damn.aisuper.layout.frontend.rikka.RenderWidget as RikkaRenderWidget

@Composable
@Preview
fun App(customProvider: AppletProvider? = null, entryPath: String = "files/applet.json") {
    // Local in-process runtime is kept as a fallback when remote kernel is unavailable.
    val applet = remember {
        Applet(
            engineFactory = { createAppJSEngine("app-ui") },
            resourceLoader = (customProvider ?: ComposeAppletProvider()).createLoader()
        )
    }
    val remoteClient = remember { RemoteAppletClient(baseUrl = REMOTE_SERVER_BASE_URL) }
    var remoteState by remember { mutableStateOf<HeadlessSessionSnapshot?>(null) }
    var remoteSessionId by remember { mutableStateOf<String?>(null) }
    var useRemote by remember { mutableStateOf<Boolean?>(null) }
    var localAppletError by remember { mutableStateOf<String?>(null) }

        DisposableEffect(applet, remoteClient) {
            onDispose {
                applet.close()
                remoteClient.close()
            }
        }

        val currentFeature by applet.currentFeature.collectAsState()
        val localStyleSheet by applet.currentStyleSheet.collectAsState()
        val localFrameworkName by applet.currentFramework.collectAsState()
        val styleSheet = if (useRemote == true) remoteState?.styleSheet else localStyleSheet
        val frameworkName = if (useRemote == true) {
            remoteState?.framework ?: LayoutFrontend.Rikka.name
        } else {
            localFrameworkName
        }
        val frontend = try {
            LayoutFrontend.valueOf(frameworkName)
        } catch (_: Exception) {
            LayoutFrontend.Rikka
        }

        // Derive local UI state from the current feature.
        val localLayoutRoot by remember(currentFeature) {
            currentFeature?.layoutRoot ?: flowOf(null)
        }.collectAsState(initial = null)

        val localLayoutValues by remember(currentFeature) {
            currentFeature?.values ?: flowOf(emptyMap())
        }.collectAsState(initial = emptyMap())
        val layoutRoot = if (useRemote == true) remoteState?.layout else localLayoutRoot
        val layoutValues = if (useRemote == true) (remoteState?.values ?: emptyMap()) else localLayoutValues

        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            // Try remote notebook kernel first; fallback to in-process runtime.
            try {
                val created = remoteClient.createSession(REMOTE_MANIFEST_PATH)
                remoteSessionId = created.id
                remoteState = created.state
                useRemote = true

                remoteClient.events(created.id).collect { snapshot ->
                    remoteState = snapshot
                }
            } catch (remoteError: Exception) {
                println("[AISuper][Remote] falling back to local runtime: ${remoteError.message}")
                useRemote = false
                try {
                    applet.loadApplet(entryPath)
                } catch (e: Exception) {
                    val errMsg = "Failed to load custom applet from '$entryPath':\n${e.message}"
                    println(errMsg)
                    localAppletError = errMsg
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(resolveAppBackground(styleSheet, Color.White))
        ) {
            if (useRemote == null) {
                BasicText("Connecting to headless kernel...", modifier = Modifier.safeContentPadding())
                return@Column
            }

            if (localAppletError != null) {
                BasicText(
                    localAppletError!!,
                    modifier = Modifier.safeContentPadding()
                )
                return@Column
            }

            if (layoutRoot == null) {
                BasicText(
                    if (useRemote == true) {
                        "Waiting for remote state..."
                    } else {
                        if (currentFeature == null) "Loading Applet..." else "Loading Feature..."
                    },
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
                                    if (useRemote == true && remoteSessionId != null) {
                                        scope.launch {
                                            remoteState = remoteClient.setValue(
                                                sessionId = remoteSessionId!!,
                                                id = id,
                                                value = JsonPrimitive(value)
                                            )
                                        }
                                    } else {
                                        applet.updateValue(id, JsonPrimitive(value))
                                    }
                                },
                                onAction = { action, args ->
                                    scope.launch {
                                        if (useRemote == true && remoteSessionId != null) {
                                            remoteState = remoteClient.sendAction(remoteSessionId!!, action, args)
                                        } else {
                                            applet.handleAction(action, args)
                                        }
                                    }
                                },
                                onModuleCommand = { moduleType, target, command, args ->
                                    if (useRemote == true && remoteSessionId != null) {
                                        scope.launch {
                                            remoteState = remoteClient.sendModuleCommand(
                                                sessionId = remoteSessionId!!,
                                                moduleType = moduleType,
                                                target = target,
                                                command = command,
                                                args = args
                                            )
                                        }
                                    } else {
                                        applet.handleModuleCommand(moduleType, target, command, args)
                                    }
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
                                    if (useRemote == true && remoteSessionId != null) {
                                        scope.launch {
                                            remoteState = remoteClient.setValue(
                                                sessionId = remoteSessionId!!,
                                                id = id,
                                                value = JsonPrimitive(value)
                                            )
                                        }
                                    } else {
                                        applet.updateValue(id, JsonPrimitive(value))
                                    }
                                },
                                onAction = { action, args ->
                                    scope.launch {
                                        if (useRemote == true && remoteSessionId != null) {
                                            remoteState = remoteClient.sendAction(remoteSessionId!!, action, args)
                                        } else {
                                            applet.handleAction(action, args)
                                        }
                                    }
                                },
                                onModuleCommand = { moduleType, target, command, args ->
                                    if (useRemote == true && remoteSessionId != null) {
                                        scope.launch {
                                            remoteState = remoteClient.sendModuleCommand(
                                                sessionId = remoteSessionId!!,
                                                moduleType = moduleType,
                                                target = target,
                                                command = command,
                                                args = args
                                            )
                                        }
                                    } else {
                                        applet.handleModuleCommand(moduleType, target, command, args)
                                    }
                                },
                                modifier = renderModifier
                            )
                        }
                    }
                }
            }
        }
}

private const val REMOTE_SERVER_BASE_URL = "http://127.0.0.1:8080"
private const val REMOTE_MANIFEST_PATH = "composeApp/src/commonMain/composeResources/files/applet.json"

private fun resolveAppBackground(styleSheet: StyleSheet?, fallback: Color): Color {
    val colorHex = styleSheet?.classes?.get("screen")?.backgroundColor
    return parseColorOrNull(colorHex) ?: fallback
}

