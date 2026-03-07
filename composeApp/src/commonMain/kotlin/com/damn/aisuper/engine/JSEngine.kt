package com.damn.aisuper.engine

import io.github.alexzhirkevich.keight.JSEngine
import io.github.alexzhirkevich.keight.JSRuntime
import kotlinx.coroutines.Dispatchers

interface AppJSEngine {
    suspend fun execute(script: String, functionName: String, args: List<String>): String
    fun close()
}

class KeightJSEngine : AppJSEngine {

    private val runtime = JSRuntime(Dispatchers.Default)
    private val engine = JSEngine(runtime)

    override suspend fun execute(script: String, functionName: String, args: List<String>): String {
        try {
            // 1. Evaluate the script definition into the context
            engine.evaluate(script)

            // 2. Construct the function call
            // Note: Arguments need to be properly escaped in a real app
            val argsString = args.joinToString(",") { "'$it'" }
            val callString = "$functionName($argsString)"

            // 3. Evaluate the function call
            val result = engine.evaluate(callString)

            return result.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            return "Error: ${e.message}"
        }
    }

    override fun close() {
        // Keight runtime might need cleanup if available, but for now just let GC handle it
    }
}
