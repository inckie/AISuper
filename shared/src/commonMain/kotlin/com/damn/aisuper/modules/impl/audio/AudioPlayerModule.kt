package com.damn.aisuper.modules.impl.audio

import io.github.kdroidfilter.composemediaplayer.audio.AudioPlayer as LibAudioPlayer
import io.github.kdroidfilter.composemediaplayer.audio.AudioPlayerState as LibAudioPlayerState
import io.github.kdroidfilter.composemediaplayer.audio.ErrorListener
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
 * Audio player implementation using ComposeMediaPlayer.
 */
class ComposeAudioPlayer(
    override val name: String
) : AudioPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(AudioPlayerState())
    override val state: StateFlow<AudioPlayerState> = _state.asStateFlow()

    private val player = LibAudioPlayer()
    private var tickerJob: Job? = null

    init {
        player.setOnErrorListener(object : ErrorListener {
            override fun onError(message: String?) {
                _state.update { it.copy(phase = "error", error = message ?: "Unknown error") }
            }
        })
    }

    override fun load(url: String) {
        stopTicker()
        // ComposeMediaPlayer.play(url) both loads and starts playing if not careful.
        // But we want to follow our interface which has separate load and play.
        // Actually LibAudioPlayer.play(url) is the way to start it.
        // We'll store the URL and "ready" it.
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
        val url = _state.value.sourceUrl ?: return
        try {
            // Note: play(url) starts playback immediately.
            player.play(url)
            _state.update { it.copy(phase = "playing", error = null) }
            startTicker()
        } catch (e: Exception) {
            _state.update { it.copy(phase = "error", error = e.message) }
        }
    }

    override fun pause() {
        player.pause()
        stopTicker()
        updateStateFromPlayer("paused")
    }

    override fun stop() {
        player.stop()
        stopTicker()
        _state.update {
            it.copy(
                phase = if (it.sourceUrl == null) "idle" else "ready",
                positionMs = 0L
            )
        }
    }

    override fun seek(positionMs: Long) {
        player.seekTo(positionMs)
        _state.update { it.copy(positionMs = positionMs) }
    }

    override fun release() {
        stopTicker()
        player.release()
        scope.cancel()
    }

    private fun startTicker() {
        stopTicker()
        tickerJob = scope.launch {
            while (isActive) {
                delay(500)
                updateStateFromPlayer()
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun updateStateFromPlayer(forcedPhase: String? = null) {
        val currentPos = player.currentPosition() ?: 0L
        val duration = player.currentDuration()
        val libState = player.currentPlayerState()

        _state.update { current ->
            current.copy(
                positionMs = currentPos,
                durationMs = duration,
                phase = forcedPhase ?: mapLibState(libState)
            )
        }
    }

    private fun mapLibState(libState: LibAudioPlayerState?): String = when (libState) {
        LibAudioPlayerState.PLAYING -> "playing"
        LibAudioPlayerState.PAUSED -> "paused"
        LibAudioPlayerState.BUFFERING -> "loading"
        LibAudioPlayerState.IDLE -> "idle"
        null -> "idle"
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
