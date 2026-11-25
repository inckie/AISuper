package com.damn.aisuper.rpc

/**
 * Declares a Kotlin class as a self-documenting FluentOS JSON-RPC surface.
 * Inspected once at registration time by [ReflectiveRpcSurface] without requiring `kotlin-reflect`.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RpcService(
    val name: String,
    val description: String = "",
    val kind: SurfaceKind = SurfaceKind.NATIVE_MODULE,
    val version: String = "1.0.0"
)

/**
 * Exposes a Kotlin method (regular or `suspend`) on an [RpcService] over JSON-RPC 2.0,
 * direct in-process invocation, WebSocket, and optionally MCP.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RpcMethod(
    val name: String = "",
    val aliases: Array<String> = [],
    val description: String = "",
    val returnsDescription: String = "",
    val exposeInMcp: Boolean = true,
    val readOnly: Boolean = false
)

/**
 * Attaches explicit runtime metadata to an [RpcMethod] parameter.
 * Storing the parameter name in a runtime annotation guarantees R8 minification safety.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class RpcParam(
    val name: String,
    val description: String = "",
    val required: Boolean = true,
    val type: String = "",
    val enumValues: Array<String> = [],
    val itemsType: String = ""
)
