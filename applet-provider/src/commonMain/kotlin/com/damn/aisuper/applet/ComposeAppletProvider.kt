package com.damn.aisuper.applet

import aisuper.appletprovider.generated.resources.Res
import com.damn.aisuper.runtime.AppletResourceLoader
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * [AppletProvider] backed by Compose Multiplatform resources.
 *
 * Reads applet artifacts from the bundled compose resources of the
 * `:applet-provider` module (i.e. `commonMain/composeResources/files/`).
 * Works on all compose targets: Android, iOS, Desktop JVM, JS, WASM.
 *
 * Usage (replaces the old ComposeAppletResourceLoader from composeApp):
 * ```kotlin
 * val applet = Applet(
 *     engineFactory = { … },
 *     resourceLoader = ComposeAppletProvider().createLoader()
 * )
 * applet.loadApplet("files/applet.json")
 * ```
 */
class ComposeAppletProvider : AppletProvider {
    @OptIn(ExperimentalResourceApi::class)
    override fun createLoader(): AppletResourceLoader =
        AppletResourceLoader { path -> Res.readBytes(path) }
}

