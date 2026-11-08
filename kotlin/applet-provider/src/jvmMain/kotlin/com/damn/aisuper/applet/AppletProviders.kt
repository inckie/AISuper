package com.damn.aisuper.applet

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Entry-point factory for all [AppletProvider] implementations.
 *
 * Recommended usage:
 * ```kotlin
 * // Default: bundled resources on the JVM classpath
 * val provider = AppletProviders.classpath()
 *
 * // Development: custom directory, fall back to bundled resources
 * val provider = AppletProviders.filesystem(
 *     root = Paths.get("/srv/applets"),
 *     fallbackToClasspath = true
 * )
 *
 * // Future: ZIP bundle
 * val provider = AppletProviders.zip(Paths.get("applet.zip"))
 * ```
 */
object AppletProviders {

    /**
     * Provider that reads from the JVM classpath.
     * The `:applet-provider` JAR ships a full copy of the default applet bundle,
     * so this works out of the box with no configuration.
     *
     * @param classLoader ClassLoader to use; defaults to the module's own classloader.
     * @param fallback    Optional secondary provider consulted when a resource is not
     *                    found on the classpath (e.g. a filesystem override directory).
     */
    fun classpath(
        classLoader: ClassLoader = AppletProviders::class.java.classLoader
            ?: ClassLoader.getSystemClassLoader(),
        fallback: AppletProvider? = null
    ): AppletProvider = ClasspathAppletProvider(classLoader, fallback)

    /**
     * Provider that reads from the local filesystem.
     *
     * @param root            Primary search root; defaults to the JVM working directory.
     * @param fallbackRoots   Additional roots tried in order when a path is not found
     *                        under [root].
     * @param fallbackToClasspath If true, a [ClasspathAppletProvider] is appended as the
     *                        last resort, giving access to bundled defaults.
     */
    fun filesystem(
        root: Path = Paths.get("").toAbsolutePath(),
        vararg fallbackRoots: Path,
        fallbackToClasspath: Boolean = false
    ): AppletProvider {
        val fs = FileSystemAppletProvider(root, fallbackRoots.toList())
        return if (fallbackToClasspath) {
            // Wrap: filesystem first, then classpath for any missing resources
            ChainedAppletProvider(fs, classpath())
        } else {
            fs
        }
    }

    /**
     * Provider that reads from a ZIP archive.
     * **Not yet implemented** — will throw [UnsupportedOperationException] at runtime.
     */
    fun zip(zipPath: Path): AppletProvider = ZipAppletProvider(zipPath)
}

/**
 * Internal chain: try [primary] first; if it throws, try [secondary].
 */
internal class ChainedAppletProvider(
    private val primary: AppletProvider,
    private val secondary: AppletProvider
) : AppletProvider {
    override fun createLoader(): com.damn.aisuper.runtime.AppletResourceLoader {
        val a = primary.createLoader()
        val b = secondary.createLoader()
        return com.damn.aisuper.runtime.AppletResourceLoader { path ->
            try {
                a.readBytes(path)
            } catch (_: Exception) {
                b.readBytes(path)
            }
        }
    }
}

