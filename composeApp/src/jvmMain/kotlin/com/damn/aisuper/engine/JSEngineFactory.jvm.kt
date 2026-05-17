package com.damn.aisuper.engine

import com.damn.aisuper.engine.keight.KeightJSEngine
import com.damn.aisuper.engine.quickjs.QuickJsKtEngine

/** JVM default: QuickJS. Override via [JSEngineSettings.override]. */
actual fun createPlatformJSEngine(override: JSEngineBackend?): AppJSEngine {
    return when (override ?: JSEngineBackend.QuickJs) {
        JSEngineBackend.QuickJs -> QuickJsKtEngine()
        JSEngineBackend.Keight -> KeightJSEngine()
    }
}

