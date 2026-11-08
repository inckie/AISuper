package com.damn.aisuper.applet

import com.damn.aisuper.runtime.AppletResourceLoader
import java.io.File
import java.io.FileNotFoundException
import java.util.zip.ZipFile

class AndroidZipAppletProvider(
    private val zipFile: File
) : AppletProvider {

    override fun createLoader(): AppletResourceLoader =
        AppletResourceLoader { path ->
            ZipFile(zipFile).use { zip ->
                val entryPath = path.removePrefix("/")
                val entry = zip.getEntry(entryPath)
                    ?: throw FileNotFoundException("Entry '$entryPath' not found in zip '${zipFile.absolutePath}'")

                zip.getInputStream(entry).use { it.readBytes() }
            }
        }
}
