package com.damn.aisuper.modules.impl.rpc

import com.damn.aisuper.modules.FeatureModule
import com.damn.aisuper.modules.FeatureModuleContext
import com.damn.aisuper.modules.FeatureModuleFactory
import com.damn.aisuper.rpc.RpcCallContext
import com.damn.aisuper.rpc.RpcParams
import com.damn.aisuper.rpc.RpcResult
import com.damn.aisuper.rpc.RpcRouter
import com.damn.aisuper.rpc.RpcScope
import com.damn.aisuper.rpc.SurfaceRegistry
import com.damn.aisuper.rpc.Surfaces
import com.damn.aisuper.runtime.ModuleDefinition
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * Bridges any [com.damn.aisuper.rpc.RpcSurface] registered in [SurfaceRegistry]
 * into AISuper applets as a standard [FeatureModule], enforcing optional [RpcScope]
 * permissions (multi-toolbox access control).
 */
class RpcSurfaceFeatureModuleFactory(
    private val registry: SurfaceRegistry = Surfaces,
    private val scope: RpcScope = RpcScope.ALL
) : FeatureModuleFactory {

    override val type: String = "rpcSurface"

    override val exposedFunctions: Set<String> = setOf("rpc_discover", "rpc_call")

    override suspend fun create(definition: ModuleDefinition): FeatureModule {
        return RpcSurfaceFeatureModule(
            moduleName = definition.name,
            registry = registry,
            scope = scope
        )
    }
}

private class RpcSurfaceFeatureModule(
    private val moduleName: String,
    private val registry: SurfaceRegistry,
    private val scope: RpcScope
) : FeatureModule {

    private val router = RpcRouter(registry)

    override suspend fun attach(context: FeatureModuleContext) {
        val callCtx = RpcCallContext(
            caller = "aisuper:$moduleName",
            transport = "aisuper-feature-module"
        )

        context.registerSuspendFunction("rpc_discover") { args ->
            val filter = (args.getOrNull(0) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            val effectiveScope = if (filter != null) {
                if (scope.allowsSurface(filter)) scope.copy(allowedSurfaces = setOf(filter))
                else scope.copy(allowedSurfaces = emptySet())
            } else {
                scope
            }
            registry.toDiscoveryJson(mcpEnabled = false, scope = effectiveScope)
        }

        context.registerSuspendFunction("rpc_call") { args ->
            val method = (args.getOrNull(0) as? JsonPrimitive)?.contentOrNull
                ?: return@registerSuspendFunction errorJson("rpc_call requires method name as first argument")
            val params = toRpcParams(args.getOrNull(1))
            when (val res = router.call(method = method, params = params, context = callCtx, scope = scope)) {
                is RpcResult.Success -> res.data
                is RpcResult.Error -> errorJson(res.message, res.code)
            }
        }

        // If a surface matches definition.name, also expose convenience `<surface>_<method>` helpers
        val targetSurface = registry.findSurface(moduleName)
        if (targetSurface != null && scope.allowsSurface(targetSurface.name)) {
            val desc = targetSurface.descriptor()
            for (methodDesc in desc.methods) {
                if (!scope.allowsMethod(targetSurface.name, methodDesc)) continue
                val fnName = "${targetSurface.name}_${methodDesc.name}"
                context.registerSuspendFunction(fnName) { args ->
                    val params = when {
                        args.isEmpty() -> RpcParams.Empty
                        args.size == 1 && args[0] is JsonObject -> RpcParams.Named(args[0] as JsonObject)
                        else -> RpcParams.Positional(JsonArray(args))
                    }
                    when (val res = router.call(
                        method = methodDesc.qualifiedName,
                        params = params,
                        context = callCtx,
                        scope = scope
                    )) {
                        is RpcResult.Success -> res.data
                        is RpcResult.Error -> errorJson(res.message, res.code)
                    }
                }
            }
        }
    }

    override fun detach() {
        // Nothing to release; registry lifecycle is owned by host
    }

    private fun toRpcParams(arg: JsonElement?): RpcParams = when (arg) {
        null, is JsonNull -> RpcParams.Empty
        is JsonObject -> RpcParams.Named(arg)
        is JsonArray -> RpcParams.Positional(arg)
        else -> RpcParams.Positional(JsonArray(listOf(arg)))
    }

    private fun errorJson(message: String, code: Int = -32603): JsonObject = buildJsonObject {
        put("error", message)
        put("code", code)
    }
}
