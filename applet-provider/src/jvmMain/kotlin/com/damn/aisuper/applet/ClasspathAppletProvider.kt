package com.damn.aisuper.applet

import com.damn.aisuper.runtime.AppletResourceLoader

/**
 * [AppletProvider] that loads applet artifacts from the JVM classpath.
 *
 * By default this uses the classloader that loaded [ClasspathAppletProvider],
 * which means any resources bundled inside this module's JAR (or any JAR on
 * the runtime classpath) are accessible.  The `:applet-provider` module ships
 * a full copy of the default applet bundle under `files/` in its JAR, so
 * callers can obtain a working loader with zero configuration:
 *
 * ```kotlin
 * val provider = ClasspathAppletProvider()
 * val applet = Applet(engineFactory = { … }, resourceLoader = provider.createLoader())
 * applet.loadApplet("files/applet.json")
 * ```
 *
 * If a resource is not found on the default classpath the provider optionally
 * delegates to a [fallback] (e.g. a [FileSystemAppletProvider]) so that
 * development overrides still work.
 */
class ClasspathAppletProvider(
    private val classLoader: ClassLoader = ClasspathAppletProvider::class.java.classLoader
        ?: ClassLoader.getSystemClassLoader(),
    private val fallback: AppletProvider? = null
) : AppletProvider {

    override fun createLoader(): AppletResourceLoader {
        val fallbackLoader = fallback?.createLoader()
        return AppletResourceLoader { path ->
            val stream = classLoader.getResourceAsStream(path)
            if (stream != null) {
                stream.use { it.readBytes() }
            } else {
                fallbackLoader?.readBytes(path)
                    ?: error("Applet resource not found on classpath or fallback: $path")
            }
        }
    }
}

