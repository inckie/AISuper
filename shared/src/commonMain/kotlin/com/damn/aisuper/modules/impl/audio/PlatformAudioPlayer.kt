package com.damn.aisuper.modules.impl.audio

fun createPlatformAudioPlayer(name: String): AudioPlayer = ComposeAudioPlayer(name)
