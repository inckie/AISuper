package com.damn.aisuper.rpc

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.serializerOrNull
import com.damn.aisuper.toolbox.ParameterType
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Shared `kotlinx.serialization` JSON codec for self-documenting RPC surfaces.
 *
 * Uses `encodeDefaults = true` and `explicitNulls = false` so `@Serializable` DTOs
 * automatically serialize identically to manual `if (x != null) put(...)` builders.
 */
@OptIn(ExperimentalSerializationApi::class)
object RpcJson {
    val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
        isLenient = true
    }

    inline fun <reified T> encode(value: T): JsonElement = json.encodeToJsonElement(value)

    inline fun <reified T> decode(element: JsonElement): T = json.decodeFromJsonElement(element)

    @Suppress("UNCHECKED_CAST")
    fun encodeAny(value: Any?, declaredType: Type? = null): JsonElement {
        if (value == null || value is Unit) return JsonObject(emptyMap())
        if (value is JsonElement) return value
        if (value is String) return JsonPrimitive(value)
        if (value is Boolean) return JsonPrimitive(value)
        if (value is Number) return JsonPrimitive(value)

        if (declaredType != null && declaredType != Any::class.java) {
            val declaredSerializer = runCatching { serializerOrNull(declaredType) }.getOrNull()
            if (declaredSerializer != null) {
                return json.encodeToJsonElement(declaredSerializer as KSerializer<Any>, value)
            }
        }

        val runtimeSerializer = runCatching { serializerOrNull(value.javaClass) }.getOrNull()
        if (runtimeSerializer != null) {
            return json.encodeToJsonElement(runtimeSerializer as KSerializer<Any>, value)
        }

        if (value is Iterable<*>) {
            return JsonArray(value.map { item -> if (item == null) JsonNull else encodeAny(item) })
        }
        if (value is Map<*, *>) {
            return JsonObject(
                value.entries.associate { (k, v) ->
                    k.toString() to (if (v == null) JsonNull else encodeAny(v))
                }
            )
        }
        return JsonPrimitive(value.toString())
    }
}

/**
 * Self-documenting [RpcSurface] powered by JVM runtime annotations (`@RpcService`, `@RpcMethod`, `@RpcParam`).
 *
 * @wk-id rpc-reflection-kotlin
 * @wk-tags kotlin, json-rpc, reflection, serialization, annotations, r8-safe
 * @wk-categories engineering-specs, agent-architecture
 *
 * Scans methods once at construction, caches parameter bindings, supports both regular and Kotlin `suspend`
 * functions without requiring `kotlin-reflect`, automatically serializes/deserializes `@Serializable` DTOs
 * via [RpcJson], validates named and positional JSON-RPC parameters, and emits full capability metadata
 * for `rpc.discover` and MCP. See [[unified-rpc-surfaces]].
 */
