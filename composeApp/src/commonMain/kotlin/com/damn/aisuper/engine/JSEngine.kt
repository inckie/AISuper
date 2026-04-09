package com.damn.aisuper.engine

import kotlinx.serialization.json.JsonElement

interface AppJSEngine {
    suspend fun execute(script: String, functionName: String, args: List<JsonElement> = emptyList()): JsonElement
    suspend fun registerFunction(name: String, callback: (List<JsonElement>) -> JsonElement)
    suspend fun registerSuspendFunction(name: String, callback: suspend (List<JsonElement>) -> JsonElement)
    fun close()
}
