package com.damn.aisuper.modules.impl.http

import com.damn.aisuper.modules.FeatureModule
import com.damn.aisuper.modules.FeatureModuleContext
import com.damn.aisuper.modules.FeatureModuleFactory
import com.damn.aisuper.runtime.ModuleDefinition
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class HttpFeatureModule : FeatureModule {
    override suspend fun attach(context: FeatureModuleContext) {
        context.registerSuspendFunction("httpGet") { args ->
            val url = args.getOrNull(0)?.jsonPrimitiveContentOrNull() ?: return@registerSuspendFunction JsonPrimitive("")
            val headers = extractHeaders(args.getOrNull(1))
            JsonPrimitive(HttpComponent.get(url, headers))
        }

        context.registerSuspendFunction("httpPost") { args ->
            val url = args.getOrNull(0)?.jsonPrimitiveContentOrNull() ?: return@registerSuspendFunction JsonPrimitive("")
            val body = args.getOrNull(1)?.jsonPrimitiveContentOrNull() ?: ""
            val headers = extractHeaders(args.getOrNull(2))
            JsonPrimitive(HttpComponent.post(url, body, headers))
        }
    }

    override fun detach() = Unit

    private fun extractHeaders(element: JsonElement?): Map<String, String> {
        if (element == null) return emptyMap()
        return try {
            val obj = element.jsonObject
            obj.mapValues { (_, value) ->
                value.jsonPrimitive.contentOrNull ?: ""
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }
}

object HttpFeatureModuleFactory : FeatureModuleFactory {
    override val type: String = "http"

    override suspend fun create(definition: ModuleDefinition): FeatureModule {
        return HttpFeatureModule()
    }
}

private fun JsonElement.jsonPrimitiveContentOrNull(): String? {
    return try {
        this.jsonPrimitive.contentOrNull
    } catch (_: Exception) {
        null
    }
}


