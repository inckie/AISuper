package com.damn.aisuper

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.damn.aisuper.engine.createAppJSEngine
import com.damn.aisuper.host.ActiveAppletSession
import com.damn.aisuper.host.AppletHostActions
import com.damn.aisuper.host.AppletHostState
import com.damn.aisuper.host.AppletPickerScreen
import com.damn.aisuper.host.AppletStatusBar
import com.damn.aisuper.layout.StyleSheet
import com.damn.aisuper.layout.frontend.LayoutFrontend
import com.damn.aisuper.layout.frontend.material3.Material3FrontendTheme
import com.damn.aisuper.layout.frontend.rikka.RikkaFrontendTheme
import com.damn.aisuper.layout.parseColorOrNull
import com.damn.aisuper.modal.ModalContextProvider
import com.damn.aisuper.runtime.Applet
import com.damn.aisuper.util.Logger
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import com.damn.aisuper.layout.frontend.material3.RenderWidget as Material3RenderWidget
import com.damn.aisuper.layout.frontend.rikka.RenderWidget as RikkaRenderWidget

@Composable
@Preview
fun App(
    activeSession: ActiveAppletSession? = null,
    hostState: AppletHostState = AppletHostState(),
    hostActions: AppletHostActions = object : AppletHostActions {},
    showPicker: Boolean = activeSession == null,
    onOpenPicker: () -> Unit = {},
    onClosePicker: () -> Unit = {},
    onCloseApplet: () -> Unit = {}
) {
    val applet = remember(activeSession?.sessionKey, activeSession?.providedApplet) {
        if (activeSession == null) {
            null
        } else {
            activeSession.providedApplet ?: Applet(
                engineFactory = { createAppJSEngine("app-ui") },
                resourceLoader = activeSession.provider.createLoader()
            )
        }
    }

    var localAppletError by remember(activeSession?.sessionKey) { mutableStateOf<String?>(null) }

    DisposableEffect(applet) {
        onDispose {
            applet?.close()
        }
    }

    val currentManifestState = remember(applet) {
        applet?.currentManifest ?: flowOf(null)
    }.collectAsState(initial = null)
    val currentFeatureState = remember(applet) {
        applet?.currentFeature ?: flowOf(null)
    }.collectAsState(initial = null)
    val styleSheetState = remember(applet) {
        applet?.currentStyleSheet ?: flowOf(null)
    }.collectAsState(initial = null)
    val frameworkNameState = remember(applet) {
        applet?.currentFramework ?: flowOf(LayoutFrontend.Rikka.name)
    }.collectAsState(initial = LayoutFrontend.Rikka.name)

    val currentManifest = currentManifestState.value
    val currentFeature = currentFeatureState.value
    val styleSheet = styleSheetState.value
    val frameworkName = frameworkNameState.value

    val frontend = try {
        LayoutFrontend.valueOf(frameworkName)
    } catch (_: Exception) {
        LayoutFrontend.Rikka
    }

    val layoutRootSnapshot by remember(currentFeature) {
        currentFeature?.layoutRoot ?: flowOf(null)
    }.collectAsState(initial = null)

    val layoutValues by remember(currentFeature) {
        currentFeature?.values ?: flowOf(emptyMap())
    }.collectAsState(initial = emptyMap())

    val scope = rememberCoroutineScope()

    LaunchedEffect(
        applet,
        activeSession?.sessionKey,
        activeSession?.entry?.entryPath,
        activeSession?.initialFeatureId
    ) {
        val session = activeSession ?: return@LaunchedEffect
        val runtime = applet ?: return@LaunchedEffect
        localAppletError = null
        try {
            runtime.loadApplet(session.entry.entryPath, session.initialFeatureId)
        } catch (e: Exception) {
            val errMsg = "Failed to load applet '${session.entry.name}' (${session.entry.entryPath}):\n${e.message}"
            Logger.e("Runtime") { errMsg }
            localAppletError = errMsg
        }
    }

    if (activeSession == null || applet == null || showPicker) {
        MaterialTheme {
            AppletPickerScreen(
                hostState = hostState,
                hostActions = hostActions,
                activeAppletName = activeSession?.let { currentManifest?.name ?: it.entry.name },
                onResumeActive = if (activeSession != null) onClosePicker else null,
                onCloseActive = if (activeSession != null) onCloseApplet else null
            )
        }
        return
    }

    val barTitle = currentManifest?.name ?: activeSession.entry.name

    ModalContextProvider(modalHost = applet.modalHost) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(resolveAppBackground(styleSheet, Color.White))
        ) {
            MaterialTheme {
                AppletStatusBar(
                    title = barTitle,
                    canPinShortcut = hostState.canPinHomeScreenShortcut,
                    onOpenPicker = onOpenPicker,
                    onReload = { hostActions.reloadActiveApplet() },
                    onPinShortcut = {
                        hostActions.pinShortcutToHomeScreen(
                            entry = activeSession.entry,
                            provider = activeSession.provider
                        )
                    }
                )
                if (!hostState.statusMessage.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = hostState.statusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { hostActions.clearStatusMessage() }) {
                                Text("OK")
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (localAppletError != null) {
                    MaterialTheme {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = localAppletError!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = onOpenPicker) {
                                Text("Open Applet Picker")
                            }
                        }
                    }
                    return@Box
                }

                val layoutRoot = layoutRootSnapshot
                if (layoutRoot == null) {
                    BasicText(
                        if (currentFeature == null) "Loading Applet..." else "Loading Feature...",
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    val widget = layoutRoot.layout
                    val renderModifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                            )
                        )

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
    }
}

private fun resolveAppBackground(styleSheet: StyleSheet?, fallback: Color): Color {
    val colorHex = styleSheet?.classes?.get("screen")?.backgroundColor
    return parseColorOrNull(colorHex) ?: fallback
}
