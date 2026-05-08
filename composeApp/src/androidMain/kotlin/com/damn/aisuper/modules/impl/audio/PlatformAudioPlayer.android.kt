package com.damn.aisuper.modules.impl.audio

import android.media.MediaPlayer
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

actual fun createPlatformAudioPlayer(name: String): AudioPlayer = AndroidAudioPlayer(name)

private class AndroidAudioPlayer(
    override val name: String
) : AudioPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(AudioPlayerState())
    override val state: StateFlow<AudioPlayerState> = _state.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var positionJob: Job? = null
    private var playWhenPrepared = false

    override fun load(url: String) {
        releasePlayer()
        playWhenPrepared = false

        _state.value = AudioPlayerState(
            sourceUrl = url,
            phase = "loading",
            positionMs = 0L,
            durationMs = null,
            error = null
        )

        val player = MediaPlayer()
        mediaPlayer = player

        player.setOnPreparedListener { mp ->
            val duration = runCatching { mp.duration.toLong() }.getOrNull()
            _state.update {
                it.copy(
                    phase = "ready",
                    durationMs = duration,
                    error = null
                )
            }
            if (playWhenPrepared) {
                playWhenPrepared = false
                play()
            }
        }

        player.setOnCompletionListener { mp ->
            stopPositionTracking()
            _state.update {
                it.copy(
                    phase = "completed",
                    positionMs = runCatching { mp.currentPosition.toLong() }.getOrDefault(it.positionMs)
                )
            }
        }

        player.setOnErrorListener { _, what, extra ->
            stopPositionTracking()
            _state.update {
                it.copy(
                    phase = "error",
                    error = "MediaPlayer error ($what,$extra)"
                )
            }
            true
        }

        try {
            player.setDataSource(url)
            player.prepareAsync()
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    phase = "error",
                    error = e.message ?: "Failed to load stream"
                )
            }
        }
    }

    override fun play() {
        val player = mediaPlayer ?: return
        val phase = _state.value.phase
        if (phase == "loading") {
            playWhenPrepared = true
            return
        }
        try {
            player.start()
            _state.update { it.copy(phase = "playing", error = null) }
            startPositionTracking()
        } catch (e: Exception) {
            _state.update { it.copy(phase = "error", error = e.message ?: "Failed to play") }
        }
    }

    override fun pause() {
        val player = mediaPlayer ?: return
        try {
            if (player.isPlaying) {
                player.pause()
            }
            stopPositionTracking()
            _state.update {
                it.copy(
                    phase = "paused",
                    positionMs = runCatching { player.currentPosition.toLong() }.getOrDefault(it.positionMs)
                )
            }
        } catch (e: Exception) {
            _state.update { it.copy(phase = "error", error = e.message ?: "Failed to pause") }
        }
    }

    override fun stop() {
        stopPositionTracking()
        playWhenPrepared = false
        val source = _state.value.sourceUrl
        val duration = _state.value.durationMs

        runCatching {
            mediaPlayer?.stop()
        }
        releasePlayer()

        _state.value = AudioPlayerState(
            sourceUrl = source,
            phase = if (source == null) "idle" else "ready",
            positionMs = 0L,
            durationMs = duration,
            error = null
        )
    }

    override fun seek(positionMs: Long) {
        val player = mediaPlayer ?: return
        val safePosition = positionMs.coerceAtLeast(0L).toInt()
        runCatching { player.seekTo(safePosition) }
        _state.update { it.copy(positionMs = safePosition.toLong()) }
    }

    override fun release() {
        stopPositionTracking()
        releasePlayer()
        scope.cancel()
    }

    private fun startPositionTracking() {
        stopPositionTracking()
        val player = mediaPlayer ?: return
        positionJob = scope.launch {
            while (isActive) {
                delay(500)
                val current = runCatching { player.currentPosition.toLong() }.getOrNull() ?: continue
                val duration = runCatching { player.duration.toLong() }.getOrNull()
                _state.update { it.copy(positionMs = current, durationMs = duration) }
            }
        }
    }

    private fun stopPositionTracking() {
        positionJob?.cancel()
        positionJob = null
    }

    private fun releasePlayer() {
        val player = mediaPlayer
        mediaPlayer = null
        if (player != null) {
            runCatching {
                player.reset()
                player.release()
            }
        }
    }
}


