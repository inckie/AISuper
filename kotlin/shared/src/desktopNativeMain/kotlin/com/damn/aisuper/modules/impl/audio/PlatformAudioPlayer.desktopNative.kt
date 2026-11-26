package com.damn.aisuper.modules.impl.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private class StubAudioPlayer(
    override val name: String
) : AudioPlayer {
    private val _state = MutableStateFlow(AudioPlayerState())
    override val state: StateFlow<AudioPlayerState> = _state.asStateFlow()

    override fun load(url: String) {
        _state.update {
            it.copy(
                sourceUrl = url,
                phase = "ready",
                positionMs = 0L,
                durationMs = null,
                error = null
            )
        }
    }

    override fun play() {
        if (_state.value.sourceUrl == null) return
        _state.update { it.copy(phase = "playing", error = null) }
    }

    override fun pause() {
        _state.update { it.copy(phase = "paused") }
    }

    override fun stop() {
        _state.update {
            it.copy(
                phase = if (it.sourceUrl == null) "idle" else "ready",
                positionMs = 0L
            )
        }
    }

    override fun seek(positionMs: Long) {
        _state.update { it.copy(positionMs = positionMs) }
    }

    override fun release() {
        _state.update { AudioPlayerState() }
    }
}

actual fun createPlatformAudioPlayer(name: String): AudioPlayer = StubAudioPlayer(name)
