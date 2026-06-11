package com.damn.aisuper

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.window.application
import com.damn.aisuper.applet.AppletProviders
import com.damn.aisuper.harness.McpServer
import com.damn.aisuper.runtime.Applet
import com.damn.aisuper.util.LogBufferSink
import com.damn.aisuper.util.Logger
import java.io.File

fun main(args: Array<String>) = application {
    val mcpPort = args.indexOf("--mcp-server").let { if (it != -1) args.getOrNull(it + 1)?.toIntOrNull() ?: 8081 else null }

    val customProvider = args.firstOrNull()?.let { pathString ->
        if (pathString.startsWith("--")) return@let null
        val file = File(pathString)
        if (file.isDirectory) {
            AppletProviders.filesystem(file.toPath(), fallbackToClasspath = true)
        } else if (file.extension.equals("zip", ignoreCase = true)) {
            AppletProviders.zip(file.toPath())
        } else {
            Logger.w("JVM", "Main") { "Unsupported applet format for: $pathString" }
            null
        }
    }

    val appletRoot = args.firstOrNull()?.let { File(it) }?.takeIf { it.isDirectory }

    val applet = remember(customProvider) {
        Applet(
            engineFactory = { com.damn.aisuper.engine.createAppJSEngine("app-ui") },
            resourceLoader = (customProvider ?: com.damn.aisuper.applet.ComposeAppletProvider()).createLoader()
        )
    }

    var mcpServer by remember { mutableStateOf<McpServer?>(null) }

    if (mcpPort != null) {
        val logBuffer = remember { LogBufferSink() }
        LaunchedEffect(applet, mcpPort, appletRoot) {
            Logger.addSink(logBuffer)
            // Start server in IO dispatcher to avoid blocking UI
            withContext(Dispatchers.IO) {
                try {
                    val server = McpServer(applet, mcpPort, logBuffer, appletRoot)
                    mcpServer = server
                    server.start()
                } catch (e: Exception) {
                    Logger.e("Harness", throwable = e) { "Failed to start MCP Server: ${e.message}" }
                }
            }
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "AISuper",
    ) {
        if (mcpPort != null && mcpServer != null) {
            val window = this.window
            LaunchedEffect(window, mcpServer) {
                mcpServer?.setWindowBoundsProvider {
                    java.awt.Rectangle(window.x, window.y, window.width, window.height)
                }
            }
        }
        val entryPath = if (customProvider != null) "applet.json" else "files/applet.json"
        App(customProvider, entryPath, providedApplet = applet)
    }
}
