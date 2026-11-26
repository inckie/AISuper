package com.damn.aisuper

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.damn.aisuper.applet.AppletEntry
import com.damn.aisuper.applet.AppletSourceKind
import com.damn.aisuper.applet.JvmAppletScanner
import com.damn.aisuper.applet.ResolvedApplet
import com.damn.aisuper.engine.createAppJSEngine
import com.damn.aisuper.harness.McpServer
import com.damn.aisuper.host.ActiveAppletSession
import com.damn.aisuper.host.AppletHostActions
import com.damn.aisuper.host.AppletHostState
import com.damn.aisuper.runtime.Applet
import com.damn.aisuper.util.LogBufferSink
import com.damn.aisuper.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Rectangle
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import java.nio.file.Paths
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.filechooser.FileNameExtensionFilter

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
    val mcpPort = args.indexOf("--mcp-server").let {
        if (it != -1) args.getOrNull(it + 1)?.toIntOrNull() ?: 8081 else null
    }
    val cliPathArg = args.firstOrNull { !it.startsWith("--") && it != mcpPort?.toString() }

    val initialCliResolution = remember(cliPathArg) {
        cliPathArg?.let { pathString ->
            runCatching { JvmAppletScanner.resolveAppletPath(Paths.get(pathString)) }
        }
    }

    val initialCliApplet = remember(initialCliResolution) {
        initialCliResolution?.getOrNull()?.let { resolved ->
            Applet(
                engineFactory = { createAppJSEngine("app-ui") },
                resourceLoader = resolved.provider.createLoader()
            )
        }
    }

    var sessionCounter by remember { mutableLongStateOf(1L) }
    var activeSession by remember {
        mutableStateOf(
            initialCliResolution?.getOrNull()?.let { resolved ->
                ActiveAppletSession(
                    entry = resolved.entry,
                    provider = resolved.provider,
                    providedApplet = initialCliApplet,
                    sessionKey = 1L
                )
            }
        )
    }
    var showPicker by remember { mutableStateOf(activeSession == null) }
    var hostState by remember {
        mutableStateOf(
            AppletHostState(
                defaultDirectoryLabel = JvmAppletScanner.getConfiguredAppletsDirectory().toString(),
                scanError = initialCliResolution?.exceptionOrNull()?.let {
                    "Failed to load '$cliPathArg': ${it.message}"
                },
                canSelectDefaultDirectory = true,
                canPickExternalFolder = true,
                canPickExternalFile = true,
                canPinHomeScreenShortcut = false,
                supportsDragAndDrop = true
            )
        )
    }

    val scope = rememberCoroutineScope()

    fun refreshDirectory() {
        val dir = JvmAppletScanner.getConfiguredAppletsDirectory()
        hostState = hostState.copy(
            defaultDirectoryLabel = dir.toString(),
            isScanning = true,
            scanError = null
        )
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { JvmAppletScanner.scanDirectory(dir) }
            }
            hostState = result.fold(
                onSuccess = { list ->
                    hostState.copy(
                        defaultDirectoryLabel = dir.toString(),
                        discoveredApplets = list,
                        isScanning = false,
                        scanError = null
                    )
                },
                onFailure = { err ->
                    hostState.copy(
                        isScanning = false,
                        scanError = err.message ?: "Failed to scan directory."
                    )
                }
            )
        }
    }

    fun launchResolved(resolved: ResolvedApplet) {
        sessionCounter += 1L
        activeSession = ActiveAppletSession(
            entry = resolved.entry,
            provider = resolved.provider,
            sessionKey = sessionCounter
        )
        showPicker = false
        hostState = hostState.copy(scanError = null, statusMessage = null)
    }

    fun openPath(file: File) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { JvmAppletScanner.resolveAppletPath(file.toPath()) }
            }
            result.onSuccess { resolved ->
                launchResolved(resolved)
            }.onFailure { err ->
                hostState = hostState.copy(
                    scanError = err.message ?: "Failed to open '${file.name}'."
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshDirectory()
    }

    val appletRoot = cliPathArg?.let { File(it) }?.takeIf { it.isDirectory }
    var mcpServer by remember { mutableStateOf<McpServer?>(null) }

    if (mcpPort != null && initialCliApplet != null) {
        val logBuffer = remember { LogBufferSink() }
        LaunchedEffect(initialCliApplet, mcpPort, appletRoot) {
            Logger.addSink(logBuffer)
            withContext(Dispatchers.IO) {
                try {
                    val server = McpServer(initialCliApplet, mcpPort, logBuffer, appletRoot)
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

    val windowTitle = if (!showPicker && activeSession != null) {
        "AISuper — ${activeSession!!.entry.name}"
    } else {
        "AISuper"
    }

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = windowTitle,
    ) {
        val awtWindow = this.window

        DisposableEffect(awtWindow) {
            val dropTarget = DropTarget(awtWindow, DnDConstants.ACTION_COPY, object : DropTargetAdapter() {
                override fun drop(dtde: DropTargetDropEvent) {
                    try {
                        dtde.acceptDrop(DnDConstants.ACTION_COPY)
                        val transferable = dtde.transferable
                        if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                            @Suppress("UNCHECKED_CAST")
                            val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<File>
                            val first = files?.firstOrNull()
                            if (first != null) {
                                openPath(first)
                                dtde.dropComplete(true)
                                return
                            }
                        }
                        dtde.dropComplete(false)
                    } catch (_: Exception) {
                        dtde.dropComplete(false)
                    }
                }
            }, true)

            onDispose {
                dropTarget.isActive = false
            }
        }

        if (mcpPort != null && mcpServer != null) {
            val coroutineScope = rememberCoroutineScope()
            LaunchedEffect(awtWindow, mcpServer) {
                val focusAction = {
                    try {
                        java.awt.EventQueue.invokeLater {
                            if (awtWindow.state == java.awt.Frame.ICONIFIED) {
                                awtWindow.state = java.awt.Frame.NORMAL
                            }
                            if (awtWindow.x >= 1920 || awtWindow.x < 0) {
                                awtWindow.setLocation(100, 100)
                            }
                            awtWindow.isAlwaysOnTop = true
                            awtWindow.toFront()
                            awtWindow.requestFocus()
                            awtWindow.isAlwaysOnTop = false
                        }
                    } catch (_: Exception) {}
                }
                mcpServer?.setWindowFocusHandler(focusAction)
                mcpServer?.setWindowBoundsProvider {
                    focusAction()
                    Rectangle(awtWindow.x, awtWindow.y, awtWindow.width, awtWindow.height)
                }
                mcpServer?.setWindowResizeHandler { width, height ->
                    try {
                        java.awt.EventQueue.invokeLater {
                            awtWindow.setSize(width, height)
                            if (awtWindow.x >= 1920 || awtWindow.x < 0) {
                                awtWindow.setLocation(100, 100)
                            }
                        }
                    } catch (_: Exception) {}
                }
                mcpServer?.setWindowTapHandler { x, y ->
                    try {
                        java.awt.EventQueue.invokeLater {
                            val insets = awtWindow.insets
                            val contentX = (x - insets.left).coerceAtLeast(0)
                            val contentY = (y - insets.top).coerceAtLeast(0)
                            val target = awtWindow.contentPane.findComponentAt(contentX, contentY) ?: awtWindow.contentPane
                            val pt = SwingUtilities.convertPoint(awtWindow.contentPane, contentX, contentY, target)
                            val time = System.currentTimeMillis()

                            target.dispatchEvent(java.awt.event.MouseEvent(
                                target, java.awt.event.MouseEvent.MOUSE_ENTERED, time, 0, pt.x, pt.y, 0, false
                            ))
                            target.dispatchEvent(java.awt.event.MouseEvent(
                                target, java.awt.event.MouseEvent.MOUSE_MOVED, time, 0, pt.x, pt.y, 0, false
                            ))
                            target.dispatchEvent(java.awt.event.MouseEvent(
                                target, java.awt.event.MouseEvent.MOUSE_PRESSED, time,
                                java.awt.event.InputEvent.BUTTON1_DOWN_MASK, pt.x, pt.y, 1, false,
                                java.awt.event.MouseEvent.BUTTON1
                            ))
                            target.dispatchEvent(java.awt.event.MouseEvent(
                                target, java.awt.event.MouseEvent.MOUSE_RELEASED, time + 20, 0,
                                pt.x, pt.y, 1, false, java.awt.event.MouseEvent.BUTTON1
                            ))
                            target.dispatchEvent(java.awt.event.MouseEvent(
                                target, java.awt.event.MouseEvent.MOUSE_CLICKED, time + 25, 0,
                                pt.x, pt.y, 1, false, java.awt.event.MouseEvent.BUTTON1
                            ))
                        }
                    } catch (e: Exception) {
                        Logger.e("Harness", throwable = e) { "Failed to tap: ${e.message}" }
                    }
                }
                mcpServer?.setWindowScrollHandler { x, y, amount ->
                    try {
                        java.awt.EventQueue.invokeLater {
                            val px = x ?: (awtWindow.contentPane.width / 2)
                            val py = y ?: (awtWindow.contentPane.height / 2)
                            val target = awtWindow.contentPane.findComponentAt(px, py) ?: awtWindow.contentPane
                            val pt = SwingUtilities.convertPoint(awtWindow.contentPane, px, py, target)
                            val time = System.currentTimeMillis()

                            target.dispatchEvent(java.awt.event.MouseWheelEvent(
                                target, java.awt.event.MouseEvent.MOUSE_WHEEL, time, 0, pt.x, pt.y,
                                0, false, java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL, amount, amount
                            ))
                        }
                    } catch (e: Exception) {
                        Logger.e("Harness", throwable = e) { "Failed to scroll: ${e.message}" }
                    }
                }
                mcpServer?.setWindowDragHandler { startX, startY, endX, endY, durationMs ->
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val target = awtWindow.contentPane.findComponentAt(startX, startY) ?: awtWindow.contentPane
                            val ptStart = SwingUtilities.convertPoint(awtWindow.contentPane, startX, startY, target)
                            val ptEnd = SwingUtilities.convertPoint(awtWindow.contentPane, endX, endY, target)

                            val time = System.currentTimeMillis()
                            java.awt.EventQueue.invokeLater {
                                target.dispatchEvent(java.awt.event.MouseEvent(
                                    target, java.awt.event.MouseEvent.MOUSE_PRESSED, time,
                                    java.awt.event.InputEvent.BUTTON1_DOWN_MASK, ptStart.x, ptStart.y, 1, false,
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
                                        target, java.awt.event.MouseEvent.MOUSE_DRAGGED,
                                        System.currentTimeMillis(), java.awt.event.InputEvent.BUTTON1_DOWN_MASK,
                                        curX, curY, 0, false, java.awt.event.MouseEvent.BUTTON1
                                    ))
                                }
                                Thread.sleep(stepDelay)
                            }

                            java.awt.EventQueue.invokeLater {
                                target.dispatchEvent(java.awt.event.MouseEvent(
                                    target, java.awt.event.MouseEvent.MOUSE_RELEASED,
                                    System.currentTimeMillis(), 0, ptEnd.x, ptEnd.y, 1, false,
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

        val hostActions = remember(awtWindow) {
            object : AppletHostActions {
                override fun refreshDirectory() {
                    refreshDirectory()
                }

                override fun openApplet(entry: AppletEntry) {
                    val targetPath = when (entry.sourceKind) {
                        AppletSourceKind.ZIP -> Paths.get(entry.sourceRef)
                        AppletSourceKind.DIRECTORY -> Paths.get(entry.sourceRef).resolve(entry.entryPath)
                    }
                    openPath(targetPath.toFile())
                }

                override fun selectDefaultDirectory() {
                    scope.launch {
                        val chosen = pickDirectoryDialog(
                            awtWindow,
                            "Choose Default Applets Directory",
                            JvmAppletScanner.getConfiguredAppletsDirectory().toFile()
                        )
                        if (chosen != null) {
                            withContext(Dispatchers.IO) {
                                JvmAppletScanner.setConfiguredAppletsDirectory(chosen.toPath())
                            }
                            refreshDirectory()
                        }
                    }
                }

                override fun pickExternalFolder() {
                    scope.launch {
                        val chosen = pickDirectoryDialog(
                            awtWindow,
                            "Select Applet Folder",
                            JvmAppletScanner.getConfiguredAppletsDirectory().toFile()
                        )
                        if (chosen != null) {
                            openPath(chosen)
                        }
                    }
                }

                override fun pickExternalFile() {
                    scope.launch {
                        val chosen = pickAppletFileDialog(
                            awtWindow,
                            JvmAppletScanner.getConfiguredAppletsDirectory().toFile()
                        )
                        if (chosen != null) {
                            openPath(chosen)
                        }
                    }
                }

                override fun reloadActiveApplet() {
                    val current = activeSession ?: return
                    sessionCounter += 1L
                    activeSession = current.copy(sessionKey = sessionCounter)
                }

                override fun clearStatusMessage() {
                    hostState = hostState.copy(statusMessage = null, scanError = null)
                }
            }
        }

        App(
            activeSession = activeSession,
            hostState = hostState,
            hostActions = hostActions,
            showPicker = showPicker,
            onOpenPicker = {
                refreshDirectory()
                showPicker = true
            },
            onClosePicker = {
                if (activeSession != null) showPicker = false
            },
            onCloseApplet = {
                activeSession = null
                showPicker = true
            }
        )
    }
}

private suspend fun pickDirectoryDialog(
    parent: java.awt.Window,
    title: String,
    initialDir: File
): File? = withContext(Dispatchers.IO) {
    var selected: File? = null
    SwingUtilities.invokeAndWait {
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
        val chooser = JFileChooser(initialDir.takeIf { it.exists() }).apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
        }
        if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
            selected = chooser.selectedFile
        }
    }
    selected
}

private suspend fun pickAppletFileDialog(
    parent: java.awt.Window,
    initialDir: File
): File? = withContext(Dispatchers.IO) {
    var selected: File? = null
    SwingUtilities.invokeAndWait {
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
        val chooser = JFileChooser(initialDir.takeIf { it.exists() }).apply {
            dialogTitle = "Open Applet (.zip or applet.json)"
            fileSelectionMode = JFileChooser.FILES_ONLY
            fileFilter = FileNameExtensionFilter("AISuper Applets (*.zip, *.json)", "zip", "json")
        }
        if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
            selected = chooser.selectedFile
        }
    }
    selected
}
