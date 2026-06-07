package com.damn.aisuper

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.damn.aisuper.applet.AppletProviders
import com.damn.aisuper.util.Logger
import java.io.File

fun main(args: Array<String>) = application {
    val customProvider = args.firstOrNull()?.let { pathString ->
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

    Window(
        onCloseRequest = ::exitApplication,
        title = "AISuper",
    ) {
        val entryPath = if (customProvider != null) "applet.json" else "files/applet.json"
        if (customProvider != null) {
            App(customProvider, entryPath)
        } else {
            App(null, entryPath)
        }
    }
}
