package com.damn.aisuper.modules.impl.http

import com.damn.aisuper.modules.FeatureModule
import com.damn.aisuper.modules.FeatureModuleContext
import com.damn.aisuper.modules.FeatureModuleFactory
import com.damn.aisuper.runtime.ModuleDefinition
import kotlinx.serialization.json.JsonElement
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


