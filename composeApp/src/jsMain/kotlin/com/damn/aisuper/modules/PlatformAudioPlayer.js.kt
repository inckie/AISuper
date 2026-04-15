package com.damn.aisuper.modules

actual fun createPlatformAudioPlayer(name: String): AudioPlayer = NoopAudioPlayer(name)

