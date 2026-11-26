@file:OptIn(
    ExperimentalNativeApi::class,
    ExperimentalForeignApi::class,
    ExperimentalCoroutinesApi::class,
    ExperimentalSerializationApi::class,
    ExperimentalAtomicApi::class
)

package com.damn.aisuper.bridge

import com.damn.aisuper.engine.createAppJSEngine
import com.damn.aisuper.layout.LayoutRoot
import com.damn.aisuper.modal.ConfirmationRequest
import com.damn.aisuper.modal.ModalRequest
import com.damn.aisuper.runtime.Applet
import com.damn.aisuper.runtime.AppletResourceLoader
import com.damn.aisuper.runtime.Feature
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import platform.posix.size_tVar
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.CName
import kotlin.random.Random

typealias SnapshotCallback = CPointer<CFunction<(COpaquePointer?, CPointer<ByteVar>?) -> Unit>>
typealias ErrorCallback = CPointer<CFunction<(COpaquePointer?, CPointer<ByteVar>?) -> Unit>>
typealias ReadFileCallback = CPointer<CFunction<(COpaquePointer?, CPointer<ByteVar>?, CPointer<CPointerVar<UByteVar>>?, CPointer<size_tVar>?) -> Int>>
typealias FreeBufCallback = CPointer<CFunction<(COpaquePointer?, CPointer<UByteVar>?) -> Unit>>

private val bridgeJson = Json {
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = true
}

private class CallbackAppletResourceLoader(
    private val readFile: ReadFileCallback,
    private val freeBuf: FreeBufCallback?,
    private val userData: COpaquePointer?
) : AppletResourceLoader {
    override suspend fun readBytes(path: String): ByteArray = memScoped {
        val outPtr = alloc<CPointerVar<UByteVar>>()
        val outLen = alloc<size_tVar>()
        outPtr.value = null
        outLen.value = 0u

        val rc = readFile(userData, path.cstr.ptr, outPtr.ptr, outLen.ptr)
        val buf = outPtr.value
        if (rc != 0 || buf == null) {
            if (buf != null) {
                freeBuf?.invoke(userData, buf)
            }
            throw IllegalStateException("Failed to read applet resource '$path' (rc=$rc)")
        }

        try {
            val len = outLen.value.toInt()
            if (len <= 0) ByteArray(0) else buf.reinterpret<ByteVar>().readBytes(len)
        } finally {
            freeBuf?.invoke(userData, buf)
        }
    }
}

private class NativeSession(
    private val onSnapshot: SnapshotCallback?,
    private val onError: ErrorCallback?,
    readFile: ReadFileCallback,
    freeBuf: FreeBufCallback?,
    private val userData: COpaquePointer?
) {
    val id: String = "nat_${Random.nextInt(100000, 999999)}"
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val closed = AtomicBoolean(false)

    val applet = Applet(
        engineFactory = { createAppJSEngine("native:$id") },
        resourceLoader = CallbackAppletResourceLoader(readFile, freeBuf, userData)
    )

    init {
        val featureStateFlow = applet.currentFeature.flatMapLatest { feature ->
            if (feature == null) {
                flowOf(Triple<Feature?, Map<String, JsonElement>, LayoutRoot?>(null, emptyMap(), null))
            } else {
                combine(feature.values, feature.layoutRoot) { values, layout ->
                    Triple<Feature?, Map<String, JsonElement>, LayoutRoot?>(feature, values, layout)
                }
            }
        }

        val hostStateFlow = combine(
            applet.currentStyleSheet,
            applet.currentFramework,
            applet.modalHost.activeModal
        ) { styleSheet, framework, modal ->
            Triple(styleSheet, framework, modal)
        }

        scope.launch {
            combine(featureStateFlow, hostStateFlow) { (feature, values, layout), (styleSheet, framework, modal) ->
                NativeSessionSnapshot(
                    sessionId = id,
                    reason = "update",
                    featureId = feature?.id ?: "",
                    values = values,
                    layout = layout,
                    styleSheet = styleSheet,
                    framework = framework,
                    modal = modal.toNativeModal()
                )
            }.collectLatest { snapshot ->
                if (snapshot.featureId.isNotEmpty() && snapshot.layout != null) {
                    emitSnapshot(snapshot)
                }
            }
        }
    }

    fun emitSnapshot(snapshot: NativeSessionSnapshot) {
        if (closed.load()) return
        val cb = onSnapshot ?: return
        val encoded = bridgeJson.encodeToString(snapshot)
        memScoped {
            cb(userData, encoded.cstr.ptr)
        }
    }

    fun emitError(message: String) {
        if (closed.load()) return
        val cb = onError ?: return
        memScoped {
            cb(userData, message.cstr.ptr)
        }
    }

    fun close() {
        if (!closed.compareAndSet(expectedValue = false, newValue = true)) return
        applet.close()
        scope.cancel()
    }
}

private fun ModalRequest?.toNativeModal(): NativeConfirmationModal? {
    return when (this) {
        is ConfirmationRequest -> NativeConfirmationModal(
            id = id,
            title = title,
            message = message,
            confirmText = confirmText,
            cancelText = cancelText,
            isDestructive = isDestructive
        )
        null -> null
    }
}

