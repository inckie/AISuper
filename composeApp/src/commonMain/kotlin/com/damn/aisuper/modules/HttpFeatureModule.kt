package com.damn.aisuper.modules

import com.damn.aisuper.runtime.ModuleDefinition
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class HttpFeatureModule : FeatureModule {
    override suspend fun attach(context: FeatureModuleContext) {
        context.registerSuspendFunction("httpGet") { args ->
            val url = args.firstOrNull()?.jsonPrimitiveContentOrNull() ?: return@registerSuspendFunction JsonPrimitive("")
            JsonPrimitive(HttpComponent.get(url))
        }
    }

    override fun detach() = Unit
}

object HttpFeatureModuleFactory : FeatureModuleFactory {
    override val type: String = "http"

    override fun create(definitions: List<ModuleDefinition>): FeatureModule {
        return HttpFeatureModule()
    }
}

private fun kotlinx.serialization.json.JsonElement.jsonPrimitiveContentOrNull(): String? {
    return try {
        this.jsonPrimitive.contentOrNull
    } catch (_: Exception) {
        null
    }
}

