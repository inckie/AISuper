package com.damn.aisuper

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.ExperimentalResourceApi

import com.damn.aisuper.engine.KeightJSEngine
import com.damn.aisuper.layout.RenderWidget
import com.damn.aisuper.runtime.Applet
import kotlinx.coroutines.launch

@OptIn(ExperimentalResourceApi::class)
@Composable
@Preview
fun App() {
    MaterialTheme {
        // Instantiate the Applet with the Keight engine
        // wrapped in remember to survive recompositions.
        val applet = remember { Applet(KeightJSEngine()) }

        DisposableEffect(applet) {
            onDispose {
                applet.close()
            }
        }

        // Collect state from the Applet
        val layoutRoot by applet.layoutRoot.collectAsState()
        val layoutValues by applet.values.collectAsState()

        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            // Load the echo chat applet resources
            applet.load("files/echo_chat.json", "files/echo_block.js")
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeContentPadding()
        ) {
            if (layoutRoot != null) {
                RenderWidget(
                    widget = layoutRoot!!.layout,
                    values = layoutValues,
                    onValueChange = { id, value ->
                        applet.updateValue(id, value)
                    },
                    onAction = { action ->
                        scope.launch {
                            applet.handleAction(action)
                        }
                    }
                )
            } else {
                Text("Loading layout...")
            }
        }
    }
}