package com.damn.aisuper.applet

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
sealed interface AndroidAppletRef {
    @Serializable
    data class LocalDirectory(
        val path: String,
        val entryPath: String = "applet.json"
    ) : AndroidAppletRef

    @Serializable
    data class LocalZip(
        val path: String,
        val entryPath: String = "applet.json",
        val rootPrefix: String = ""
    ) : AndroidAppletRef

    @Serializable
    data class SafDirectory(
        val treeUri: String,
        val documentId: String,
        val entryPath: String = "applet.json"
    ) : AndroidAppletRef

    @Serializable
    data class SafZip(
        val documentUri: String,
        val entryPath: String = "applet.json",
        val rootPrefix: String = ""
    ) : AndroidAppletRef
}

private val refJson = Json { ignoreUnknownKeys = true }

fun AndroidAppletRef.encodeToJson(): String = refJson.encodeToString(this)

fun decodeAndroidAppletRef(raw: String): AndroidAppletRef =
    refJson.decodeFromString<AndroidAppletRef>(raw)
