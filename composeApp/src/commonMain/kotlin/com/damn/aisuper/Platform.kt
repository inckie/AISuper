package com.damn.aisuper

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform