package com.damn.aisuper.rpc

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import com.damn.aisuper.toolbox.ParameterType
import com.damn.aisuper.toolbox.ToolParameter
import com.damn.aisuper.toolbox.ToolProviderKind
import com.damn.aisuper.toolbox.ToolResult

enum class SurfaceKind(val wireName: String) {
    OS("os"),
    NATIVE_MODULE("native_module"),
    JS_POD("js_pod"),
    REMOTE_POD("remote_pod"),
    PLUGIN("plugin");

    fun toToolProviderKind(): ToolProviderKind = when (this) {
        OS -> ToolProviderKind.OS
        NATIVE_MODULE -> ToolProviderKind.NATIVE_MODULE
        JS_POD -> ToolProviderKind.JS_POD
        REMOTE_POD, PLUGIN -> ToolProviderKind.PLUGIN
    }

    companion object {
        fun fromWireName(raw: String?): SurfaceKind = when (raw?.trim()?.lowercase()) {
            "os" -> OS
            "js_pod" -> JS_POD
            "remote_pod" -> REMOTE_POD
            "plugin" -> PLUGIN
            else -> NATIVE_MODULE
        }
    }
}

enum class SurfaceLocation(val wireName: String) {
    LOCAL("local"),
    REMOTE("remote");

    companion object {
        fun fromWireName(raw: String?): SurfaceLocation = when (raw?.trim()?.lowercase()) {
            "remote" -> REMOTE
            else -> LOCAL
        }
    }
}

