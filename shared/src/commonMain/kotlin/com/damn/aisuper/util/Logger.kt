package com.damn.aisuper.util

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR, NONE
}

object Logger {
    var minLevel: LogLevel = LogLevel.DEBUG
    private val tagLevels = mutableMapOf<String, LogLevel>()

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
            println("[$prefix][$tagString] ${msg()}")
            throwable?.let {
                println(it.stackTraceToString())
            }
        }
    }
}
