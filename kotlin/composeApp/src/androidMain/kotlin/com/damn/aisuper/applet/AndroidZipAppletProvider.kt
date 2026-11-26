package com.damn.aisuper.applet

import android.content.Context
import android.net.Uri
import com.damn.aisuper.runtime.AppletResourceLoader
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

class AndroidZipAppletProvider(
    private val zipFile: File,
    private val rootPrefix: String = ""
) : AppletProvider {

    override fun createLoader(): AppletResourceLoader =
        AppletResourceLoader { path ->
            ZipFile(zipFile).use { zip ->
                val cleanPath = path.removePrefix("/").replace('\\', '/')
                val entry = (if (rootPrefix.isNotEmpty()) zip.getEntry(rootPrefix + cleanPath) else null)
                    ?: zip.getEntry(cleanPath)
                    ?: throw FileNotFoundException("Entry '${rootPrefix + cleanPath}' not found in zip '${zipFile.absolutePath}'")

                zip.getInputStream(entry).use { it.readBytes() }
            }
        }
}

class AndroidLocalDirectoryAppletProvider(
    private val rootDir: File
) : AppletProvider {

    override fun createLoader(): AppletResourceLoader =
        AppletResourceLoader { path ->
            val cleanPath = path.removePrefix("/").replace('\\', '/')
            val target = File(rootDir, cleanPath).canonicalFile
            val rootCanonical = rootDir.canonicalFile
            if (!target.path.startsWith(rootCanonical.path)) {
                throw SecurityException("Path escapes applet root: $path")
            }
            if (!target.isFile) {
                throw FileNotFoundException("Applet resource not found: ${target.absolutePath}")
            }
            target.readBytes()
        }
}

class AndroidUriZipAppletProvider(
    private val context: Context,
    private val uri: Uri,
    private val rootPrefix: String = ""
) : AppletProvider {

    override fun createLoader(): AppletResourceLoader {
        val cacheDir = File(context.cacheDir, "applet_zips").apply { mkdirs() }
        val cacheFile = File(cacheDir, "${uri.toString().hashCode().toUInt()}.zip")
        val inputStream = context.contentResolver.openInputStream(uri)
        if (inputStream != null) {
            inputStream.use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } else if (!cacheFile.exists()) {
            throw FileNotFoundException("Unable to open ZIP URI: $uri")
        }
        return AndroidZipAppletProvider(cacheFile, rootPrefix).createLoader()
    }
}

data class AndroidZipManifestLocation(
    val rootPrefix: String,
    val entryPath: String,
    val manifestBytes: ByteArray
)

fun inspectAndroidZipFile(zipFile: File): AndroidZipManifestLocation? {
    ZipFile(zipFile).use { zip ->
        val entriesByName = LinkedHashMap<String, java.util.zip.ZipEntry>()
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (!entry.isDirectory) {
                entriesByName[entry.name.removePrefix("/").replace('\\', '/')] = entry
            }
        }

        fun read(entry: java.util.zip.ZipEntry): ByteArray =
            zip.getInputStream(entry).use { it.readBytes() }

        entriesByName["applet.json"]?.let {
            return AndroidZipManifestLocation("", "applet.json", read(it))
        }
        entriesByName["files/applet.json"]?.let {
            return AndroidZipManifestLocation("", "files/applet.json", read(it))
        }
        for ((name, entry) in entriesByName) {
            val parts = name.split('/')
            if (parts.size == 2 && parts[1] == "applet.json") {
                return AndroidZipManifestLocation("${parts[0]}/", "applet.json", read(entry))
            }
        }
        for ((name, entry) in entriesByName) {
            val parts = name.split('/')
            if (parts.size == 3 && parts[1] == "files" && parts[2] == "applet.json") {
                return AndroidZipManifestLocation("${parts[0]}/", "files/applet.json", read(entry))
            }
        }
        return null
    }
}

fun inspectZipInputStream(input: InputStream): AndroidZipManifestLocation? {
    var rootDirect: ByteArray? = null
    var rootFiles: ByteArray? = null
    var nestedDirect: Pair<String, ByteArray>? = null
    var nestedFiles: Pair<String, ByteArray>? = null

    ZipInputStream(input).use { zis ->
        while (true) {
            val entry = zis.nextEntry ?: break
            if (!entry.isDirectory) {
                val name = entry.name.removePrefix("/").replace('\\', '/')
                when {
                    name == "applet.json" -> {
                        rootDirect = zis.readBytes()
                        break
                    }
                    name == "files/applet.json" && rootFiles == null -> {
                        rootFiles = zis.readBytes()
                    }
                    else -> {
                        val parts = name.split('/')
                        if (parts.size == 2 && parts[1] == "applet.json" && nestedDirect == null) {
                            nestedDirect = "${parts[0]}/" to zis.readBytes()
                        } else if (parts.size == 3 && parts[1] == "files" && parts[2] == "applet.json" && nestedFiles == null) {
                            nestedFiles = "${parts[0]}/" to zis.readBytes()
                        }
                    }
                }
            }
            zis.closeEntry()
        }
    }

    rootDirect?.let { return AndroidZipManifestLocation("", "applet.json", it) }
    rootFiles?.let { return AndroidZipManifestLocation("", "files/applet.json", it) }
    nestedDirect?.let { return AndroidZipManifestLocation(it.first, "applet.json", it.second) }
    nestedFiles?.let { return AndroidZipManifestLocation(it.first, "files/applet.json", it.second) }
    return null
}
