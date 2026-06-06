package com.damn.aisuper.applet

import com.damn.aisuper.runtime.AppletResourceLoader
import java.nio.file.Path

/**
 * [AppletProvider] that loads applet artifacts from a ZIP archive.
 *
 * Each entry in the archive is accessible by its internal path, e.g.
 * `files/applet.json` maps to the `files/applet.json` entry inside the ZIP.
 *
 * This implementation is a **stub** — ZIP reading will be wired up in a
 * follow-up once the module API is stable.  Calling [createLoader] currently
 * throws [UnsupportedOperationException] so callers can fail fast during
 * development.
 */
class ZipAppletProvider(
    @Suppress("unused") val zipPath: Path
) : AppletProvider {

    override fun createLoader(): AppletResourceLoader =
        AppletResourceLoader { path ->
            // TODO: open zipPath as a ZipFile, read the entry at `path`
            throw UnsupportedOperationException(
                "ZipAppletProvider is not yet implemented. " +
                    "Requested path: $path  ZIP: $zipPath"
            )
        }
}

