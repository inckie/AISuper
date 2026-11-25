package com.damn.aisuper.toolbox

enum class ToolProviderKind(val wireName: String) {
    OS("os"),
    NATIVE_MODULE("native_module"),
    JS_POD("js_pod"),
    PLUGIN("plugin")
}

/**
 * Named group of related [Tool]s contributed by an OS subsystem, native module, or JS pod.
 */
interface ToolProvider {
    val name: String
    val description: String
    val kind: ToolProviderKind get() = ToolProviderKind.NATIVE_MODULE

    fun qualifiedToolName(localName: String): String =
        if (localName.startsWith("$name.")) localName else "$name.$localName"

    fun tools(): List<Tool>
}
