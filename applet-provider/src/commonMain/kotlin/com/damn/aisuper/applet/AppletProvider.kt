package com.damn.aisuper.applet

import com.damn.aisuper.runtime.AppletResourceLoader

/**
 * High-level factory for [AppletResourceLoader] implementations.
 *
 * Each provider knows where to find applet artifacts (filesystem, classpath, ZIP, …)
 * and produces a loader configured for that source.
 *
 * Usage:
 * ```kotlin
 * // Bundled resources on the JVM classpath (default applet jar)
 * val provider = AppletProviders.classpath()
 *
 * // Custom directory on disk (user-supplied applets)
 * val provider = AppletProviders.filesystem(Path.of("/srv/applets"))
 *
 * // Future: ZIP bundle
 * val provider = AppletProviders.zip(Path.of("applet.zip"))
 *
 * val applet = Applet(engineFactory = { … }, resourceLoader = provider.createLoader())
 * applet.loadApplet("files/applet.json")
 * ```
 */
interface AppletProvider {
    /** Produces an [AppletResourceLoader] backed by this provider. */
    fun createLoader(): AppletResourceLoader
}

