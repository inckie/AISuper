package com.damn.aisuper.modules.impl.audio

actual fun createPlatformAudioPlayer(name: String): AudioPlayer = NoopAudioPlayer(name)


