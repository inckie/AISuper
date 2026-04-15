package com.damn.aisuper.modules

import com.damn.aisuper.runtime.ModuleDefinition
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class AudioPlayerFeatureModule(
    playerNames: List<String>
) : FeatureModule, NativeCommandFeatureModule {
    private val module = AudioPlayerModule(playerNames)
    private val stateJobs = mutableListOf<Job>()
    private val subscriptions = mutableMapOf<String, MutableSet<String>>()

    private lateinit var context: FeatureModuleContext

    override suspend fun attach(context: FeatureModuleContext) {
        this.context = context

        registerFunctions(context)

        module.names().forEach { playerName ->
            val player = module.player(playerName) ?: return@forEach
            val job = context.scope.launch {
                player.state.collectLatest { state ->
                    publishState(playerName, state)
                    dispatchStateEvent(playerName, state)
                }
            }
            stateJobs.add(job)
        }
    }

    override fun detach() {
        stateJobs.forEach { it.cancel() }
        stateJobs.clear()
        module.release()
    }

    override fun handleCommand(target: String, command: String, args: List<JsonElement>): Boolean {
        val player = module.player(target) ?: return false
        when (command) {
            "load" -> {
                val url = args.firstOrNull()?.jsonPrimitiveContentOrNull() ?: return true
                player.load(url)
            }
            "play" -> player.play()
            "pause" -> player.pause()
            "stop" -> player.stop()
            "seek" -> {
                val positionMs = args.firstOrNull()?.jsonPrimitive?.longOrNull ?: return true
                player.seek(positionMs)
            }
            else -> return false
        }
        return true
    }

    private suspend fun registerFunctions(context: FeatureModuleContext) {
        context.registerFunction("getAudioPlayers") {
            buildJsonArray {
                module.names().forEach { add(JsonPrimitive(it)) }
            }
        }

        context.registerFunction("audioGetState") { args ->
            val playerName = args.stringAt(0) ?: return@registerFunction JsonNull
            val state = module.player(playerName)?.state?.value ?: return@registerFunction JsonNull
            stateToJson(playerName, state)
        }

        context.registerFunction("audioLoad") { args ->
            val playerName = args.stringAt(0) ?: return@registerFunction JsonNull
            val url = args.stringAt(1) ?: return@registerFunction JsonNull
            module.player(playerName)?.load(url)
            JsonNull
        }

        context.registerFunction("audioPlay") { args ->
            val playerName = args.stringAt(0) ?: return@registerFunction JsonNull
            module.player(playerName)?.play()
            JsonNull
        }

        context.registerFunction("audioPause") { args ->
            val playerName = args.stringAt(0) ?: return@registerFunction JsonNull
            module.player(playerName)?.pause()
            JsonNull
        }

        context.registerFunction("audioStop") { args ->
            val playerName = args.stringAt(0) ?: return@registerFunction JsonNull
            module.player(playerName)?.stop()
            JsonNull
        }

        context.registerFunction("audioSeek") { args ->
            val playerName = args.stringAt(0) ?: return@registerFunction JsonNull
            val positionMs = args.getOrNull(1)?.jsonPrimitive?.longOrNull ?: return@registerFunction JsonNull
            module.player(playerName)?.seek(positionMs)
            JsonNull
        }

        context.registerFunction("audioSubscribe") { args ->
            val playerName = args.stringAt(0) ?: return@registerFunction JsonNull
            val handler = args.stringAt(1) ?: return@registerFunction JsonNull
            val handlers = subscriptions.getOrPut(playerName) { mutableSetOf() }
            handlers.add(handler)
            JsonNull
        }

        context.registerFunction("audioUnsubscribe") { args ->
            val playerName = args.stringAt(0) ?: return@registerFunction JsonNull
            val handler = args.stringAt(1)
            if (handler == null) {
                subscriptions.remove(playerName)
            } else {
                subscriptions[playerName]?.remove(handler)
            }
            JsonNull
        }
    }

    private fun publishState(playerName: String, state: AudioPlayerState) {
        val prefix = "$playerName.media"
        context.updateValue("$prefix.sourceUrl", state.sourceUrl?.let(::JsonPrimitive) ?: JsonNull)
        context.updateValue("$prefix.state", JsonPrimitive(state.phase))
        context.updateValue("$prefix.positionMs", JsonPrimitive(state.positionMs))
        context.updateValue("$prefix.durationMs", state.durationMs?.let(::JsonPrimitive) ?: JsonNull)
        context.updateValue("$prefix.error", state.error?.let(::JsonPrimitive) ?: JsonNull)
    }

    private fun dispatchStateEvent(playerName: String, state: AudioPlayerState) {
        val handlers = subscriptions[playerName]?.toList().orEmpty()
        if (handlers.isEmpty()) return

        val payload = stateToJson(playerName, state)
        handlers.forEach { handler ->
            context.scope.launch {
                runCatching {
                    context.invokeScript(handler, listOf(payload))
                }
            }
        }
    }

    private fun stateToJson(playerName: String, state: AudioPlayerState): JsonObject {
        return buildJsonObject {
            put("player", JsonPrimitive(playerName))
            put("sourceUrl", state.sourceUrl?.let(::JsonPrimitive) ?: JsonNull)
            put("state", JsonPrimitive(state.phase))
            put("positionMs", JsonPrimitive(state.positionMs))
            put("durationMs", state.durationMs?.let(::JsonPrimitive) ?: JsonNull)
            put("error", state.error?.let(::JsonPrimitive) ?: JsonNull)
        }
    }
}

object AudioPlayerFeatureModuleFactory : FeatureModuleFactory {
    override val type: String = "audioPlayer"

    override fun create(definitions: List<ModuleDefinition>): FeatureModule {
        val names = definitions.map { it.name }.distinct()
        return AudioPlayerFeatureModule(names)
    }
}

private fun List<JsonElement>.stringAt(index: Int): String? {
    return getOrNull(index)?.jsonPrimitiveContentOrNull()
}

private fun JsonElement.jsonPrimitiveContentOrNull(): String? {
    return try {
        this.jsonPrimitive.contentOrNull
    } catch (_: Exception) {
        null
    }
}

