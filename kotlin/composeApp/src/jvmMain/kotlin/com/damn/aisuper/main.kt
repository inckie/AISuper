package com.damn.aisuper

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.window.application
import com.damn.aisuper.applet.AppletProviders
import com.damn.aisuper.applet.ComposeAppletProvider
import com.damn.aisuper.engine.createAppJSEngine
import com.damn.aisuper.harness.McpServer
import com.damn.aisuper.runtime.Applet
import com.damn.aisuper.util.LogBufferSink
import com.damn.aisuper.util.Logger
import java.awt.Rectangle
import java.io.File

/**
 * Main application entry point.
 *
 * @wk-id main-entry
 * @wk-tags kotlin, desktop, entry-point
 * @wk-categories architecture-overview
 *
 * Bootstraps the AISuper environment, parses CLI arguments for applet loading, and starts the MCP server.
 */
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
            engineFactory = { createAppJSEngine("app-ui") },
            resourceLoader = (customProvider ?: ComposeAppletProvider()).createLoader()
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

    val windowState = rememberWindowState(
        position = WindowPosition(100.dp, 100.dp),
        size = DpSize(380.dp, 750.dp)
    )

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "AISuper",
    ) {
        if (mcpPort != null && mcpServer != null) {
            val window = this.window
            val coroutineScope = rememberCoroutineScope()
            LaunchedEffect(window, mcpServer) {
                val focusAction = {
                    try {
                        java.awt.EventQueue.invokeLater {
                            if (window.state == java.awt.Frame.ICONIFIED) {
                                window.state = java.awt.Frame.NORMAL
                            }
                            if (window.x >= 1920 || window.x < 0) {
                                window.setLocation(100, 100)
                            }
                            window.isAlwaysOnTop = true
                            window.toFront()
                            window.requestFocus()
                            window.isAlwaysOnTop = false
                        }
                    } catch (_: Exception) {}
                }
                mcpServer?.setWindowFocusHandler(focusAction)
                mcpServer?.setWindowBoundsProvider {
                    focusAction()
                    Rectangle(window.x, window.y, window.width, window.height)
                }
                mcpServer?.setWindowResizeHandler { width, height ->
                    try {
                        java.awt.EventQueue.invokeLater {
                            window.setSize(width, height)
                            if (window.x >= 1920 || window.x < 0) {
                                window.setLocation(100, 100)
                            }
                        }
                    } catch (_: Exception) {}
                }
                mcpServer?.setWindowTapHandler { x, y ->
                    try {
                        java.awt.EventQueue.invokeLater {
                            val insets = window.insets
                            val contentX = (x - insets.left).coerceAtLeast(0)
                            val contentY = (y - insets.top).coerceAtLeast(0)
                            val target = window.contentPane.findComponentAt(contentX, contentY) ?: window.contentPane
                            val pt = javax.swing.SwingUtilities.convertPoint(window.contentPane, contentX, contentY, target)
                            val time = System.currentTimeMillis()

                            // Dispatch AWT mouse events directly to the component without moving the OS mouse cursor
                            target.dispatchEvent(java.awt.event.MouseEvent(
                                target,
                                java.awt.event.MouseEvent.MOUSE_ENTERED,
                                time,
                                0,
                                pt.x, pt.y,
                                0,
                                false
                            ))
                            target.dispatchEvent(java.awt.event.MouseEvent(
                                target,
                                java.awt.event.MouseEvent.MOUSE_MOVED,
                                time,
                                0,
                                pt.x, pt.y,
                                0,
                                false
                            ))
                            target.dispatchEvent(java.awt.event.MouseEvent(
                                target,
                                java.awt.event.MouseEvent.MOUSE_PRESSED,
                                time,
                                java.awt.event.InputEvent.BUTTON1_DOWN_MASK,
                                pt.x, pt.y,
                                1,
                                false,
                                java.awt.event.MouseEvent.BUTTON1
                            ))
                            target.dispatchEvent(java.awt.event.MouseEvent(
                                target,
                                java.awt.event.MouseEvent.MOUSE_RELEASED,
                                time + 20,
                                0,
                                pt.x, pt.y,
                                1,
                                false,
                                java.awt.event.MouseEvent.BUTTON1
                            ))
                            target.dispatchEvent(java.awt.event.MouseEvent(
                                target,
                                java.awt.event.MouseEvent.MOUSE_CLICKED,
                                time + 25,
                                0,
                                pt.x, pt.y,
                                1,
                                false,
                                java.awt.event.MouseEvent.BUTTON1
                            ))
                        }
                    } catch (e: Exception) {
                        Logger.e("Harness", throwable = e) { "Failed to tap: ${e.message}" }
                    }
                }
                mcpServer?.setWindowScrollHandler { x, y, amount ->
                    try {
                        java.awt.EventQueue.invokeLater {
                            val px = x ?: (window.contentPane.width / 2)
                            val py = y ?: (window.contentPane.height / 2)
                            val target = window.contentPane.findComponentAt(px, py) ?: window.contentPane
                            val pt = javax.swing.SwingUtilities.convertPoint(window.contentPane, px, py, target)
                            val time = System.currentTimeMillis()

                            target.dispatchEvent(java.awt.event.MouseWheelEvent(
                                target,
                                java.awt.event.MouseEvent.MOUSE_WHEEL,
                                time,
                                0,
                                pt.x, pt.y,
                                0,
                                false,
                                java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL,
                                amount,
                                amount
                            ))
                        }
                    } catch (e: Exception) {
                        Logger.e("Harness", throwable = e) { "Failed to scroll: ${e.message}" }
                    }
                }
                mcpServer?.setWindowDragHandler { startX, startY, endX, endY, durationMs ->
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val target = window.contentPane.findComponentAt(startX, startY) ?: window.contentPane
                            val ptStart = javax.swing.SwingUtilities.convertPoint(window.contentPane, startX, startY, target)
                            val ptEnd = javax.swing.SwingUtilities.convertPoint(window.contentPane, endX, endY, target)

                            val time = System.currentTimeMillis()
                            java.awt.EventQueue.invokeLater {
                                target.dispatchEvent(java.awt.event.MouseEvent(
                                    target,
                                    java.awt.event.MouseEvent.MOUSE_PRESSED,
                                    time,
                                    java.awt.event.InputEvent.BUTTON1_DOWN_MASK,
                                    ptStart.x, ptStart.y,
                                    1,
                                    false,
                                    java.awt.event.MouseEvent.BUTTON1
                                ))
                            }

                            val steps = 20
                            val stepDelay = (durationMs / steps).coerceAtLeast(10)
                            for (i in 1..steps) {
                                val curX = ptStart.x + ((ptEnd.x - ptStart.x) * i / steps)
                                val curY = ptStart.y + ((ptEnd.y - ptStart.y) * i / steps)
                                java.awt.EventQueue.invokeLater {
                                    target.dispatchEvent(java.awt.event.MouseEvent(
                                        target,
                                        java.awt.event.MouseEvent.MOUSE_DRAGGED,
                                        System.currentTimeMillis(),
                                        java.awt.event.InputEvent.BUTTON1_DOWN_MASK,
                                        curX, curY,
                                        0,
                                        false,
                                        java.awt.event.MouseEvent.BUTTON1
                                    ))
                                }
                                Thread.sleep(stepDelay)
                            }

                            java.awt.EventQueue.invokeLater {
                                target.dispatchEvent(java.awt.event.MouseEvent(
                                    target,
                                    java.awt.event.MouseEvent.MOUSE_RELEASED,
                                    System.currentTimeMillis(),
                                    0,
                                    ptEnd.x, ptEnd.y,
                                    1,
                                    false,
                                    java.awt.event.MouseEvent.BUTTON1
                                ))
                            }
                        } catch (e: Exception) {
                            Logger.e("Harness", throwable = e) { "Failed to drag: ${e.message}" }
                        }
                    }
                }
            }
        }
        val entryPath = if (customProvider != null) "applet.json" else "files/applet.json"
        App(customProvider, entryPath, providedApplet = applet)
    }
}
