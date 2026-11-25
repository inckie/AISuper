package com.damn.aisuper.rpc

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Pluggable transport abstraction for calling any local or remote FluentOS JSON-RPC surface.
 *
 * @wk-id rpc-transports
 * @wk-tags kotlin, json-rpc, transport, websocket, http, ktor, local
 * @wk-categories engineering-specs, agent-architecture
 *
 * Implementations: [LocalRpcTransport] (zero-overhead in-process), [HttpRpcTransport] (JSON-RPC 2.0 over HTTP),
 * and [WebSocketRpcTransport] (persistent Ktor CIO WebSocket client with server push notifications).
 * See [[unified-rpc-surfaces]].
 */
interface RpcTransport {
    val id: String
    val endpointDescription: String
    suspend fun call(
        method: String,
        params: RpcParams = RpcParams.Empty,
        context: RpcCallContext = RpcCallContext.LOCAL
    ): RpcResult
    suspend fun close() {}
}

/**
 * Zero-serialization in-process transport calling [RpcRouter] directly within the same process.
 */
class LocalRpcTransport(
    private val router: RpcRouter,
    private val surfaceScope: String? = null
) : RpcTransport {
    override val id: String = "local"
    override val endpointDescription: String =
        if (surfaceScope.isNullOrBlank()) "local://in-process" else "local://$surfaceScope"

    override suspend fun call(
        method: String,
        params: RpcParams,
        context: RpcCallContext
    ): RpcResult = router.call(
        method = method,
        params = params,
        surfaceScope = surfaceScope,
        context = context.copy(transport = "direct")
    )
}

/**
 * Stateless HTTP JSON-RPC 2.0 transport (`POST` JSON-RPC request -> JSON-RPC response).
 */
class HttpRpcTransport(
    val endpointUrl: String,
    private val headersProvider: () -> Map<String, String> = { emptyMap() },
    private val timeoutMs: Long = 15_000L
) : RpcTransport {
    private val idSeq = AtomicLong(1L)

    override val id: String = "http"
    override val endpointDescription: String = endpointUrl

    override suspend fun call(
        method: String,
        params: RpcParams,
        context: RpcCallContext
    ): RpcResult = withContext(Dispatchers.IO) {
        val reqId = idSeq.getAndIncrement()
        val payload = buildJsonObject {
            put("jsonrpc", RpcRouter.JSON_RPC_VERSION)
            put("id", reqId)
            put("method", method)
            if (params !is RpcParams.Empty) {
                put("params", params.toJsonElement())
            }
        }.toString()

        var conn: HttpURLConnection? = null
        try {
            val timeoutInt = timeoutMs.coerceIn(1_000L, 60_000L).toInt()
            conn = (URI(endpointUrl).toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = timeoutInt
                readTimeout = timeoutInt
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                headersProvider().forEach { (k, v) -> setRequestProperty(k, v) }
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload)
                writer.flush()
            }
            val statusCode = conn.responseCode
            val stream = try {
                if (statusCode in 200..299) conn.inputStream else conn.errorStream
            } catch (_: Exception) {
                null
            }
            val responseText = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            if (statusCode !in 200..299) {
                return@withContext RpcResult.Error(
                    code = statusCode,
                    message = "Remote HTTP RPC error ($statusCode): ${responseText.take(240)}"
                )
            }
            parseJsonRpcResponse(responseText)
        } catch (e: Exception) {
            RpcResult.Error(
                code = RpcErrorCodes.INTERNAL_ERROR,
                message = "HTTP RPC transport to $endpointUrl failed: ${e.message ?: e::class.java.simpleName}"
            )
        } finally {
            conn?.disconnect()
        }
    }
}

/**
 * Full-duplex WebSocket JSON-RPC 2.0 transport with request-ID multiplexing,
 * automatic reconnect on demand, and optional server-push notification callback.
 */
