package com.damn.aisuper.applet

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

object AndroidAppletScanner {
    private const val PREFS_NAME = "aisuper_host_prefs"
    private const val KEY_DEFAULT_TREE_URI = "default_applets_tree_uri"

    private data class SafChild(
        val documentId: String,
        val displayName: String,
        val isDirectory: Boolean
    )

    fun getDefaultLocalAppletsDir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "applets").apply { mkdirs() }

    fun getDefaultDirectoryTreeUri(context: Context): Uri? {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DEFAULT_TREE_URI, null)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val uri = Uri.parse(raw)
        val persisted = context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
        return if (persisted) uri else null
    }

    fun getDefaultDirectoryDisplay(context: Context): String {
        val treeUri = getDefaultDirectoryTreeUri(context) ?: return "App Storage / applets"
        return runCatching {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            when {
                docId.startsWith("primary:") -> "Internal Storage/" + docId.removePrefix("primary:")
                docId.contains(':') -> docId.replace(':', '/')
                else -> docId
            }
        }.getOrElse { treeUri.toString() }
    }

    fun setDefaultDirectoryTreeUri(context: Context, treeUri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DEFAULT_TREE_URI, treeUri.toString())
            .apply()
    }

    fun clearDefaultDirectoryTreeUri(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_DEFAULT_TREE_URI)
            .apply()
    }

    fun scanDefaultDirectory(context: Context): List<AppletEntry> {
        val treeUri = getDefaultDirectoryTreeUri(context)
        if (treeUri != null) {
            val safEntries = runCatching { scanSafTree(context, treeUri) }.getOrNull()
            if (safEntries != null) return safEntries
        }
        return scanLocalDirectory(getDefaultLocalAppletsDir(context))
    }

    fun resolveRef(context: Context, ref: AndroidAppletRef): ResolvedApplet = when (ref) {
        is AndroidAppletRef.LocalDirectory -> {
            val dir = File(ref.path)
            if (!dir.isDirectory) throw FileNotFoundException("Applet folder not found: ${ref.path}")
            val provider = AndroidLocalDirectoryAppletProvider(dir)
            val manifest = parseAppletManifestBytes(File(dir, ref.entryPath).readBytes())
            ResolvedApplet(
                entry = AppletEntry(
                    id = manifest.id,
                    name = manifest.name,
                    entryFeature = manifest.entryFeature,
                    featureCount = manifest.features.size,
                    sourceKind = AppletSourceKind.DIRECTORY,
                    sourceRef = ref.encodeToJson(),
                    entryPath = ref.entryPath,
                    displayPath = dir.name,
                    iconPath = manifest.icon
                ),
                provider = provider
            )
        }

        is AndroidAppletRef.LocalZip -> {
            val file = File(ref.path)
            if (!file.isFile) throw FileNotFoundException("Applet ZIP not found: ${ref.path}")
            val provider = AndroidZipAppletProvider(file, ref.rootPrefix)
            val manifestBytes = runBlockingRead(provider, ref.entryPath)
            val manifest = parseAppletManifestBytes(manifestBytes)
            ResolvedApplet(
                entry = AppletEntry(
                    id = manifest.id,
                    name = manifest.name,
                    entryFeature = manifest.entryFeature,
                    featureCount = manifest.features.size,
                    sourceKind = AppletSourceKind.ZIP,
                    sourceRef = ref.encodeToJson(),
                    entryPath = ref.entryPath,
                    displayPath = file.name,
                    iconPath = manifest.icon
                ),
                provider = provider
            )
        }

        is AndroidAppletRef.SafDirectory -> {
            val treeUri = Uri.parse(ref.treeUri)
            val provider = AndroidSafDirectoryAppletProvider(context.applicationContext, treeUri, ref.documentId)
            val manifestBytes = runBlockingRead(provider, ref.entryPath)
            val manifest = parseAppletManifestBytes(manifestBytes)
            val label = ref.documentId.substringAfterLast('/').substringAfterLast(':')
            ResolvedApplet(
                entry = AppletEntry(
                    id = manifest.id,
                    name = manifest.name,
                    entryFeature = manifest.entryFeature,
                    featureCount = manifest.features.size,
                    sourceKind = AppletSourceKind.DIRECTORY,
                    sourceRef = ref.encodeToJson(),
                    entryPath = ref.entryPath,
                    displayPath = label.ifBlank { "Folder" },
                    iconPath = manifest.icon
                ),
                provider = provider
            )
        }

        is AndroidAppletRef.SafZip -> {
            val uri = Uri.parse(ref.documentUri)
            val provider = AndroidUriZipAppletProvider(context.applicationContext, uri, ref.rootPrefix)
            val manifestBytes = runBlockingRead(provider, ref.entryPath)
            val manifest = parseAppletManifestBytes(manifestBytes)
            val name = queryDisplayName(context, uri) ?: "applet.zip"
            ResolvedApplet(
                entry = AppletEntry(
                    id = manifest.id,
                    name = manifest.name,
                    entryFeature = manifest.entryFeature,
                    featureCount = manifest.features.size,
                    sourceKind = AppletSourceKind.ZIP,
                    sourceRef = ref.encodeToJson(),
                    entryPath = ref.entryPath,
                    displayPath = name,
                    iconPath = manifest.icon
                ),
                provider = provider
            )
        }
    }

    fun resolvePickedDocumentUri(context: Context, uri: Uri): ResolvedApplet {
        val hasPersisted = try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            true
        } catch (_: SecurityException) {
            context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
        }

        val displayName = queryDisplayName(context, uri) ?: "applet.zip"
        val tempFile = File(context.cacheDir, "picked_${uri.toString().hashCode().toUInt()}.zip")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        } ?: throw FileNotFoundException("Unable to read selected file: $uri")

        val loc = inspectAndroidZipFile(tempFile)
            ?: throw IllegalArgumentException("Could not find 'applet.json' inside '$displayName'.")
        val manifest = parseAppletManifestBytes(loc.manifestBytes)

        return if (hasPersisted) {
            val ref = AndroidAppletRef.SafZip(
                documentUri = uri.toString(),
                entryPath = loc.entryPath,
                rootPrefix = loc.rootPrefix
            )
            ResolvedApplet(
                entry = AppletEntry(
                    id = manifest.id,
                    name = manifest.name,
                    entryFeature = manifest.entryFeature,
                    featureCount = manifest.features.size,
                    sourceKind = AppletSourceKind.ZIP,
                    sourceRef = ref.encodeToJson(),
                    entryPath = loc.entryPath,
                    displayPath = displayName,
                    iconPath = manifest.icon
                ),
                provider = AndroidUriZipAppletProvider(context.applicationContext, uri, loc.rootPrefix)
            )
        } else {
            val importedDir = File(context.filesDir, "imported_applets").apply { mkdirs() }
            val importedZip = File(importedDir, "${manifest.id.hashCode().toUInt()}_${displayName.hashCode().toUInt()}.zip")
            tempFile.copyTo(importedZip, overwrite = true)
            val ref = AndroidAppletRef.LocalZip(
                path = importedZip.absolutePath,
                entryPath = loc.entryPath,
                rootPrefix = loc.rootPrefix
            )
            ResolvedApplet(
                entry = AppletEntry(
                    id = manifest.id,
                    name = manifest.name,
                    entryFeature = manifest.entryFeature,
                    featureCount = manifest.features.size,
                    sourceKind = AppletSourceKind.ZIP,
                    sourceRef = ref.encodeToJson(),
                    entryPath = loc.entryPath,
                    displayPath = displayName,
                    iconPath = manifest.icon
                ),
                provider = AndroidZipAppletProvider(importedZip, loc.rootPrefix)
            )
        }
    }

    fun resolvePickedFolderTreeUri(context: Context, treeUri: Uri): ResolvedApplet {
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
        }

        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootLabel = rootDocId.substringAfterLast('/').substringAfterLast(':')
        val resolved = resolveSafDirectory(context, treeUri, rootDocId, rootLabel)
            ?: throw IllegalArgumentException("Could not find 'applet.json' inside selected folder.")
        val ref = decodeAndroidAppletRef(resolved.sourceRef)
        return resolveRef(context, ref)
    }

    private fun scanLocalDirectory(directory: File): List<AppletEntry> {
        if (!directory.isDirectory) return emptyList()
        val entries = mutableListOf<AppletEntry>()
        directory.listFiles()?.forEach { child ->
            runCatching {
                when {
                    child.isDirectory -> resolveLocalDirectory(child)
                    child.isFile && child.extension.equals("zip", ignoreCase = true) -> resolveLocalZip(child)
                    else -> null
                }
            }.getOrNull()?.let(entries::add)
        }
        return entries.sortedBy { it.name.lowercase() }
    }

    private fun resolveLocalDirectory(dir: File): AppletEntry? {
        val candidates = buildList {
            add(dir to "applet.json")
            add(dir to "files/applet.json")
            dir.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name.lowercase() }?.forEach { sub ->
                add(sub to "applet.json")
            }
        }
        for ((root, entryPath) in candidates) {
            val manifestFile = File(root, entryPath)
            if (manifestFile.isFile) {
                val manifest = parseAppletManifestBytes(manifestFile.readBytes())
                val ref = AndroidAppletRef.LocalDirectory(
                    path = root.absolutePath,
                    entryPath = entryPath
                )
                return AppletEntry(
                    id = manifest.id,
                    name = manifest.name,
                    entryFeature = manifest.entryFeature,
                    featureCount = manifest.features.size,
                    sourceKind = AppletSourceKind.DIRECTORY,
                    sourceRef = ref.encodeToJson(),
                    entryPath = entryPath,
                    displayPath = dir.name,
                    iconPath = manifest.icon
                )
            }
        }
        return null
    }

    private fun resolveLocalZip(file: File): AppletEntry? {
        val loc = inspectAndroidZipFile(file) ?: return null
        val manifest = parseAppletManifestBytes(loc.manifestBytes)
        val ref = AndroidAppletRef.LocalZip(
            path = file.absolutePath,
            entryPath = loc.entryPath,
            rootPrefix = loc.rootPrefix
        )
        return AppletEntry(
            id = manifest.id,
            name = manifest.name,
            entryFeature = manifest.entryFeature,
            featureCount = manifest.features.size,
            sourceKind = AppletSourceKind.ZIP,
            sourceRef = ref.encodeToJson(),
            entryPath = loc.entryPath,
            displayPath = file.name,
            iconPath = manifest.icon
        )
    }

    private fun scanSafTree(context: Context, treeUri: Uri): List<AppletEntry> {
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val children = listSafChildren(context, treeUri, rootDocId)
        val entries = mutableListOf<AppletEntry>()
        for (child in children) {
            runCatching {
                when {
                    child.isDirectory -> resolveSafDirectory(context, treeUri, child.documentId, child.displayName)
                    child.displayName.endsWith(".zip", ignoreCase = true) -> {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, child.documentId)
                        val loc = context.contentResolver.openInputStream(docUri)?.use { inspectZipInputStream(it) }
                            ?: return@runCatching null
                        val manifest = parseAppletManifestBytes(loc.manifestBytes)
                        val ref = AndroidAppletRef.SafZip(
                            documentUri = docUri.toString(),
                            entryPath = loc.entryPath,
                            rootPrefix = loc.rootPrefix
                        )
                        AppletEntry(
                            id = manifest.id,
                            name = manifest.name,
                            entryFeature = manifest.entryFeature,
                            featureCount = manifest.features.size,
                            sourceKind = AppletSourceKind.ZIP,
                            sourceRef = ref.encodeToJson(),
                            entryPath = loc.entryPath,
                            displayPath = child.displayName,
                            iconPath = manifest.icon
                        )
                    }
                    else -> null
                }
            }.getOrNull()?.let(entries::add)
        }
        return entries.sortedBy { it.name.lowercase() }
    }

    private fun resolveSafDirectory(
        context: Context,
        treeUri: Uri,
        dirDocId: String,
        displayLabel: String
    ): AppletEntry? {
        val children = listSafChildren(context, treeUri, dirDocId)
        children.firstOrNull { !it.isDirectory && it.displayName == "applet.json" }?.let { manifestChild ->
            val manifestUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, manifestChild.documentId)
            val bytes = context.contentResolver.openInputStream(manifestUri)?.use { it.readBytes() } ?: return null
            val manifest = parseAppletManifestBytes(bytes)
            val ref = AndroidAppletRef.SafDirectory(
                treeUri = treeUri.toString(),
                documentId = dirDocId,
                entryPath = "applet.json"
            )
            return AppletEntry(
                id = manifest.id,
                name = manifest.name,
                entryFeature = manifest.entryFeature,
                featureCount = manifest.features.size,
                sourceKind = AppletSourceKind.DIRECTORY,
                sourceRef = ref.encodeToJson(),
                entryPath = "applet.json",
                displayPath = displayLabel,
                iconPath = manifest.icon
            )
        }

        children.firstOrNull { it.isDirectory && it.displayName == "files" }?.let { filesChild ->
            val nested = listSafChildren(context, treeUri, filesChild.documentId)
            nested.firstOrNull { !it.isDirectory && it.displayName == "applet.json" }?.let { manifestChild ->
                val manifestUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, manifestChild.documentId)
                val bytes = context.contentResolver.openInputStream(manifestUri)?.use { it.readBytes() } ?: return null
                val manifest = parseAppletManifestBytes(bytes)
                val ref = AndroidAppletRef.SafDirectory(
                    treeUri = treeUri.toString(),
                    documentId = dirDocId,
                    entryPath = "files/applet.json"
                )
                return AppletEntry(
                    id = manifest.id,
                    name = manifest.name,
                    entryFeature = manifest.entryFeature,
                    featureCount = manifest.features.size,
                    sourceKind = AppletSourceKind.DIRECTORY,
                    sourceRef = ref.encodeToJson(),
                    entryPath = "files/applet.json",
                    displayPath = displayLabel,
                    iconPath = manifest.icon
                )
            }
        }

        for (sub in children.filter { it.isDirectory && it.displayName != "files" }) {
            val subChildren = listSafChildren(context, treeUri, sub.documentId)
            subChildren.firstOrNull { !it.isDirectory && it.displayName == "applet.json" }?.let { manifestChild ->
                val manifestUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, manifestChild.documentId)
                val bytes = context.contentResolver.openInputStream(manifestUri)?.use { it.readBytes() } ?: return null
                val manifest = parseAppletManifestBytes(bytes)
                val ref = AndroidAppletRef.SafDirectory(
                    treeUri = treeUri.toString(),
                    documentId = sub.documentId,
                    entryPath = "applet.json"
                )
                return AppletEntry(
                    id = manifest.id,
                    name = manifest.name,
                    entryFeature = manifest.entryFeature,
                    featureCount = manifest.features.size,
                    sourceKind = AppletSourceKind.DIRECTORY,
                    sourceRef = ref.encodeToJson(),
                    entryPath = "applet.json",
                    displayPath = displayLabel,
                    iconPath = manifest.icon
                )
            }
        }
        return null
    }

    private fun listSafChildren(context: Context, treeUri: Uri, dirDocId: String): List<SafChild> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val out = mutableListOf<SafChild>()
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val id = cursor.getString(idIdx) ?: continue
                val name = cursor.getString(nameIdx) ?: continue
                val mime = cursor.getString(mimeIdx) ?: ""
                out.add(SafChild(id, name, mime == DocumentsContract.Document.MIME_TYPE_DIR))
            }
        }
        return out
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx)
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun runBlockingRead(provider: AppletProvider, path: String): ByteArray =
        kotlinx.coroutines.runBlocking {
            provider.createLoader().readBytes(path)
        }
}
