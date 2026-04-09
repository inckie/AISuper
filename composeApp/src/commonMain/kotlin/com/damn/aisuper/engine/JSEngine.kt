package com.damn.aisuper.engine

interface AppJSEngine {
    suspend fun execute(script: String, functionName: String, args: List<String>): String
    suspend fun registerFunction(name: String, callback: (List<String>) -> String)
    suspend fun registerSuspendFunction(name: String, callback: suspend (List<String>) -> String)
    fun close()
}
