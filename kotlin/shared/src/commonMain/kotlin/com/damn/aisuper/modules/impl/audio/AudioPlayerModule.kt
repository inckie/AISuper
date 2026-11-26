package com.damn.aisuper.modules.impl.audio

import kotlinx.coroutines.flow.StateFlow

data class AudioPlayerState(
    val sourceUrl: String? = null,
    val phase: String = "idle",
    val positionMs: Long = 0L,
    val durationMs: Long? = null,
    val error: String? = null
)

interface AudioPlayer {
    val name: String
    val state: StateFlow<AudioPlayerState>

    fun load(url: String)
    fun play()
    fun pause()
    fun stop()
    fun seek(positionMs: Long)
    fun release()
}

class AudioPlayerModule(
    playerNames: List<String>
) {
    private val players = playerNames.associateWith { createPlatformAudioPlayer(it) }

    fun names(): Set<String> = players.keys

    fun player(name: String): AudioPlayer? = players[name]

    fun release() {
        players.values.forEach { it.release() }
    }
}
