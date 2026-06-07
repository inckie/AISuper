package com.damn.aisuper.modules.impl.http

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

object HttpComponent {
    private val client = HttpClient {
        expectSuccess = false // Do not throw on non-2xx responses
    }

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        return try {
            val response = client.get(url) {
                headers.forEach { (key, value) ->
                    header(key, value)
                }
            }
            if (response.status.value in 200..299) {
                response.bodyAsText()
            } else {
                "Error: HTTP ${response.status.value}"
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun post(url: String, body: String = "", headers: Map<String, String> = emptyMap()): String {
        return try {
            val response = client.post(url) {
                headers.forEach { (key, value) ->
                    header(key, value)
                }
                if (body.isNotEmpty()) {
                    setBody(body)
                }
            }
            if (response.status.value in 200..299) {
                response.bodyAsText()
            } else {
                "Error: HTTP ${response.status.value}"
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun requestRaw(method: String, url: String, body: String = "", headers: Map<String, String> = emptyMap()): String {
        return try {
            val response: HttpResponse = if (method.uppercase() == "POST") {
                client.post(url) {
                    headers.forEach { (key, value) ->
                        header(key, value)
                    }
                    if (body.isNotEmpty()) {
                        setBody(body)
                    }
                }
            } else {
                client.get(url) {
                    headers.forEach { (key, value) ->
                        header(key, value)
                    }
                }
            }
            
            val responseHeaders = buildJsonObject {
                response.headers.entries().forEach { (key, values) ->
                    put(key, JsonPrimitive(values.firstOrNull() ?: ""))
                }
            }
            
            val result = buildJsonObject {
                put("status", JsonPrimitive(response.status.value))
                put("headers", responseHeaders)
                put("body", JsonPrimitive(response.bodyAsText()))
            }
            Json.encodeToString(result)
        } catch (e: Exception) {
            val result = buildJsonObject {
                put("status", JsonPrimitive(0))
                put("error", JsonPrimitive(e.message ?: "Unknown error"))
            }
            Json.encodeToString(result)
        }
    }
}

