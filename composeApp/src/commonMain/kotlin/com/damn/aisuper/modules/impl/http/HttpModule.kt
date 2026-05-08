package com.damn.aisuper.modules.impl.http

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

object HttpComponent {
    private val client = HttpClient()

    suspend fun get(url: String): String {
        return try {
            client.get(url).bodyAsText()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

