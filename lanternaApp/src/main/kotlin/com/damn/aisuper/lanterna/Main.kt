package com.damn.aisuper.lanterna

import com.damn.aisuper.applet.AppletProviders
import com.damn.aisuper.engine.createAppJSEngine
import com.damn.aisuper.layout.*
import com.damn.aisuper.runtime.Applet
import com.damn.aisuper.util.LogLevel
import com.damn.aisuper.util.Logger
import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.gui2.Button
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import com.googlecode.lanterna.terminal.MouseCaptureMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Paths

fun main(args: Array<String>) {
    // 0. Disable logging to avoid messing up the Terminal UI
    Logger.minLevel = LogLevel.NONE

    // 1. Setup Lanterna Terminal & GUI with Mouse Capture
    val terminalFactory = DefaultTerminalFactory()
    terminalFactory.setMouseCaptureMode(MouseCaptureMode.CLICK_RELEASE)

    val terminal = terminalFactory.createTerminal()
    val screen = TerminalScreen(terminal)
    screen.startScreen()

    val gui = MultiWindowTextGUI(screen)
    val window = BasicWindow("AISuper Terminal")
    window.setHints(listOf(Window.Hint.FULL_SCREEN))

    val mainPanel = Panel(LinearLayout(Direction.VERTICAL))
    window.component = mainPanel

    // 2. Setup AISuper Core Applet
    val provider = if (args.isNotEmpty()) {
        val path = Paths.get(args[0])
        val file = path.toFile()
        if (file.isFile && file.extension.equals("zip", ignoreCase = true)) {
            AppletProviders.zip(path)
        } else {
            AppletProviders.filesystem(path, fallbackToClasspath = true)
        }
    } else {
        AppletProviders.classpath()
    }

    val applet = Applet(
        engineFactory = { createAppJSEngine("app-ui") },
        resourceLoader = provider.createLoader()
    )

    // 3. Collect State and Render
    val scope = CoroutineScope(Dispatchers.Default + Job())

    scope.launch {
        val entryPath = if (args.isNotEmpty()) "applet.json" else "files/applet.json"
        try {
            applet.loadApplet(entryPath)
        } catch (e: Exception) {
            if (entryPath != "files/applet.json") {
                // We use println here because Logger is disabled and we might want to see critical startup errors
                // but since it's a TUI, even this might be problematic if it happens after screen.startScreen()
                // However, the requirement was to disable Logger, so we follow that.
                try {
                    applet.loadApplet("files/applet.json")
                } catch (e2: Exception) {
                    // Critical failure
                }
            }
        }
    }

    scope.launch {
        var layoutJob: Job? = null
        var previousRoot: LayoutRoot? = null
        var previousValues: Map<String, JsonElement> = emptyMap()
        val lastTypedValues = mutableMapOf<String, String>()

        applet.currentFeature.collect { feature ->
            layoutJob?.cancel()
            previousRoot = null
            previousValues = emptyMap()
            lastTypedValues.clear()

            if (feature == null) {
                gui.updateUI {
                    mainPanel.removeAllComponents()
                    mainPanel.addComponent(Label("Loading Applet..."))
                }
                return@collect
            }

            layoutJob = scope.launch {
                combine(
                    feature.layoutRoot,
                    feature.values
                ) { root, values ->
                    Pair(root, values)
                }.collect { (root, values) ->
                    val rootChanged = root != previousRoot
                    val changedKeys = values.filter { (k, v) -> previousValues[k] != v }.keys

                    val meaningfulChanges = changedKeys.filter { key ->
                        val typedVal = lastTypedValues[key]
                        val newVal = values[key]?.stringOrNull() ?: ""
                        typedVal != newVal
                    }

                    previousRoot = root
                    previousValues = values

                    if (!rootChanged && meaningfulChanges.isEmpty()) {
                        // Skip re-render if it's just an echo of what we are currently typing
                        return@collect
                    }

                    gui.updateUI {
                        mainPanel.removeAllComponents()
                        if (root != null) {
                            val rendered =
                                renderWidget(root.layout, applet, values, lastTypedValues)
                            mainPanel.addComponent(rendered)
                            // Automatically focus the first interactive component to maintain keyboard navigation
                            findFirstInteractable(rendered)?.takeFocus()
                        } else {
                            mainPanel.addComponent(Label("Loading Feature Layout..."))
                        }
                    }
                }
            }
        }
    }

    // 4. Start the GUI loop (Blocking)
    gui.addWindowAndWait(window)

    // 5. Cleanup
    scope.cancel()
    applet.close()
    screen.stopScreen()
}

/** Recursively searches a Component hierarchy to find the first focusable Interactable element */
private fun findFirstInteractable(component: Component): Interactable? {
    if (component is Interactable) return component
    if (component is Container) {
        for (child in component.children) {
            val found = findFirstInteractable(child)
            if (found != null) return found
        }
    }
    return null
}

