package com.damn.aisuper.engine

import com.damn.aisuper.engine.keight.KeightJSEngine
import com.damn.aisuper.util.Logger

/** JS default: Keight (QuickJS not available on this target). Override has no effect on QuickJs → logs and falls back. */
actual fun createPlatformJSEngine(override: JSEngineBackend?): AppJSEngine {
    if (override == JSEngineBackend.QuickJs) {
        Logger.w("JS", "EngineFactory") { "QuickJS is not available for JS target, using Keight" }
    }
    return KeightJSEngine()
}
