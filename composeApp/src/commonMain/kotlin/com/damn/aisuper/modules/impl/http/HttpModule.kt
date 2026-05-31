package com.damn.aisuper.modules.impl.http

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText

object HttpComponent {
    private val client = HttpClient()

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        return try {
            client.get(url) {
                headers.forEach { (key, value) ->
                    header(key, value)
                }
            }.bodyAsText()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun post(url: String, body: String = "", headers: Map<String, String> = emptyMap()): String {
        return try {
            client.post(url) {
                headers.forEach { (key, value) ->
                    header(key, value)
                }
                if (body.isNotEmpty()) {
                    setBody(body)
                }
            }.bodyAsText()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

