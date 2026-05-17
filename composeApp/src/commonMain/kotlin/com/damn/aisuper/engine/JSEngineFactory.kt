package com.damn.aisuper.engine

enum class JSEngineBackend {
    Keight,
    QuickJs
}

object JSEngineSettings {
    /**
     * Global backend preference. When set to null (default) each platform uses its own
     * default (QuickJS on Android/JVM/iOS, Keight on JS/Wasm).
     * Override here to force one engine everywhere:
     *   JSEngineSettings.override = JSEngineBackend.QuickJs
     *   JSEngineSettings.override = JSEngineBackend.Keight
     */
    var override: JSEngineBackend? = null
}

fun createAppJSEngine(): AppJSEngine =
    createPlatformJSEngine(JSEngineSettings.override)

/**
 * Platform actuals must:
 *  - use QuickJs when [override] == QuickJs
 *  - use Keight  when [override] == Keight
 *  - apply their own default when [override] == null
 */
expect fun createPlatformJSEngine(override: JSEngineBackend?): AppJSEngine

