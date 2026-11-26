package com.damn.aisuper.applet

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

/**
 * Discovers, configures, and resolves applets from directories, manifest files, and `.zip` archives on JVM.
 */
object JvmAppletScanner {
    private const val PREF_DEFAULT_APPLETS_DIR = "default_applets_dir"
    private val prefsLock = Any()

    private fun hostPrefsFile(): Path =
        Paths.get(System.getProperty("user.home"), ".aisuper", "aisuper_host.properties")

    fun defaultAppletsDirectory(): Path =
        Paths.get(System.getProperty("user.home"), ".aisuper", "applets").toAbsolutePath().normalize()

    fun getConfiguredAppletsDirectory(): Path = synchronized(prefsLock) {
        val file = hostPrefsFile()
        if (Files.isRegularFile(file)) {
            val props = Properties()
            runCatching {
                Files.newInputStream(file).use { props.load(it) }
            }
            val configured = props.getProperty(PREF_DEFAULT_APPLETS_DIR)?.trim()?.takeIf { it.isNotEmpty() }
            if (configured != null) {
                return Paths.get(configured).toAbsolutePath().normalize()
            }
        }
        val defaultDir = defaultAppletsDirectory()
        runCatching { Files.createDirectories(defaultDir) }
        defaultDir
    }

    fun setConfiguredAppletsDirectory(path: Path) {
        synchronized(prefsLock) {
            val normalized = path.toAbsolutePath().normalize()
            val file = hostPrefsFile()
            Files.createDirectories(file.parent)
            val props = Properties()
            if (Files.isRegularFile(file)) {
                runCatching { Files.newInputStream(file).use { props.load(it) } }
            }
            props.setProperty(PREF_DEFAULT_APPLETS_DIR, normalized.toString())
            Files.newOutputStream(file).use { props.store(it, "AISuper host preferences") }
        }
    }

    /**
     * Resolves an applet from a directory, `.zip` archive, or direct `applet.json` file.
     */
    fun resolveAppletPath(path: Path): ResolvedApplet {
        val normalized = path.toAbsolutePath().normalize()
        require(normalized.exists()) { "Applet path does not exist: $normalized" }

        if (normalized.isRegularFile()) {
            if (normalized.extension.equals("zip", ignoreCase = true)) {
                val loc = inspectZipManifest(normalized)
                    ?: throw IllegalArgumentException("Could not find 'applet.json' inside ZIP archive '$normalized'.")
                val manifest = parseAppletManifestBytes(loc.manifestBytes)
                val provider = AppletProviders.zip(normalized, loc.rootPrefix)
                val entry = AppletEntry(
                    id = manifest.id,
                    name = manifest.name,
                    entryFeature = manifest.entryFeature,
                    featureCount = manifest.features.size,
                    sourceKind = AppletSourceKind.ZIP,
                    sourceRef = normalized.toString(),
                    entryPath = loc.entryPath,
                    displayPath = normalized.name,
                    iconPath = manifest.icon
                )
                return ResolvedApplet(entry, provider)
            }

            if (normalized.extension.equals("json", ignoreCase = true)) {
                val parent = normalized.parent
                    ?: throw IllegalArgumentException("Invalid manifest path: $normalized")
                val (rootDir, entryPath) = if (parent.fileName?.toString().equals("files", ignoreCase = true) && parent.parent != null) {
                    parent.parent to "files/${normalized.fileName}"
                } else {
                    parent to normalized.fileName.toString()
                }
                val manifest = parseAppletManifestBytes(Files.readAllBytes(normalized))
                val provider = AppletProviders.filesystem(rootDir)
                val entry = AppletEntry(
                    id = manifest.id,
                    name = manifest.name,
                    entryFeature = manifest.entryFeature,
                    featureCount = manifest.features.size,
                    sourceKind = AppletSourceKind.DIRECTORY,
                    sourceRef = rootDir.toString(),
                    entryPath = entryPath,
                    displayPath = rootDir.toString(),
                    iconPath = manifest.icon
                )
                return ResolvedApplet(entry, provider)
            }

            throw IllegalArgumentException("Unsupported applet file '$normalized'. Expected a folder, .zip, or applet.json.")
        }

        if (normalized.isDirectory()) {
            val (rootDir, entryPath, manifestPath) = findManifestInDirectory(normalized)
                ?: throw IllegalArgumentException("Could not find 'applet.json' inside directory '$normalized'.")
            val manifest = parseAppletManifestBytes(Files.readAllBytes(manifestPath))
            val provider = AppletProviders.filesystem(rootDir)
            val entry = AppletEntry(
                id = manifest.id,
                name = manifest.name,
                entryFeature = manifest.entryFeature,
                featureCount = manifest.features.size,
                sourceKind = AppletSourceKind.DIRECTORY,
                sourceRef = rootDir.toString(),
                entryPath = entryPath,
                displayPath = normalized.name.ifEmpty { normalized.toString() },
                iconPath = manifest.icon
            )
            return ResolvedApplet(entry, provider)
        }

        throw IllegalArgumentException("Unsupported applet path: $normalized")
    }

    /**
     * Scans immediate children of [directory] for folder applets and `.zip` applets.
     */
    fun scanDirectory(directory: Path): List<AppletEntry> {
        if (!Files.isDirectory(directory)) return emptyList()
        val results = mutableListOf<AppletEntry>()
        Files.newDirectoryStream(directory).use { stream ->
            for (child in stream) {
                val isCandidate = child.isDirectory() ||
                    (child.isRegularFile() && child.extension.equals("zip", ignoreCase = true))
                if (!isCandidate) continue
                runCatching {
                    resolveAppletPath(child).entry
                }.onSuccess { entry ->
                    results.add(entry)
                }
            }
        }
        return results.sortedBy { it.name.lowercase() }
    }

    private fun findManifestInDirectory(dir: Path): Triple<Path, String, Path>? {
        val direct = dir.resolve("applet.json")
        if (Files.isRegularFile(direct)) {
            return Triple(dir, "applet.json", direct)
        }
        val nestedFiles = dir.resolve("files").resolve("applet.json")
        if (Files.isRegularFile(nestedFiles)) {
            return Triple(dir, "files/applet.json", nestedFiles)
        }
        Files.newDirectoryStream(dir).use { stream ->
            val subDirs = stream.filter { it.isDirectory() }.sortedBy { it.fileName.toString().lowercase() }
            for (sub in subDirs) {
                val subManifest = sub.resolve("applet.json")
                if (Files.isRegularFile(subManifest)) {
                    return Triple(sub, "applet.json", subManifest)
                }
            }
        }
        return null
    }
}