private fun parseArgsList(argsJson: String?): List<JsonElement> {
    if (argsJson.isNullOrBlank()) return emptyList()
    val parsed = bridgeJson.parseToJsonElement(argsJson)
    return (parsed as? JsonArray)?.toList() ?: emptyList()
}

private fun parseJsonValue(valueJson: String?): JsonElement {
    if (valueJson == null) return JsonNull
    return try {
        bridgeJson.parseToJsonElement(valueJson)
    } catch (_: Throwable) {
        JsonNull
    }
}

@CName("aisuper_session_create")
fun aisuperSessionCreate(
    onSnapshot: SnapshotCallback?,
    onError: ErrorCallback?,
    readFile: ReadFileCallback?,
    freeBuf: FreeBufCallback?,
    userData: COpaquePointer?
): COpaquePointer? {
    if (readFile == null) return null
    return try {
        val session = NativeSession(
            onSnapshot = onSnapshot,
            onError = onError,
            readFile = readFile,
            freeBuf = freeBuf,
            userData = userData
        )
        StableRef.create(session).asCPointer()
    } catch (_: Throwable) {
        null
    }
}

@CName("aisuper_session_load_applet")
fun aisuperSessionLoadApplet(handle: COpaquePointer?, manifestPath: CPointer<ByteVar>?) {
    if (handle == null || manifestPath == null) return
    val session = try {
        handle.asStableRef<NativeSession>().get()
    } catch (_: Throwable) {
        return
    }
    try {
        val path = manifestPath.toKString()
        session.scope.launch {
            try {
                session.applet.loadApplet(path)
            } catch (t: Throwable) {
                session.emitError(t.message ?: "Failed to load applet '$path'")
            }
        }
    } catch (t: Throwable) {
        session.emitError(t.message ?: "Failed to schedule applet load")
    }
}

@CName("aisuper_session_send_action")
fun aisuperSessionSendAction(
    handle: COpaquePointer?,
    action: CPointer<ByteVar>?,
    argsJson: CPointer<ByteVar>?
) {
    if (handle == null || action == null) return
    val session = try {
        handle.asStableRef<NativeSession>().get()
    } catch (_: Throwable) {
        return
    }
    try {
        val actionStr = action.toKString()
        val args = parseArgsList(argsJson?.toKString())
        session.scope.launch {
            try {
                session.applet.handleAction(actionStr, args)
            } catch (t: Throwable) {
                session.emitError(t.message ?: "Action '$actionStr' failed")
            }
        }
    } catch (t: Throwable) {
        session.emitError(t.message ?: "Failed to dispatch action")
    }
}

@CName("aisuper_session_set_value")
fun aisuperSessionSetValue(
    handle: COpaquePointer?,
    id: CPointer<ByteVar>?,
    valueJson: CPointer<ByteVar>?
) {
    if (handle == null || id == null) return
    val session = try {
        handle.asStableRef<NativeSession>().get()
    } catch (_: Throwable) {
        return
    }
    try {
        val idStr = id.toKString()
        val value = parseJsonValue(valueJson?.toKString())
        session.scope.launch {
            try {
                session.applet.updateValue(idStr, value)
            } catch (t: Throwable) {
                session.emitError(t.message ?: "Failed to set value '$idStr'")
            }
        }
    } catch (t: Throwable) {
        session.emitError(t.message ?: "Failed to schedule value update")
    }
}

@CName("aisuper_session_module_command")
fun aisuperSessionModuleCommand(
    handle: COpaquePointer?,
    moduleType: CPointer<ByteVar>?,
    target: CPointer<ByteVar>?,
    command: CPointer<ByteVar>?,
    argsJson: CPointer<ByteVar>?
) {
    if (handle == null || moduleType == null || target == null || command == null) return
    val session = try {
        handle.asStableRef<NativeSession>().get()
    } catch (_: Throwable) {
        return
    }
    try {
        val modTypeStr = moduleType.toKString()
        val targetStr = target.toKString()
        val cmdStr = command.toKString()
        val args = parseArgsList(argsJson?.toKString())
        session.scope.launch {
            try {
                session.applet.handleModuleCommand(modTypeStr, targetStr, cmdStr, args)
            } catch (t: Throwable) {
                session.emitError(t.message ?: "Module command '$modTypeStr/$cmdStr' failed")
            }
        }
    } catch (t: Throwable) {
        session.emitError(t.message ?: "Failed to schedule module command")
    }
}

@CName("aisuper_session_respond_modal")
fun aisuperSessionRespondModal(
    handle: COpaquePointer?,
    modalId: CPointer<ByteVar>?,
    confirmed: Boolean
) {
    if (handle == null) return
    val session = try {
        handle.asStableRef<NativeSession>().get()
    } catch (_: Throwable) {
        return
    }
    try {
        val expectedId = modalId?.toKString()
        val active = session.applet.modalHost.activeModal.value
        if (active == null || (expectedId != null && expectedId.isNotEmpty() && active.id != expectedId)) {
            return
        }
        session.applet.modalHost.respondToActive(confirmed)
    } catch (t: Throwable) {
        session.emitError(t.message ?: "Failed to respond to modal")
    }
}

@CName("aisuper_session_destroy")
fun aisuperSessionDestroy(handle: COpaquePointer?) {
    if (handle == null) return
    try {
        val ref = handle.asStableRef<NativeSession>()
        val session = ref.get()
        session.close()
        ref.dispose()
    } catch (_: Throwable) {
    }
}
