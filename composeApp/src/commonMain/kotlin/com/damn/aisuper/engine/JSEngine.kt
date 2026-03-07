package com.damn.aisuper.engine

import io.github.alexzhirkevich.keight.Callable
import io.github.alexzhirkevich.keight.JSEngine
import io.github.alexzhirkevich.keight.JSRuntime
import io.github.alexzhirkevich.keight.js.js
import io.github.alexzhirkevich.keight.set
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

interface AppJSEngine {
    suspend fun execute(script: String, functionName: String, args: List<String>): String
    suspend fun registerFunction(name: String, callback: (List<String>) -> String)
    suspend fun registerSuspendFunction(name: String, callback: suspend (List<String>) -> String)
    fun close()
}

class KeightJSEngine : AppJSEngine {

    private val runtime = JSRuntime(Dispatchers.Default)
    private val engine = JSEngine(runtime)
    private var loadedScript: String? = null

    override suspend fun execute(
        script: String,
        functionName: String,
        args: List<String>
    ): String {
        try {
            // 1. Evaluate the script definition into the context only if changed
            if (script != loadedScript) {
                engine.evaluate(script)
                loadedScript = script
            }

            if (functionName.isEmpty()) return ""

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

    override suspend fun registerFunction(
        name: String,
        callback: (List<String>) -> String
    ) {
        runtime.set(name.js, Callable { args ->
            val stringArgs = args.map { it.toString() }
            val result = callback(stringArgs)
            result.js
        })
    }

    override suspend fun registerSuspendFunction(
        name: String,
        callback: suspend (List<String>) -> String
    ) {
        runtime.set(name.js, Callable { args ->
            val deferred = runtime.async {
                val stringArgs = args.map { it.toString() }
                val result = callback(stringArgs)
                result.js
            }
            deferred.js
        })
    }

    override fun close() {
        // Keight runtime might need cleanup if available, but for now just let GC handle it
    }
}
