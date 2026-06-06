package com.damn.aisuper.headless

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

class RemoteAppletClient(
    private val baseUrl: String,
    private val httpClient: HttpClient = defaultClient,
    private val json: Json = defaultJson
) {
    suspend fun createSession(manifestPath: String): CreateSessionResponse {
        return httpClient.post("$baseUrl/sessions") {
            setBody(CreateSessionRequest(manifestPath))
        }.body()
    }

    suspend fun getSessionState(sessionId: String): HeadlessSessionSnapshot {
        return httpClient.get("$baseUrl/sessions/$sessionId/state").body()
    }

    suspend fun sendAction(sessionId: String, action: String, args: List<kotlinx.serialization.json.JsonElement> = emptyList()): HeadlessSessionSnapshot {
        return httpClient.post("$baseUrl/sessions/$sessionId/action") {
            setBody(ActionRequest(action, args))
        }.body()
    }

    suspend fun setValue(sessionId: String, id: String, value: kotlinx.serialization.json.JsonElement): HeadlessSessionSnapshot {
        return httpClient.post("$baseUrl/sessions/$sessionId/value") {
            setBody(SetValueRequest(id, value))
        }.body()
    }

    suspend fun sendModuleCommand(
        sessionId: String,
        moduleType: String,
        target: String,
        command: String,
        args: List<kotlinx.serialization.json.JsonElement> = emptyList()
    ): HeadlessSessionSnapshot {
        return httpClient.post("$baseUrl/sessions/$sessionId/module-command") {
            setBody(ModuleCommandRequest(moduleType, target, command, args))
        }.body()
    }

    fun events(sessionId: String): Flow<HeadlessSessionSnapshot> = flow {
        val response = httpClient.get("$baseUrl/sessions/$sessionId/events")
        val channel = response.body<io.ktor.utils.io.ByteReadChannel>()
        val eventLines = mutableListOf<String>()

        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line() ?: break
            if (line.isBlank()) {
                val payload = eventLines
                    .asSequence()
                    .filter { it.startsWith("data:") }
                    .joinToString("\n") { it.removePrefix("data:").trimStart() }

                if (payload.isNotBlank()) {
                    emit(json.decodeFromString<HeadlessSessionSnapshot>(payload))
                }
                eventLines.clear()
            } else {
                eventLines += line
            }
        }
    }

    fun close() {
        httpClient.close()
    }

    companion object {
        private val defaultJson = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        private val defaultClient = HttpClient {
            install(ContentNegotiation) {
                json(defaultJson)
            }
        }
    }
}

