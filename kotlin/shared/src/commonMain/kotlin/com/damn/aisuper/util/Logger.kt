package com.damn.aisuper.util

import kotlinx.serialization.Serializable

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR, NONE
}

interface LogSink {
    fun onLog(level: LogLevel, tag: String, message: String, throwable: Throwable?)
}

@Serializable
class LogEntry(
    val level: LogLevel,
    val tag: String,
    val message: String,
    val timestamp: Long,
    val throwable: String? = null
)

class LogBufferSink(private val maxEntries: Int = 1000) : LogSink {
    private val _entries = mutableListOf<LogEntry>()
    val entries: List<LogEntry> get() = _entries.toList()

    override fun onLog(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        if (_entries.size >= maxEntries) {
            _entries.removeAt(0)
        }
        _entries.add(
            LogEntry(
                level = level,
                tag = tag,
                message = message,
                timestamp = currentTimeMillis(),
                throwable = throwable?.stackTraceToString()
            )
        )
    }

    fun clear() = _entries.clear()
}

internal expect fun currentTimeMillis(): Long

object Logger {
    var minLevel: LogLevel = LogLevel.DEBUG
    private val tagLevels = mutableMapOf<String, LogLevel>()
    private val sinks = mutableListOf<LogSink>()

    fun addSink(sink: LogSink) = sinks.add(sink)
    fun removeSink(sink: LogSink) = sinks.remove(sink)

    fun setTagLevel(tag: String, level: LogLevel) {
        tagLevels[tag] = level
    }

    fun d(vararg tags: String, msg: () -> String) = log(LogLevel.DEBUG, tags, msg)
    fun i(vararg tags: String, msg: () -> String) = log(LogLevel.INFO, tags, msg)
    fun w(vararg tags: String, msg: () -> String) = log(LogLevel.WARN, tags, msg)
    fun e(vararg tags: String, throwable: Throwable? = null, msg: () -> String) = log(LogLevel.ERROR, tags, msg, throwable)

    private fun log(level: LogLevel, tags: Array<out String>, msg: () -> String, throwable: Throwable? = null) {
        if (level == LogLevel.NONE) return
        
        // Find the most specific (highest) min level among all tags
        val effectiveMin = if (tags.isEmpty()) {
            minLevel
        } else {
            tags.mapNotNull { tagLevels[it] }.maxByOrNull { it.ordinal } ?: minLevel
        }
        
        if (level.ordinal >= effectiveMin.ordinal) {
            val prefix = when (level) {
                LogLevel.DEBUG -> "D"
                LogLevel.INFO -> "I"
                LogLevel.WARN -> "W"
                LogLevel.ERROR -> "E"
                LogLevel.NONE -> ""
            }
            val tagString = if (tags.isEmpty()) "AISuper" else tags.joinToString(":")
            val message = msg()
            
            println("[$prefix][$tagString] $message")
            throwable?.let {
                println(it.stackTraceToString())
            }

            sinks.forEach { it.onLog(level, tagString, message, throwable) }
        }
    }
}
