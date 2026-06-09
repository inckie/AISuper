package com.damn.aisuper.server

import com.damn.aisuper.applet.AppletProviders
import com.damn.aisuper.engine.createAppJSEngine
import com.damn.aisuper.headless.ActionRequest
import com.damn.aisuper.headless.CreateSessionRequest
import com.damn.aisuper.headless.CreateSessionResponse
import com.damn.aisuper.headless.HeadlessSession
import com.damn.aisuper.headless.HeadlessSessionManager
import com.damn.aisuper.headless.HeadlessSessionSnapshot
import com.damn.aisuper.headless.ModuleCommandRequest
import com.damn.aisuper.headless.SetValueRequest
import com.damn.aisuper.runtime.Applet
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

fun main(args: Array<String>) {
    val customProvider = args.firstOrNull()?.let { pathString ->
        val file = File(pathString)
        if (file.isDirectory) {
            AppletProviders.filesystem(file.toPath(), fallbackToClasspath = false)
        } else if (file.extension.equals("zip", ignoreCase = true)) {
            AppletProviders.zip(file.toPath())
        } else {
            null
        }
    }

    // Filesystem-first with classpath fallback: custom path overrides work,
    // and the bundled default applet from :applet-provider is always available.
    val resourceLoader = (customProvider ?: AppletProviders
        .filesystem(fallbackToClasspath = true))
        .createLoader()

    val manager = HeadlessSessionManager(
        appletFactory = { sessionId ->
            Applet(
                engineFactory = { createAppJSEngine("server:$sessionId") },
                resourceLoader = resourceLoader
            )
        },
        idFactory = { UUID.randomUUID().toString() }
    )

    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        install(CallLogging)
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                ignoreUnknownKeys = true
                explicitNulls = false
            })
        }
        install(CORS) {
            anyHost()
            allowHeader("Content-Type")
            allowMethod(io.ktor.http.HttpMethod.Get)
            allowMethod(io.ktor.http.HttpMethod.Post)
        }
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (cause.message ?: "Internal server error"))
                )
            }
        }

        routing {
            get("/health") {
                call.respond(mapOf("ok" to true))
            }

            route("/sessions") {
                post {
                    val request = call.receive<CreateSessionRequest>()
                    
                    val entryPath = request.manifestPath.takeIf { it.isNotEmpty() }
                        ?: if (customProvider != null) "applet.json" else "files/applet.json"

                    val session = manager.create(entryPath)
                    val createdState = session.snapshot("created")
                    if (createdState.featureId.isBlank()) {
                        manager.close(session.id)
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf(
                                "error" to "Failed to load applet from '$entryPath'. Verify the manifest path and entry feature."
                            )
                        )
                        return@post
                    }
                    call.respond(
                        CreateSessionResponse(
                            id = session.id,
                            manifestPath = entryPath,
                            state = createdState
                        )
                    )
                }

                get {
                    call.respond(manager.list().map { it.id })
                }

                get("/{id}/state") {
                    val session = manager.get(call.parameters["id"]).orNotFound(call) ?: return@get
                    call.respond(session.snapshot("state"))
                }

                post("/{id}/action") {
                    val session = manager.get(call.parameters["id"]).orNotFound(call) ?: return@post
                    val request = call.receive<ActionRequest>()
                    session.applet.handleAction(request.action, request.args)
                    call.respond(session.snapshot("action:${request.action}"))
                }

                post("/{id}/value") {
                    val session = manager.get(call.parameters["id"]).orNotFound(call) ?: return@post
                    val request = call.receive<SetValueRequest>()
                    session.applet.updateValue(request.id, request.value)
                    call.respond(session.snapshot("value:${request.id}"))
                }

                post("/{id}/module-command") {
                    val session = manager.get(call.parameters["id"]).orNotFound(call) ?: return@post
                    val request = call.receive<ModuleCommandRequest>()
                    session.applet.handleModuleCommand(
                        moduleType = request.moduleType,
                        target = request.target,
                        command = request.command,
                        args = request.args
                    )
                    call.respond(session.snapshot("module:${request.moduleType}:${request.command}"))
                }

                get("/{id}/events") {
                    val session = manager.get(call.parameters["id"]).orNotFound(call) ?: return@get
                    call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                        // Initial event helps clients bootstrap quickly.
                        writeSseEvent(session.snapshot("connected"))

                        session.events.collect { payload ->
                            if (!coroutineContext.isActive) return@collect
                            writeSseEvent(payload)
                        }
                    }
                }
            }
        }
    }.start(wait = true)
}

private suspend fun java.io.Writer.writeSseEvent(payload: HeadlessSessionSnapshot) {
    val body = serverJson.encodeToString(HeadlessSessionSnapshot.serializer(), payload)
    write("event: state\n")
    write("data: $body\n\n")
    flush()
}

private val serverJson = Json {
    prettyPrint = false
    explicitNulls = false
}

private suspend fun HeadlessSession?.orNotFound(call: io.ktor.server.application.ApplicationCall): HeadlessSession? {
    if (this == null) {
        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Session not found"))
    }
    return this
}


