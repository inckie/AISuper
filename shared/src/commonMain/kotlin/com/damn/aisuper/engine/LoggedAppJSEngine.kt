package com.damn.aisuper.engine

import com.damn.aisuper.util.Logger
import kotlinx.serialization.json.JsonElement

/**
 * Logging wrapper for JS engines. Keeps behavior unchanged, adds per-call tracing.
 */
class LoggedAppJSEngine(
    private val delegate: AppJSEngine,
    private val tag: String
) : AppJSEngine by delegate {

    override suspend fun loadScript(script: String) {
        Logger.i("JS", "Engine", tag) { "loadScript length=${script.length}" }
        delegate.loadScript(script)
        Logger.i("JS", "Engine", tag) { "loadScript ok" }
    }

    override suspend fun callFunction(functionName: String, args: List<JsonElement>): JsonElement {
        Logger.i("JS", "Engine", tag) { "call -> $functionName args=${safeArgs(args)}" }
        val result = delegate.callFunction(functionName, args)
        Logger.i("JS", "Engine", tag) { "call <- $functionName result=${safe(result)}" }
        return result
    }

    override suspend fun registerFunction(name: String, callback: (List<JsonElement>) -> JsonElement) {
        Logger.i("JS", "Engine", tag) { "registerFunction $name" }
        delegate.registerFunction(name) { args ->
            Logger.i("JS", "Engine", tag) { "callback(sync) -> $name args=${safeArgs(args)}" }
            try {
                val result = callback(args)
                Logger.i("JS", "Engine", tag) { "callback(sync) <- $name result=${safe(result)}" }
                result
            } catch (e: Exception) {
                Logger.e("JS", "Engine", tag, throwable = e) { "callback(sync) !! $name error=${e.message}" }
                throw e
            }
        }
    }

    override suspend fun registerSuspendFunction(name: String, callback: suspend (List<JsonElement>) -> JsonElement) {
        Logger.i("JS", "Engine", tag) { "registerSuspendFunction $name" }
        delegate.registerSuspendFunction(name) { args ->
            Logger.i("JS", "Engine", tag) { "callback(async) -> $name args=${safeArgs(args)}" }
            try {
                val result = callback(args)
                Logger.i("JS", "Engine", tag) { "callback(async) <- $name result=${safe(result)}" }
                result
            } catch (e: Exception) {
                Logger.e("JS", "Engine", tag, throwable = e) { "callback(async) !! $name error=${e.message}" }
                throw e
            }
        }
    }

    override fun close() {
        Logger.i("JS", "Engine", tag) { "close" }
        delegate.close()
    }

    private fun safeArgs(args: List<JsonElement>): String {
        return args.joinToString(prefix = "[", postfix = "]") { safe(it) }
    }

    private fun safe(value: Any?): String {
        return value?.toString()?.replace("\n", " ")?.take(220) ?: "null"
    }
}
