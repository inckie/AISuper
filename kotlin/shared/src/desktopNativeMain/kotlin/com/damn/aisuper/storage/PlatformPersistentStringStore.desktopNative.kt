package com.damn.aisuper.storage

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.posix.FILE
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.fwrite
import platform.posix.getenv

internal expect fun ensureStorageDirectory(path: String): Boolean

@OptIn(ExperimentalForeignApi::class)
internal actual class PlatformPersistentStringStore actual constructor(
    private val namespace: String
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val filePath: String? = resolveFilePath(namespace)
    private var cache: MutableMap<String, String>? = null

    private fun resolveFilePath(ns: String): String? {
        val base = getenv("LOCALAPPDATA")?.toKString()?.takeIf { it.isNotBlank() }
            ?: getenv("XDG_DATA_HOME")?.toKString()?.takeIf { it.isNotBlank() }
            ?: getenv("HOME")?.toKString()?.takeIf { it.isNotBlank() }?.let { "$it/.local/share" }
            ?: return null
        val dir = "${base.replace('\\', '/')}/aisuper"
        if (!ensureStorageDirectory(dir)) {
            return null
        }
        val safeNs = ns.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return "$dir/$safeNs.json"
    }

    private fun loadLocked(): MutableMap<String, String> {
        cache?.let { return it }
        val map = mutableMapOf<String, String>()
        val path = filePath
        if (path != null) {
            val f = fopen(path, "rb")
            if (f != null) {
                try {
                    fseek(f, 0, SEEK_END)
                    val size = ftell(f).toInt()
                    fseek(f, 0, SEEK_SET)
                    if (size > 0) {
                        val bytes = ByteArray(size)
                        val read = bytes.usePinned { pinned ->
                            fread(pinned.addressOf(0), 1u, size.toULong(), f).toInt()
                        }
                        if (read > 0) {
                            val text = bytes.decodeToString(0, read)
                            val decoded = json.decodeFromString<Map<String, String>>(text)
                            map.putAll(decoded)
                        }
                    }
                } catch (_: Throwable) {
                    // Corrupted or unreadable storage file; start clean
                } finally {
                    fclose(f)
                }
            }
        }
        cache = map
        return map
    }

    private fun saveLocked(map: Map<String, String>) {
        val path = filePath ?: return
        val bytes = json.encodeToString(map).encodeToByteArray()
        val f: kotlinx.cinterop.CPointer<FILE>? = fopen(path, "wb")
        if (f != null) {
            try {
                if (bytes.isNotEmpty()) {
                    bytes.usePinned { pinned ->
                        fwrite(pinned.addressOf(0), 1u, bytes.size.toULong(), f)
                    }
                }
            } finally {
                fclose(f)
            }
        }
    }

    actual suspend fun get(key: String): String? = mutex.withLock {
        loadLocked()[key]
    }

    actual suspend fun put(key: String, value: String) = mutex.withLock {
        val map = loadLocked()
        map[key] = value
        saveLocked(map)
    }

    actual suspend fun remove(key: String) = mutex.withLock {
        val map = loadLocked()
        if (map.remove(key) != null) {
            saveLocked(map)
        }
    }

    actual suspend fun keys(): List<String> = mutex.withLock {
        loadLocked().keys.toList()
    }
}
