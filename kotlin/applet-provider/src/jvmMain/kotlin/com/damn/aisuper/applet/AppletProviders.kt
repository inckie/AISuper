package com.damn.aisuper.applet

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Entry-point factory for JVM [AppletProvider] implementations.
 *
 * Usage:
 * ```kotlin
 * // Custom directory on disk
 * val provider = AppletProviders.filesystem(root = Paths.get("/srv/applets"))
 *
 * // ZIP bundle
 * val provider = AppletProviders.zip(Paths.get("applet.zip"))
 * ```
 */
object AppletProviders {

    /**
     * Provider that reads from the local filesystem.
     *
     * @param root          Primary search root; defaults to the JVM working directory.
     * @param fallbackRoots Additional roots tried in order when a path is not found under [root].
     */
    fun filesystem(
        root: Path = Paths.get("").toAbsolutePath(),
        vararg fallbackRoots: Path
    ): AppletProvider = FileSystemAppletProvider(root, fallbackRoots.toList())

    /**
     * Provider that reads from a ZIP archive.
     */
    fun zip(zipPath: Path, rootPrefix: String = ""): AppletProvider = ZipAppletProvider(zipPath, rootPrefix)
}