data class ParamDescriptor(
    val name: String,
    val type: ParameterType,
    val description: String = "",
    val required: Boolean = true,
    val position: Int = 0,
    val enumValues: List<String>? = null,
    val itemsType: ParameterType? = null
) {
    fun toToolParameter(): ToolParameter = ToolParameter(
        name = name,
        type = type,
        description = description,
        required = required,
        enumValues = enumValues,
        itemsType = itemsType
    )

    fun toJson(): JsonObject = buildJsonObject {
        put("name", name)
        put("type", type.schemaType)
        put("description", description)
        put("required", required)
        put("position", position)
        enumValues?.takeIf { it.isNotEmpty() }?.let { values ->
            put("enum", buildJsonArray {
                values.forEach { add(JsonPrimitive(it)) }
            })
        }
        if (type == ParameterType.ARRAY) {
            put("itemsType", (itemsType ?: ParameterType.STRING).schemaType)
        }
    }

    companion object {
        fun fromToolParameter(param: ToolParameter, position: Int): ParamDescriptor = ParamDescriptor(
            name = param.name,
            type = param.type,
            description = param.description,
            required = param.required,
            position = position,
            enumValues = param.enumValues,
            itemsType = param.itemsType
        )

        fun fromJson(obj: JsonObject, fallbackPosition: Int): ParamDescriptor? {
            val name = (obj["name"] as? JsonPrimitive)?.contentOrNull?.trim()
                ?.takeIf { it.isNotEmpty() } ?: return null
            val type = ParameterType.fromSchemaType((obj["type"] as? JsonPrimitive)?.contentOrNull)
            val desc = (obj["description"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            val req = (obj["required"] as? JsonPrimitive)?.booleanOrNull ?: true
            val pos = (obj["position"] as? JsonPrimitive)?.intOrNull ?: fallbackPosition
            val enumValues = ((obj["enum"] as? JsonArray) ?: (obj["enumValues"] as? JsonArray))
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                ?.takeIf { it.isNotEmpty() }
            val itemsType = (obj["itemsType"] as? JsonPrimitive)?.contentOrNull
                ?.let { ParameterType.fromSchemaType(it) }
            return ParamDescriptor(
                name = name,
                type = type,
                description = desc,
                required = req,
                position = pos,
                enumValues = enumValues,
                itemsType = itemsType
            )
        }
    }
}

data class MethodDescriptor(
    val name: String,
    val qualifiedName: String,
    val aliases: List<String> = emptyList(),
    val description: String = "",
    val parameters: List<ParamDescriptor> = emptyList(),
    val returnsDescription: String? = null,
    val exposeInMcp: Boolean = true,
    val readOnly: Boolean = false
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("name", name)
        put("qualifiedName", qualifiedName)
        if (aliases.isNotEmpty()) {
            put("aliases", buildJsonArray {
                aliases.forEach { add(JsonPrimitive(it)) }
            })
        }
        put("description", description)
        put("exposeInMcp", exposeInMcp)
        put("readOnly", readOnly)
        returnsDescription?.takeIf { it.isNotBlank() }?.let { put("returns", it) }
        put("parameters", buildJsonArray {
            parameters.forEach { add(it.toJson()) }
        })
    }

    companion object {
        fun fromJson(surfaceName: String, obj: JsonObject): MethodDescriptor? {
            val rawName = (obj["name"] as? JsonPrimitive)?.contentOrNull?.trim()
                ?.takeIf { it.isNotEmpty() } ?: return null
            val localName = if (rawName.startsWith("$surfaceName.")) {
                rawName.removePrefix("$surfaceName.")
            } else {
                rawName
            }
            val qualified = (obj["qualifiedName"] as? JsonPrimitive)?.contentOrNull?.trim()
                ?.takeIf { it.isNotEmpty() } ?: "$surfaceName.$localName"
            val aliases = (obj["aliases"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { s -> s.isNotEmpty() } }
                ?: emptyList()
            val desc = (obj["description"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            val expose = (obj["exposeInMcp"] as? JsonPrimitive)?.booleanOrNull ?: true
            val readOnly = (obj["readOnly"] as? JsonPrimitive)?.booleanOrNull ?: false
            val returnsDesc = (obj["returns"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            val paramsArr = obj["parameters"] as? JsonArray ?: JsonArray(emptyList())
            val params = paramsArr.mapIndexedNotNull { idx, elem ->
                (elem as? JsonObject)?.let { ParamDescriptor.fromJson(it, idx) }
            }
            return MethodDescriptor(
                name = localName,
                qualifiedName = qualified,
                aliases = aliases,
                description = desc,
                parameters = params,
                returnsDescription = returnsDesc,
                exposeInMcp = expose,
                readOnly = readOnly
            )
        }
    }
}

data class SurfaceDescriptor(
    val name: String,
    val description: String,
    val kind: SurfaceKind,
    val location: SurfaceLocation = SurfaceLocation.LOCAL,
    val version: String = "1.0.0",
    val endpoint: String? = null,
    val methods: List<MethodDescriptor> = emptyList()
) {
    fun toJson(baseUrl: String = ""): JsonObject {
        val cleanBase = baseUrl.trimEnd('/')
        val rpcPath = "/api/rpc/$name"
        val wsPath = "/api/rpc/ws/$name"
        val mcpPath = "/api/mcp/$name"
        return buildJsonObject {
            put("name", name)
            put("description", description)
            put("kind", kind.wireName)
            put("location", location.wireName)
            put("version", version)
            endpoint?.takeIf { it.isNotBlank() }?.let { put("remoteEndpoint", it) }
            put("rpcEndpoint", if (cleanBase.isNotEmpty()) "$cleanBase$rpcPath" else rpcPath)
            put("wsEndpoint", if (cleanBase.isNotEmpty()) "${cleanBase.replaceFirst("http", "ws")}$wsPath" else wsPath)
            put("mcpEndpoint", if (cleanBase.isNotEmpty()) "$cleanBase$mcpPath" else mcpPath)
            put("methodCount", methods.size)
            put("mcpToolCount", methods.count { it.exposeInMcp })
            put("methods", buildJsonArray {
                methods.forEach { add(it.toJson()) }
            })
        }
    }

    companion object {
        fun fromJson(obj: JsonObject, overrideLocation: SurfaceLocation? = null, overrideEndpoint: String? = null): SurfaceDescriptor? {
            val name = (obj["name"] as? JsonPrimitive)?.contentOrNull?.trim()
                ?.takeIf { it.isNotEmpty() } ?: return null
            val desc = (obj["description"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            val kind = SurfaceKind.fromWireName((obj["kind"] as? JsonPrimitive)?.contentOrNull)
            val location = overrideLocation ?: SurfaceLocation.fromWireName((obj["location"] as? JsonPrimitive)?.contentOrNull)
            val version = (obj["version"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: "1.0.0"
            val endpoint = overrideEndpoint ?: (obj["remoteEndpoint"] as? JsonPrimitive)?.contentOrNull?.trim()
            val methodsArr = obj["methods"] as? JsonArray ?: JsonArray(emptyList())
            val methods = methodsArr.mapNotNull { elem ->
                (elem as? JsonObject)?.let { MethodDescriptor.fromJson(name, it) }
            }
            return SurfaceDescriptor(
                name = name,
                description = desc,
                kind = kind,
                location = location,
                version = version,
                endpoint = endpoint,
                methods = methods
            )
        }
    }
}

sealed class RpcParams {
    data object Empty : RpcParams()
    data class Named(val values: JsonObject) : RpcParams()
    data class Positional(val values: JsonArray) : RpcParams()

    fun toNamedObject(descriptors: List<ParamDescriptor>): JsonObject = when (this) {
        Empty -> JsonObject(emptyMap())
        is Named -> values
        is Positional -> buildJsonObject {
            val ordered = descriptors.sortedBy { it.position }
            for (idx in values.indices) {
                val target = ordered.getOrNull(idx) ?: break
                put(target.name, values[idx])
            }
        }
    }

    fun toJsonElement(): JsonElement = when (this) {
        Empty -> JsonObject(emptyMap())
        is Named -> values
        is Positional -> values
    }

    companion object {
        fun fromJsonElement(element: JsonElement?): RpcParams = when (element) {
            null, is JsonNull -> Empty
            is JsonObject -> Named(element)
            is JsonArray -> Positional(element)
            else -> throw RpcException(RpcErrorCodes.INVALID_PARAMS, "'params' must be a JSON object or array")
        }

        fun fromNullableObject(obj: JsonObject?): RpcParams =
            if (obj == null || obj.isEmpty()) Empty else Named(obj)
    }
}

data class RpcScope(
    val toolboxId: String? = null,
    val allowedSurfaces: Set<String>? = null,
    val defaultSurface: String? = null,
    val readOnlyOnly: Boolean = false,
    val disabledMethods: Set<String> = emptySet()
) {
    fun allowsSurface(surfaceName: String): Boolean =
        allowedSurfaces == null || surfaceName in allowedSurfaces

    fun allowsMethod(surfaceName: String, method: MethodDescriptor): Boolean {
        if (!allowsSurface(surfaceName)) return false
        if (readOnlyOnly && !method.readOnly) return false
        if (method.qualifiedName in disabledMethods) return false
        if ("$surfaceName.${method.name}" in disabledMethods) return false
        return true
    }

    fun intersectSurface(surfaceName: String?): RpcScope {
        val clean = surfaceName?.trim()?.takeIf { it.isNotEmpty() } ?: return this
        val nextAllowed = if (allowedSurfaces == null) {
            setOf(clean)
        } else if (clean in allowedSurfaces) {
            setOf(clean)
        } else {
            emptySet()
        }
        return copy(allowedSurfaces = nextAllowed, defaultSurface = clean)
    }

    companion object {
        val ALL = RpcScope()

        fun fromSurfaceFilter(surfaceScope: String?): RpcScope {
            val clean = surfaceScope?.trim()?.takeIf { it.isNotEmpty() } ?: return ALL
            return RpcScope(
                allowedSurfaces = setOf(clean),
                defaultSurface = clean
            )
        }
    }
}

data class RpcCallContext(
    val caller: String = "local",
    val transport: String = "direct",
    val remoteIp: String? = null,
    val peerKeyId: String? = null
) {
    companion object {
        val LOCAL = RpcCallContext(caller = "local", transport = "direct")
        val MCP = RpcCallContext(caller = "agent", transport = "mcp")
        val BRIDGE = RpcCallContext(caller = "web", transport = "bridge")
    }
}

object RpcErrorCodes {
    const val PARSE_ERROR = -32700
    const val INVALID_REQUEST = -32600
    const val METHOD_NOT_FOUND = -32601
    const val INVALID_PARAMS = -32602
    const val INTERNAL_ERROR = -32603
}

class RpcException(
    val code: Int,
    override val message: String,
    val data: JsonElement? = null
) : RuntimeException(message)

sealed class RpcResult {
    data class Success(val data: JsonElement) : RpcResult()
    data class Error(
        val code: Int,
        val message: String,
        val data: JsonElement? = null
    ) : RpcResult()

    fun toToolResult(): ToolResult = when (this) {
        is Success -> ToolResult.json(data)
        is Error -> ToolResult.error(message)
    }
}
