package com.damn.aisuper.applet

import com.damn.aisuper.runtime.AppletResourceLoader
import java.io.FileNotFoundException
import java.nio.file.Path
import java.util.zip.ZipFile

/**
 * [AppletProvider] that loads applet artifacts from a ZIP archive.
 *
 * Supports archives where `applet.json` is directly at the root, under `files/applet.json`,
 * or nested inside a single top-level wrapper folder via [rootPrefix].
 */
class ZipAppletProvider(
    val zipPath: Path,
    val rootPrefix: String = ""
) : AppletProvider {

    override fun createLoader(): AppletResourceLoader =
        AppletResourceLoader { path ->
            ZipFile(zipPath.toFile()).use { zipFile ->
                val cleanPath = path.removePrefix("/").replace('\\', '/')
                val entry = (if (rootPrefix.isNotEmpty()) zipFile.getEntry(rootPrefix + cleanPath) else null)
                    ?: zipFile.getEntry(cleanPath)
                    ?: throw FileNotFoundException("Entry '${rootPrefix + cleanPath}' not found in zip '$zipPath'")

                zipFile.getInputStream(entry).use { it.readBytes() }
            }
        }
}

data class ZipManifestLocation(
    val rootPrefix: String,
    val entryPath: String,
    val manifestBytes: ByteArray
)

/**
 * Inspects [zipPath] for a valid `applet.json` manifest at:
 * 1. `applet.json`
 * 2. `files/applet.json`
 * 3. `<topDir>/applet.json`
 * 4. `<topDir>/files/applet.json`
 */
fun inspectZipManifest(zipPath: Path): ZipManifestLocation? {
    ZipFile(zipPath.toFile()).use { zipFile ->
        val entriesByName = LinkedHashMap<String, java.util.zip.ZipEntry>()
        val entries = zipFile.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (!entry.isDirectory) {
                val normalized = entry.name.removePrefix("/").replace('\\', '/')
                entriesByName[normalized] = entry
            }
        }

        fun read(entry: java.util.zip.ZipEntry): ByteArray =
            zipFile.getInputStream(entry).use { it.readBytes() }

        entriesByName["applet.json"]?.let {
            return ZipManifestLocation(rootPrefix = "", entryPath = "applet.json", manifestBytes = read(it))
        }
        entriesByName["files/applet.json"]?.let {
            return ZipManifestLocation(rootPrefix = "", entryPath = "files/applet.json", manifestBytes = read(it))
        }

        for ((name, entry) in entriesByName) {
            val parts = name.split('/')
            if (parts.size == 2 && parts[1] == "applet.json") {
                return ZipManifestLocation(
                    rootPrefix = "${parts[0]}/",
                    entryPath = "applet.json",
                    manifestBytes = read(entry)
                )
            }
        }

        for ((name, entry) in entriesByName) {
            val parts = name.split('/')
            if (parts.size == 3 && parts[1] == "files" && parts[2] == "applet.json") {
                return ZipManifestLocation(
                    rootPrefix = "${parts[0]}/",
                    entryPath = "files/applet.json",
                    manifestBytes = read(entry)
                )
            }
        }

        return null
    }
}