/** Recursively maps an AISuper Widget to a Lanterna Component */
private fun renderWidget(
    widget: Widget,
    applet: Applet,
    values: Map<String, JsonElement>,
    lastTypedValues: MutableMap<String, String>
): Component {
    return when (widget) {
        is ColumnWidget -> {
            val panel = Panel(LinearLayout(Direction.VERTICAL))
            widget.children.forEach {
                panel.addComponent(
                    renderWidget(
                        it,
                        applet,
                        values,
                        lastTypedValues
                    )
                )
            }

            if (widget.dynamicChildrenId != null) {
                val dynamicWidgets = resolveDynamicWidgets(values[widget.dynamicChildrenId])
                dynamicWidgets.forEach { child ->
                    panel.addComponent(renderWidget(child, applet, values, lastTypedValues))
                }
            }
            if (widget.isScrollable) {
                panel.withBorder(Borders.doubleLine())
            }
            panel
        }

        is RowWidget -> {
            val panel = Panel(LinearLayout(Direction.HORIZONTAL))
            widget.children.forEach {
                panel.addComponent(
                    renderWidget(
                        it,
                        applet,
                        values,
                        lastTypedValues
                    )
                )
            }
            panel
        }

        is TextWidget -> Label(resolveText(widget.text, values))

        is ButtonWidget -> Button(widget.text) {
            // Dispatch action to Applet when pressed
            CoroutineScope(Dispatchers.Default).launch {
                applet.handleAction(widget.action, widget.actionArgs)
            }
        }

        is TextFieldWidget -> {
            val currentText = widget.id?.let { values[it]?.stringOrNull() } ?: ""
            val textBox = TextBox(TerminalSize(20, 1), currentText)
            // Push value changes back and register in our typing echo filter
            textBox.setTextChangeListener { newText, _ ->
                widget.id?.let { id ->
                    lastTypedValues[id] = newText
                    applet.updateValue(id, JsonPrimitive(newText))
                }
            }
            textBox
        }

        is ImageWidget -> {
            // Placeholders for Images in the Terminal
            val desc = widget.description.ifBlank { "Image" }
            val placeholder = Panel()
            placeholder.addComponent(Label("[Image Placeholder: $desc]"))
            placeholder
        }

        is DropdownWidget -> {
            val comboBox = ComboBox<String>()
            val options = resolveDropdownOptions(widget, values)
            options.forEach { comboBox.addItem(it.label) }

            // pre-select currently chosen value if any
            val currentValue = widget.id?.let { values[it]?.stringOrNull() }
            if (currentValue != null) {
                val idx = options.indexOfFirst { it.value == currentValue }
                if (idx >= 0) {
                    comboBox.selectedIndex = idx
                }
            }

            comboBox.addListener { selectedIndex, previousIndex, changedByUser ->
                if (changedByUser) {
                    widget.id?.let {
                        applet.updateValue(it, JsonPrimitive(options[selectedIndex].value))
                    }
                    widget.onChangeAction?.takeIf { it.isNotBlank() }?.let { action ->
                        CoroutineScope(Dispatchers.Default).launch {
                            applet.handleAction(
                                action,
                                listOf(JsonPrimitive(options[selectedIndex].value))
                            )
                        }
                    }
                }
            }
            comboBox
        }

        is SwitchWidget -> {
            val checkBox = CheckBox(widget.text)
            val isCheckedState = widget.id?.let { values[it]?.booleanOrNull() } ?: widget.checked
            checkBox.isChecked = isCheckedState
            checkBox.addListener { checked ->
                widget.id?.let { id ->
                    applet.updateValue(id, JsonPrimitive(checked))
                }
            }
            checkBox
        }

        is ProgressWidget -> ProgressBar(0, 100).apply {
            val progressVal =
                widget.progressId?.let { values[it]?.floatOrNull() } ?: widget.progress ?: 0f
            value = (progressVal * 100).toInt()
        }

        is SpinnerWidget -> {
            val visible = widget.visibilityId?.let { values[it]?.booleanOrNull() } ?: true
            if (visible) Label("...") else Label("")
        }

        is AudioPlayerWidget -> {
            val panel = Panel()
            panel.addComponent(Label("--- Audio Player ---"))
            panel.addComponent(Label(widget.title))
            panel.addComponent(Button("Play") {
                CoroutineScope(Dispatchers.Default).launch {
                    applet.handleModuleCommand("audioPlayer", widget.player, "play", emptyList())
                }
            })
            panel.addComponent(Button("Pause") {
                CoroutineScope(Dispatchers.Default).launch {
                    applet.handleModuleCommand("audioPlayer", widget.player, "pause", emptyList())
                }
            })
            panel.addComponent(Button("Stop") {
                CoroutineScope(Dispatchers.Default).launch {
                    applet.handleModuleCommand("audioPlayer", widget.player, "stop", emptyList())
                }
            })
            panel
        }

        else -> Label("<Unsupported Widget: ${widget::class.simpleName}>")
    }.apply {
        // Handle common layout traits
        if (widget.fillMaxWidth || widget.fillMaxSize || widget.weight != null) {
            val grabHorizontal = widget.fillMaxWidth || widget.weight != null
            val growPolicy =
                if (grabHorizontal) LinearLayout.GrowPolicy.CanGrow else LinearLayout.GrowPolicy.None
            layoutData = LinearLayout.createLayoutData(LinearLayout.Alignment.Fill, growPolicy)
        }
    }
}

/** Helper to safely update GUI components from Coroutine threads */
private fun MultiWindowTextGUI.updateUI(block: () -> Unit) {
    guiThread.invokeLater { block() }
}
