package com.damn.aisuper.applet

import com.damn.aisuper.runtime.AppletManifest
import kotlinx.serialization.json.Json

enum class AppletSourceKind {
    DIRECTORY,
    ZIP
}

data class AppletEntry(
    val id: String,
    val name: String,
    val entryFeature: String,
    val featureCount: Int,
    val sourceKind: AppletSourceKind,
    val sourceRef: String,
    val entryPath: String = "applet.json",
    val displayPath: String,
    val iconPath: String? = null
)

data class ResolvedApplet(
    val entry: AppletEntry,
    val provider: AppletProvider
)

private val catalogJson = Json { ignoreUnknownKeys = true }

fun parseAppletManifestBytes(bytes: ByteArray): AppletManifest =
    catalogJson.decodeFromString<AppletManifest>(bytes.decodeToString())
