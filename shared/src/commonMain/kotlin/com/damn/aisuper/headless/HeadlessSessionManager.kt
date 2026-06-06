package com.damn.aisuper.headless

import com.damn.aisuper.runtime.Applet
import com.damn.aisuper.runtime.Feature
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class HeadlessSessionManager(
    private val appletFactory: (sessionId: String) -> Applet,
    private val idFactory: () -> String
) {
    private val sessions = linkedMapOf<String, HeadlessSession>()

    suspend fun create(manifestPath: String): HeadlessSession {
        val id = idFactory()
        val applet = appletFactory(id)
        applet.loadApplet(manifestPath)
        return HeadlessSession(id = id, applet = applet).also { sessions[id] = it }
    }

    fun get(id: String?): HeadlessSession? = id?.let { sessions[it] }

    fun list(): List<HeadlessSession> = sessions.values.toList()

    fun close(id: String): Boolean {
        val session = sessions.remove(id) ?: return false
        session.close()
        return true
    }

    fun closeAll() {
        sessions.values.forEach { it.close() }
        sessions.clear()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HeadlessSession(
    val id: String,
    val applet: Applet
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _events = MutableSharedFlow<HeadlessSessionSnapshot>(replay = 1, extraBufferCapacity = 64)
    val events: Flow<HeadlessSessionSnapshot> = _events.asSharedFlow()

    init {
        combine(
            applet.currentFeature,
            applet.currentFeature.flatMapLatest { feature -> feature?.values ?: emptyFlow() },
            applet.currentFeature.flatMapLatest { feature -> feature?.layoutRoot ?: emptyFlow() },
            applet.currentStyleSheet,
            applet.currentFramework
        ) { _, _, _, _, _ ->
            snapshot("update")
        }.onEach { payload ->
            _events.emit(payload)
        }.launchIn(scope)
    }

    fun snapshot(reason: String): HeadlessSessionSnapshot {
        val feature: Feature? = applet.currentFeature.value
        return HeadlessSessionSnapshot(
            sessionId = id,
            reason = reason,
            featureId = feature?.id ?: "",
            values = feature?.values?.value ?: emptyMap(),
            layout = feature?.layoutRoot?.value,
            styleSheet = applet.currentStyleSheet.value,
            framework = applet.currentFramework.value
        )
    }

    fun close() {
        applet.close()
        scope.cancel()
    }
}



