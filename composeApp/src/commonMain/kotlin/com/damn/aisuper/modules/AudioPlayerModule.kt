package com.damn.aisuper.modules

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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

/**
 * Lightweight fallback player used on platforms where we do not wire real media playback yet.
 */
class NoopAudioPlayer(
    override val name: String
) : AudioPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(AudioPlayerState())
    override val state: StateFlow<AudioPlayerState> = _state.asStateFlow()

    private var tickerJob: Job? = null

    override fun load(url: String) {
        stopTicker()
        _state.value = AudioPlayerState(
            sourceUrl = url,
            phase = "ready",
            positionMs = 0L,
            durationMs = null,
            error = null
        )
    }

    override fun play() {
        if (_state.value.sourceUrl.isNullOrBlank()) return
        _state.update { it.copy(phase = "playing", error = null) }
        startTicker()
    }

    override fun pause() {
        stopTicker()
        _state.update { it.copy(phase = "paused") }
    }

    override fun stop() {
        stopTicker()
        _state.update {
            it.copy(
                phase = if (it.sourceUrl == null) "idle" else "ready",
                positionMs = 0L
            )
        }
    }

    override fun seek(positionMs: Long) {
        _state.update { it.copy(positionMs = positionMs.coerceAtLeast(0L)) }
    }

    override fun release() {
        stopTicker()
        scope.cancel()
    }

    private fun startTicker() {
        stopTicker()
        tickerJob = scope.launch {
            while (isActive) {
                delay(500)
                _state.update { current ->
                    if (current.phase == "playing") {
                        current.copy(positionMs = current.positionMs + 500L)
                    } else {
                        current
                    }
                }
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }
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

