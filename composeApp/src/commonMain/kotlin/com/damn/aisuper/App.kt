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

import aisuper.composeapp.generated.resources.Res
import com.damn.aisuper.engine.JSEngine
import com.damn.aisuper.engine.SimpleJSEngine
import com.damn.aisuper.layout.LayoutRoot
import com.damn.aisuper.layout.RenderWidget
import com.damn.aisuper.layout.parseLayout

@OptIn(ExperimentalResourceApi::class)
@Composable
@Preview
fun App() {
    MaterialTheme {
        var layoutRoot by remember { mutableStateOf<LayoutRoot?>(null) }
        var layoutValues by remember { mutableStateOf(mapOf<String, String>()) }
        var scriptContent by remember { mutableStateOf("") }
        val engine = remember { SimpleJSEngine() }

        LaunchedEffect(Unit) {
            try {
                // Using string path for resources
                val bytes = Res.readBytes("files/echo_chat.json")
                val jsonString = bytes.decodeToString()
                layoutRoot = parseLayout(jsonString)

                val scriptBytes = Res.readBytes("files/echo_block.js")
                scriptContent = scriptBytes.decodeToString()
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
                        layoutValues = layoutValues.toMutableMap().apply { put(id, value) }
                    },
                    onAction = { action ->
                        // Placeholder for JS integration
                        if (action == "processEcho") {
                            val input = layoutValues["input_field"] ?: ""

                            // Execute JS Block
                            val result = engine.execute(scriptContent, "process", listOf(input))

                            layoutValues = layoutValues.toMutableMap().apply {
                                put("result_text", result)
                            }
                        }
                    }
                )
            } else {
                Text("Loading layout...")
            }
        }
    }
}