class WebSocketRpcTransport(
    val wsUrl: String,
    private val headersProvider: () -> Map<String, String> = { emptyMap() },
    private val timeoutMs: Long = 15_000L,
    private val onNotification: ((method: String, params: JsonObject) -> Unit)? = null
) : RpcTransport {
    private val idSeq = AtomicLong(1L)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<RpcResult>>()
    private val sessionMutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = HttpClient(CIO) {
        install(WebSockets)
    }

    @Volatile
    private var session: DefaultClientWebSocketSession? = null
    private var readerJob: Job? = null

    override val id: String = "websocket"
    override val endpointDescription: String = wsUrl

    private suspend fun ensureSession(): DefaultClientWebSocketSession = sessionMutex.withLock {
        val current = session
        if (current != null && readerJob?.isActive == true) {
            return current
        }

        val newSession = client.webSocketSession(wsUrl) {
            headersProvider().forEach { (k, v) -> header(k, v) }
        }
        session = newSession
        readerJob?.cancel()
        readerJob = scope.launch {
            try {
                for (frame in newSession.incoming) {
                    if (frame is Frame.Text) {
                        handleIncomingText(frame.readText())
                    }
                }
            } catch (_: Exception) {
                // Connection closed or dropped; fail pending calls cleanly
            } finally {
                val disconnectedError = RpcResult.Error(
                    RpcErrorCodes.INTERNAL_ERROR,
                    "WebSocket RPC connection to $wsUrl closed"
                )
                pending.entries.toList().forEach { (key, deferred) ->
                    if (pending.remove(key, deferred)) {
                        deferred.complete(disconnectedError)
                    }
                }
                if (session === newSession) {
                    session = null
                }
            }
        }
        newSession
    }

    private fun handleIncomingText(text: String) {
        val obj = runCatching { rpcJson.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return
        val rawId = obj["id"]
        if (rawId == null || rawId is JsonNull) {
            val notifMethod = (obj["method"] as? JsonPrimitive)?.contentOrNull ?: return
            val notifParams = (obj["params"] as? JsonObject) ?: JsonObject(emptyMap())
            onNotification?.invoke(notifMethod, notifParams)
            return
        }
        val key = idKey(rawId)
        val deferred = pending.remove(key) ?: return
        deferred.complete(parseJsonRpcObject(obj))
    }

    override suspend fun call(
        method: String,
        params: RpcParams,
        context: RpcCallContext
    ): RpcResult {
        val reqId = idSeq.getAndIncrement()
        val key = reqId.toString()
        val deferred = CompletableDeferred<RpcResult>()
        pending[key] = deferred

        val payload = buildJsonObject {
            put("jsonrpc", RpcRouter.JSON_RPC_VERSION)
            put("id", reqId)
            put("method", method)
            if (params !is RpcParams.Empty) {
                put("params", params.toJsonElement())
            }
        }.toString()

        return try {
            val activeSession = ensureSession()
            activeSession.send(Frame.Text(payload))
            withTimeoutOrNull(timeoutMs) { deferred.await() }
                ?: run {
                    pending.remove(key)
                    RpcResult.Error(
                        RpcErrorCodes.INTERNAL_ERROR,
                        "WebSocket RPC timeout (${timeoutMs}ms) calling '$method' on $wsUrl"
                    )
                }
        } catch (e: Exception) {
            pending.remove(key)
            RpcResult.Error(
                RpcErrorCodes.INTERNAL_ERROR,
                "WebSocket RPC error calling '$method' on $wsUrl: ${e.message ?: e::class.java.simpleName}"
            )
        }
    }

    override suspend fun close() {
        runCatching { session?.close() }
        session = null
        readerJob?.cancel()
        readerJob = null
        pending.clear()
        runCatching { client.close() }
        scope.cancel()
    }
}

private val rpcJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

private fun idKey(elem: JsonElement): String =
    (elem as? JsonPrimitive)?.contentOrNull ?: elem.toString()

internal fun parseJsonRpcResponse(rawText: String): RpcResult {
    val obj = try {
        rpcJson.parseToJsonElement(rawText) as? JsonObject
            ?: return RpcResult.Error(RpcErrorCodes.PARSE_ERROR, "Remote RPC response is not a JSON object")
    } catch (e: Exception) {
        return RpcResult.Error(RpcErrorCodes.PARSE_ERROR, "Failed parsing remote RPC JSON: ${e.message}")
    }
    return parseJsonRpcObject(obj)
}

internal fun parseJsonRpcObject(obj: JsonObject): RpcResult {
    val errObj = obj["error"] as? JsonObject
    if (errObj != null) {
        val code = (errObj["code"] as? JsonPrimitive)?.intOrNull ?: RpcErrorCodes.INTERNAL_ERROR
        val msg = (errObj["message"] as? JsonPrimitive)?.contentOrNull ?: "Remote RPC error"
        return RpcResult.Error(code = code, message = msg, data = errObj["data"])
    }
    val resultElem = obj["result"] ?: JsonObject(emptyMap())
    return RpcResult.Success(resultElem)
}
