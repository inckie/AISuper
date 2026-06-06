package com.damn.aisuper.engine

import com.damn.aisuper.engine.keight.KeightJSEngine

/** Wasm default: Keight (QuickJS not available on this target). Override has no effect on QuickJs → logs and falls back. */
actual fun createPlatformJSEngine(override: JSEngineBackend?): AppJSEngine {
    if (override == JSEngineBackend.QuickJs) {
        println("[AISuper][JS] QuickJS is not available for Wasm target, using Keight")
    }
    return KeightJSEngine()
}