class ReflectiveRpcSurface(
    val target: Any,
    nameOverride: String? = null,
    descriptionOverride: String? = null,
    kindOverride: SurfaceKind? = null,
    versionOverride: String? = null,
    private val resultMapper: ((Any?) -> RpcResult?)? = null
) : RpcSurface {

    private data class ParamBinding(
        val descriptor: ParamDescriptor,
        val jvmType: Class<*>,
        val genericType: Type,
        val isContextParam: Boolean = false
    )

    private data class BoundMethod(
        val descriptor: MethodDescriptor,
        val javaMethod: Method,
        val isSuspend: Boolean,
        val returnType: Type,
        val bindings: List<ParamBinding>
    )

    private val surfaceMeta: RpcService? = target.javaClass.getAnnotation(RpcService::class.java)

    override val name: String = (nameOverride ?: surfaceMeta?.name)?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: throw IllegalArgumentException(
            "ReflectiveRpcSurface requires @RpcService(name = ...) or nameOverride on ${target.javaClass.name}"
        )

    private val surfaceDescription: String = descriptionOverride ?: surfaceMeta?.description.orEmpty()
    private val surfaceKind: SurfaceKind = kindOverride ?: surfaceMeta?.kind ?: SurfaceKind.NATIVE_MODULE
    private val surfaceVersion: String = versionOverride ?: surfaceMeta?.version ?: "1.0.0"

    private val canonicalMethods: List<BoundMethod>
    private val lookupMap: Map<String, BoundMethod>
    private val cachedDescriptor: SurfaceDescriptor

    init {
        val discovered = linkedMapOf<String, BoundMethod>()
        val methodsToScan = (target.javaClass.methods.toList() + target.javaClass.declaredMethods.toList())
            .distinctBy { "${it.name}(${it.parameterTypes.joinToString(",") { p -> p.name }})" }

        for (javaMethod in methodsToScan) {
            val ann = javaMethod.getAnnotation(RpcMethod::class.java) ?: continue
            javaMethod.isAccessible = true

            val rawTypes = javaMethod.parameterTypes
            val genericTypes = javaMethod.genericParameterTypes
            val paramAnnotations = javaMethod.parameterAnnotations

            val isSuspend = rawTypes.isNotEmpty() &&
                Continuation::class.java.isAssignableFrom(rawTypes.last())
            val effectiveCount = if (isSuspend) rawTypes.size - 1 else rawTypes.size

            val canonicalName = ann.name.trim().ifEmpty { javaMethod.name }
            val aliasSet = linkedSetOf<String>()
            ann.aliases.forEach { alias ->
                val clean = alias.trim()
                if (clean.isNotEmpty() && clean != canonicalName) aliasSet.add(clean)
            }
            if (javaMethod.name != canonicalName) {
                aliasSet.add(javaMethod.name)
            }

            val bindings = mutableListOf<ParamBinding>()
            val paramDescriptors = mutableListOf<ParamDescriptor>()
            var rpcPos = 0

            for (i in 0 until effectiveCount) {
                val jvmType = rawTypes[i]
                if (RpcCallContext::class.java.isAssignableFrom(jvmType)) {
                    bindings.add(
                        ParamBinding(
                            descriptor = ParamDescriptor(
                                name = "__context__",
                                type = ParameterType.OBJECT,
                                required = false,
                                position = -1
                            ),
                            jvmType = jvmType,
                            genericType = genericTypes.getOrNull(i) ?: jvmType,
                            isContextParam = true
                        )
                    )
                    continue
                }

                val rpcParam = paramAnnotations[i].filterIsInstance<RpcParam>().firstOrNull()
                val paramName = rpcParam?.name?.trim()?.takeIf { it.isNotEmpty() }
                    ?: "arg$rpcPos"
                val explicitType = rpcParam?.type?.trim()?.takeIf { it.isNotEmpty() }
                val inferredType = if (explicitType != null) {
                    ParameterType.fromSchemaType(explicitType)
                } else {
                    inferParameterType(jvmType)
                }
                val inferredItemsType = if (inferredType == ParameterType.ARRAY) {
                    val explicitItemsType = rpcParam?.itemsType?.trim()?.takeIf { it.isNotEmpty() }
                    if (explicitItemsType != null) {
                        ParameterType.fromSchemaType(explicitItemsType)
                    } else {
                        inferItemsType(genericTypes.getOrNull(i))
                    }
                } else {
                    null
                }
                val required = rpcParam?.required ?: jvmType.isPrimitive
                val enumValues = rpcParam?.enumValues
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.takeIf { it.isNotEmpty() }

                val pDesc = ParamDescriptor(
                    name = paramName,
                    type = inferredType,
                    description = rpcParam?.description.orEmpty(),
                    required = required,
                    position = rpcPos,
                    enumValues = enumValues,
                    itemsType = inferredItemsType
                )
                paramDescriptors.add(pDesc)
                bindings.add(
                    ParamBinding(
                        descriptor = pDesc,
                        jvmType = jvmType,
                        genericType = genericTypes.getOrNull(i) ?: jvmType
                    )
                )
                rpcPos += 1
            }

            val resolvedReturnType: Type = if (isSuspend) {
                extractSuspendReturnType(genericTypes.lastOrNull()) ?: Any::class.java
            } else {
                javaMethod.genericReturnType
            }

            val methodDesc = MethodDescriptor(
                name = canonicalName,
                qualifiedName = "$name.$canonicalName",
                aliases = aliasSet.toList(),
                description = ann.description,
                parameters = paramDescriptors,
                returnsDescription = ann.returnsDescription.trim().takeIf { it.isNotEmpty() },
                exposeInMcp = ann.exposeInMcp,
                readOnly = ann.readOnly
            )

            discovered[canonicalName] = BoundMethod(
                descriptor = methodDesc,
                javaMethod = javaMethod,
                isSuspend = isSuspend,
                returnType = resolvedReturnType,
                bindings = bindings
            )
        }

        canonicalMethods = discovered.values.toList()
        val index = linkedMapOf<String, BoundMethod>()
        for (bound in canonicalMethods) {
            index[bound.descriptor.name] = bound
            for (alias in bound.descriptor.aliases) {
                index.putIfAbsent(alias, bound)
            }
        }
        lookupMap = index
        cachedDescriptor = SurfaceDescriptor(
            name = name,
            description = surfaceDescription,
            kind = surfaceKind,
            location = SurfaceLocation.LOCAL,
            version = surfaceVersion,
            methods = canonicalMethods.map { it.descriptor }
        )
    }

    override fun descriptor(): SurfaceDescriptor = cachedDescriptor

    override suspend fun invoke(
        method: String,
        params: RpcParams,
        context: RpcCallContext
    ): RpcResult {
        val cleanName = if (method.startsWith("$name.")) method.removePrefix("$name.") else method
        val bound = lookupMap[cleanName]
            ?: return RpcResult.Error(
                code = RpcErrorCodes.METHOD_NOT_FOUND,
                message = "Method '$cleanName' not found on surface '$name'"
            )

        val rawArgs = try {
            bindArguments(bound, params, context)
        } catch (e: RpcException) {
            return RpcResult.Error(code = e.code, message = e.message, data = e.data)
        }

        return try {
            val outcome = if (bound.isSuspend) {
                invokeSuspendMethod(bound.javaMethod, target, rawArgs)
            } else {
                invokeRegularMethod(bound.javaMethod, target, rawArgs)
            }
            coerceReturnValue(outcome, bound.returnType)
        } catch (e: RpcException) {
            RpcResult.Error(code = e.code, message = e.message, data = e.data)
        } catch (e: IllegalArgumentException) {
            RpcResult.Error(
                code = RpcErrorCodes.INVALID_PARAMS,
                message = e.message ?: "Invalid params for '$name.$cleanName'"
            )
        } catch (e: Exception) {
            RpcResult.Error(
                code = RpcErrorCodes.INTERNAL_ERROR,
                message = e.message ?: "Failed executing '$name.$cleanName'"
            )
        }
    }

    private fun bindArguments(
        bound: BoundMethod,
        params: RpcParams,
        context: RpcCallContext
    ): Array<Any?> {
        val namedMap: Map<String, JsonElement> = when (params) {
            RpcParams.Empty -> emptyMap()
            is RpcParams.Named -> params.values
            is RpcParams.Positional -> {
                val ordered = bound.descriptor.parameters.sortedBy { it.position }
                val requiredCount = ordered.count { it.required }
                if (params.values.size < requiredCount) {
                    val missing = ordered
                        .drop(params.values.size)
                        .filter { it.required }
                        .map { it.name }
                    if (missing.isNotEmpty()) {
                        throw RpcException(
                            RpcErrorCodes.INVALID_PARAMS,
                            "Invalid params. Missing required arguments: ${missing.joinToString(", ")}"
                        )
                    }
                }
                buildMap {
                    for (idx in params.values.indices) {
                        val p = ordered.getOrNull(idx) ?: break
                        put(p.name, params.values[idx])
                    }
                }
            }
        }

        val missingRequired = mutableListOf<String>()
        val resolved = ArrayList<Any?>(bound.bindings.size)

        for (binding in bound.bindings) {
            if (binding.isContextParam) {
                resolved.add(context)
                continue
            }
            val pDesc = binding.descriptor
            val rawElem = namedMap[pDesc.name]
            val isMissing = rawElem == null || rawElem is JsonNull

            if (isMissing) {
                if (pDesc.required) {
                    missingRequired.add(pDesc.name)
                }
                resolved.add(defaultMissingValue(binding.jvmType))
                continue
            }

            resolved.add(coerceArgument(pDesc, binding.jvmType, binding.genericType, rawElem))
        }

        if (missingRequired.isNotEmpty()) {
            throw RpcException(
                RpcErrorCodes.INVALID_PARAMS,
                "Invalid params. Missing required arguments: ${missingRequired.joinToString(", ")}"
            )
        }

        return resolved.toTypedArray()
    }

    private fun defaultMissingValue(jvmType: Class<*>): Any? = when (jvmType) {
        Boolean::class.javaPrimitiveType -> false
        Int::class.javaPrimitiveType -> 0
        Long::class.javaPrimitiveType -> 0L
        Double::class.javaPrimitiveType -> 0.0
        Float::class.javaPrimitiveType -> 0f
        else -> null
    }

    private fun coerceArgument(
        desc: ParamDescriptor,
        jvmType: Class<*>,
        genericType: Type,
        elem: JsonElement
    ): Any? {
        if (elem is JsonNull) return defaultMissingValue(jvmType)

        return when {
            jvmType == JsonElement::class.java -> elem
            jvmType == JsonObject::class.java -> (elem as? JsonObject)
                ?: throw RpcException(RpcErrorCodes.INVALID_PARAMS, "Parameter '${desc.name}' must be a JSON object")
            jvmType == JsonArray::class.java -> (elem as? JsonArray)
                ?: throw RpcException(RpcErrorCodes.INVALID_PARAMS, "Parameter '${desc.name}' must be a JSON array")
            jvmType == String::class.java -> {
                val str = (elem as? JsonPrimitive)?.contentOrNull ?: elem.toString()
                desc.enumValues?.takeIf { it.isNotEmpty() }?.let { allowed ->
                    if (str !in allowed) {
                        throw RpcException(
                            RpcErrorCodes.INVALID_PARAMS,
                            "Parameter '${desc.name}' must be one of: ${allowed.joinToString(", ")}"
                        )
                    }
                }
                str
            }
            jvmType == Int::class.javaPrimitiveType || jvmType == Int::class.javaObjectType -> {
                val prim = elem as? JsonPrimitive
                prim?.intOrNull
                    ?: prim?.doubleOrNull?.toInt()
                    ?: prim?.contentOrNull?.trim()?.toIntOrNull()
                    ?: throw RpcException(RpcErrorCodes.INVALID_PARAMS, "Parameter '${desc.name}' must be an integer")
            }
            jvmType == Long::class.javaPrimitiveType || jvmType == Long::class.javaObjectType -> {
                val prim = elem as? JsonPrimitive
                prim?.longOrNull
                    ?: prim?.doubleOrNull?.toLong()
                    ?: prim?.contentOrNull?.trim()?.toLongOrNull()
                    ?: throw RpcException(RpcErrorCodes.INVALID_PARAMS, "Parameter '${desc.name}' must be a long integer")
            }
            jvmType == Double::class.javaPrimitiveType || jvmType == Double::class.javaObjectType -> {
                val prim = elem as? JsonPrimitive
                prim?.doubleOrNull
                    ?: prim?.contentOrNull?.trim()?.toDoubleOrNull()
                    ?: throw RpcException(RpcErrorCodes.INVALID_PARAMS, "Parameter '${desc.name}' must be a number")
            }
            jvmType == Float::class.javaPrimitiveType || jvmType == Float::class.javaObjectType -> {
                val prim = elem as? JsonPrimitive
                prim?.floatOrNull
                    ?: prim?.contentOrNull?.trim()?.toFloatOrNull()
                    ?: throw RpcException(RpcErrorCodes.INVALID_PARAMS, "Parameter '${desc.name}' must be a number")
            }
            jvmType == Boolean::class.javaPrimitiveType || jvmType == Boolean::class.javaObjectType -> {
                val prim = elem as? JsonPrimitive
                prim?.booleanOrNull
                    ?: prim?.contentOrNull?.trim()?.lowercase()?.toBooleanStrictOrNull()
                    ?: throw RpcException(RpcErrorCodes.INVALID_PARAMS, "Parameter '${desc.name}' must be a boolean")
            }
            else -> {
                // Support @Serializable DTOs and typed collections (List<MyDto>, Map<String, T>, etc.)
                val serializer = runCatching { serializerOrNull(genericType) }.getOrNull()
                    ?: runCatching { serializerOrNull(jvmType) }.getOrNull()
                if (serializer != null) {
                    try {
                        return RpcJson.json.decodeFromJsonElement(serializer, elem)
                    } catch (e: SerializationException) {
                        throw RpcException(
                            RpcErrorCodes.INVALID_PARAMS,
                            "Invalid value for parameter '${desc.name}': ${e.message}"
                        )
                    }
                }

                if (List::class.java.isAssignableFrom(jvmType)) {
                    val arr = elem as? JsonArray
                        ?: throw RpcException(RpcErrorCodes.INVALID_PARAMS, "Parameter '${desc.name}' must be an array")
                    return arr.mapNotNull { item ->
                        when (item) {
                            is JsonPrimitive -> item.contentOrNull
                            is JsonNull -> null
                            else -> item.toString()
                        }
                    }
                }

                throw RpcException(
                    RpcErrorCodes.INVALID_PARAMS,
                    "Unsupported parameter type '${jvmType.simpleName}' for '${desc.name}'"
                )
            }
        }
    }

    private fun invokeRegularMethod(method: Method, target: Any, args: Array<Any?>): Any? {
        return try {
            method.invoke(target, *args)
        } catch (ite: InvocationTargetException) {
            throw (ite.targetException ?: ite)
        }
    }

    private suspend fun invokeSuspendMethod(method: Method, target: Any, args: Array<Any?>): Any? {
        return suspendCancellableCoroutine { cont ->
            val callArgs = arrayOfNulls<Any?>(args.size + 1)
            System.arraycopy(args, 0, callArgs, 0, args.size)
            callArgs[args.size] = object : Continuation<Any?> {
                override val context: CoroutineContext = cont.context
                override fun resumeWith(result: Result<Any?>) {
                    result.fold(
                        onSuccess = { if (cont.isActive) cont.resume(it) },
                        onFailure = { if (cont.isActive) cont.resumeWithException(it) }
                    )
                }
            }

            try {
                val directResult = method.invoke(target, *callArgs)
                if (directResult !== COROUTINE_SUSPENDED) {
                    if (cont.isActive) cont.resume(directResult)
                }
            } catch (ite: InvocationTargetException) {
                val cause = ite.targetException ?: ite
                if (cont.isActive) cont.resumeWithException(cause)
            } catch (t: Throwable) {
                if (cont.isActive) cont.resumeWithException(t)
            }
        }
    }

    private fun coerceReturnValue(value: Any?, declaredReturnType: Type): RpcResult {
        resultMapper?.invoke(value)?.let { return it }
        return when (value) {
            null, Unit -> RpcResult.Success(JsonObject(emptyMap()))
            is RpcResult -> value
            is JsonElement -> RpcResult.Success(value)
            is String -> RpcResult.Success(JsonPrimitive(value))
            is Boolean -> RpcResult.Success(JsonPrimitive(value))
            is Number -> RpcResult.Success(JsonPrimitive(value))
            else -> RpcResult.Success(RpcJson.encodeAny(value, declaredReturnType))
        }
    }

    companion object {
        private fun extractSuspendReturnType(continuationGenericType: Type?): Type? {
            val pt = continuationGenericType as? ParameterizedType ?: return null
            val arg = pt.actualTypeArguments.firstOrNull() ?: return null
            return when (arg) {
                is WildcardType -> arg.lowerBounds.firstOrNull() ?: arg.upperBounds.firstOrNull()
                else -> arg
            }
        }

        private fun inferParameterType(jvmType: Class<*>): ParameterType = when {
            jvmType == String::class.java -> ParameterType.STRING
            jvmType == Int::class.javaPrimitiveType ||
                jvmType == Int::class.javaObjectType ||
                jvmType == Long::class.javaPrimitiveType ||
                jvmType == Long::class.javaObjectType -> ParameterType.INTEGER
            jvmType == Double::class.javaPrimitiveType ||
                jvmType == Double::class.javaObjectType ||
                jvmType == Float::class.javaPrimitiveType ||
                jvmType == Float::class.javaObjectType -> ParameterType.NUMBER
            jvmType == Boolean::class.javaPrimitiveType ||
                jvmType == Boolean::class.javaObjectType -> ParameterType.BOOLEAN
            jvmType == JsonObject::class.java -> ParameterType.OBJECT
            jvmType == JsonArray::class.java || List::class.java.isAssignableFrom(jvmType) -> ParameterType.ARRAY
            jvmType.isAnnotationPresent(Serializable::class.java) || Map::class.java.isAssignableFrom(jvmType) -> ParameterType.OBJECT
            else -> ParameterType.STRING
        }

        private fun inferItemsType(genericType: java.lang.reflect.Type?): ParameterType {
            val pt = genericType as? ParameterizedType ?: return ParameterType.STRING
            val arg = pt.actualTypeArguments.firstOrNull() as? Class<*> ?: return ParameterType.STRING
            return inferParameterType(arg)
        }
    }
}
