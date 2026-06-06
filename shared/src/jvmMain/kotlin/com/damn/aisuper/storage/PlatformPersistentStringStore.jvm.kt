package com.damn.aisuper.storage

import java.io.File
import java.util.Properties

internal actual class PlatformPersistentStringStore actual constructor(namespace: String) {
    private val lock = Any()
    private val file: File = File(System.getProperty("user.home"), ".aisuper/$namespace.properties")

    init {
        file.parentFile?.mkdirs()
        if (!file.exists()) {
            file.createNewFile()
        }
    }

    private fun loadProperties(): Properties {
        val props = Properties()
        if (file.length() > 0L) {
            file.inputStream().use { props.load(it) }
        }
        return props
    }

    private fun saveProperties(props: Properties) {
        file.outputStream().use { output ->
            props.store(output, "AISuper persistent storage")
        }
    }

    actual suspend fun get(key: String): String? = synchronized(lock) {
        loadProperties().getProperty(key)
    }

    actual suspend fun put(key: String, value: String) {
        synchronized(lock) {
            val props = loadProperties()
            props.setProperty(key, value)
            saveProperties(props)
        }
    }

    actual suspend fun remove(key: String) {
        synchronized(lock) {
            val props = loadProperties()
            props.remove(key)
            saveProperties(props)
        }
    }

    actual suspend fun keys(): List<String> = synchronized(lock) {
        loadProperties().stringPropertyNames().toList()
    }
}

