package com.damn.aisuper.applet

import com.damn.aisuper.runtime.AppletResourceLoader
import java.io.FileNotFoundException
import java.nio.file.Path
import java.util.zip.ZipFile

/**
 * [AppletProvider] that loads applet artifacts from a ZIP archive.
 *
 * Each entry in the archive is accessible by its internal path, e.g.
 * `files/applet.json` maps to the `files/applet.json` entry inside the ZIP.
 */
class ZipAppletProvider(
    val zipPath: Path
) : AppletProvider {

    override fun createLoader(): AppletResourceLoader =
        AppletResourceLoader { path ->
            ZipFile(zipPath.toFile()).use { zipFile ->
                // Ensure the path does not have a leading slash
                val entryPath = path.removePrefix("/")
                val entry = zipFile.getEntry(entryPath)
                    ?: throw FileNotFoundException("Entry '$entryPath' not found in zip '$zipPath'")

                zipFile.getInputStream(entry).use { it.readBytes() }
            }
        }
}
