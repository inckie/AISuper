package com.damn.aisuper.modules.impl.geolocation

import com.damn.aisuper.modules.FeatureModule
import com.damn.aisuper.modules.FeatureModuleContext
import com.damn.aisuper.modules.FeatureModuleFactory
import com.damn.aisuper.runtime.ModuleDefinition
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

private val geoJson = Json { ignoreUnknownKeys = true }

/**
 * Switch GeoIP provider at build time by changing this constant.
 * Supported values: HACKER_TARGET, IPWHOIS
 */
private const val GEOIP_PROVIDER_NAME = "IPWHOIS"

class GeoIpGeolocationFeatureModule : FeatureModule {
    private val resolver = GeoIpResolver()

    override suspend fun attach(context: FeatureModuleContext) {
        context.registerFunction("geoRequestPermission") { _ ->
            JsonPrimitive(true)
        }

        context.registerSuspendFunction("geoGetCurrent") { args ->
            val ipOverride = args.firstOrNull()?.stringOrNull()
            val result = resolver.resolve(ipOverride)
            result.toJson()
        }
    }

    override fun detach() {
        resolver.close()
    }
}

object GeoIpGeolocationFeatureModuleFactory : FeatureModuleFactory {
    override val type: String = "geolocation"

    override fun create(definitions: List<ModuleDefinition>): FeatureModule {
        return GeoIpGeolocationFeatureModule()
    }
}

private class GeoIpResolver {
    private val client = HttpClient()

    suspend fun resolve(ipOverride: String?): GeoLocationResult {
        return try {
            val ip = ipOverride?.takeIf { it.isNotBlank() } ?: resolvePublicIp()
            when (selectedProvider()) {
                GeoIpProvider.HACKER_TARGET -> resolveViaHackerTarget(ip)
                GeoIpProvider.IPWHOIS -> resolveViaIpWhoIs(ip)
            }
        } catch (e: Exception) {
            GeoLocationResult(
                success = false,
                source = "geoip",
                error = e.message ?: "Unknown GeoIP error"
            )
        }
    }

    fun close() {
        client.close()
    }

    private suspend fun resolvePublicIp(): String {
        return client.get("https://api.ipify.org").bodyAsText().trim()
    }

    private suspend fun resolveViaHackerTarget(ip: String): GeoLocationResult {
        val body = client.get("https://api.hackertarget.com/geoip/?q=$ip&output=json").bodyAsText()
        val obj = geoJson.parseToJsonElement(body) as? JsonObject
            ?: return GeoLocationResult(false, "geoip:hackertarget", error = "Invalid GeoIP response")

        return GeoLocationResult(
            success = true,
            source = "geoip:hackertarget",
            ip = obj.string("ip") ?: ip,
            city = obj.string("city"),
            region = obj.string("state"),
            country = obj.string("country"),
            countryCode = obj.string("cc"),
            latitude = obj.number("latitude"),
            longitude = obj.number("longitude")
        )
    }

    private suspend fun resolveViaIpWhoIs(ip: String): GeoLocationResult {
        val body = client.get("https://ipwho.is/$ip").bodyAsText()
        val obj = geoJson.parseToJsonElement(body) as? JsonObject
            ?: return GeoLocationResult(false, "geoip:ipwhois", error = "Invalid GeoIP response")

        val success = obj.boolean("success") ?: false
        if (!success) {
            return GeoLocationResult(
                success = false,
                source = "geoip:ipwhois",
                ip = obj.string("ip") ?: ip,
                error = obj.string("message") ?: "ipwho.is returned unsuccessful response"
            )
        }

        return GeoLocationResult(
            success = true,
            source = "geoip:ipwhois",
            ip = obj.string("ip") ?: ip,
            city = obj.string("city"),
            region = obj.string("region"),
            country = obj.string("country"),
            countryCode = obj.string("country_code"),
            latitude = obj.number("latitude"),
            longitude = obj.number("longitude")
        )
    }

    private fun selectedProvider(): GeoIpProvider {
        return when (GEOIP_PROVIDER_NAME.uppercase()) {
            "HACKER_TARGET" -> GeoIpProvider.HACKER_TARGET
            else -> GeoIpProvider.IPWHOIS
        }
    }
}

private enum class GeoIpProvider {
    HACKER_TARGET,
    IPWHOIS
}

private data class GeoLocationResult(
    val success: Boolean,
    val source: String,
    val ip: String? = null,
    val city: String? = null,
    val region: String? = null,
    val country: String? = null,
    val countryCode: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val error: String? = null
) {
    fun toJson(): JsonObject {
        return buildJsonObject {
            put("success", JsonPrimitive(success))
            put("source", JsonPrimitive(source))
            put("ip", ip?.let(::JsonPrimitive) ?: JsonNull)
            put("city", city?.let(::JsonPrimitive) ?: JsonNull)
            put("region", region?.let(::JsonPrimitive) ?: JsonNull)
            put("country", country?.let(::JsonPrimitive) ?: JsonNull)
            put("countryCode", countryCode?.let(::JsonPrimitive) ?: JsonNull)
            put("latitude", latitude?.let(::JsonPrimitive) ?: JsonNull)
            put("longitude", longitude?.let(::JsonPrimitive) ?: JsonNull)
            put("error", error?.let(::JsonPrimitive) ?: JsonNull)
        }
    }
}

private fun List<JsonElement>.stringOrNull(index: Int): String? {
    return getOrNull(index)?.stringOrNull()
}

private fun JsonElement.stringOrNull(): String? {
    return try {
        jsonPrimitive.contentOrNull
    } catch (_: Exception) {
        null
    }
}

private fun JsonObject.string(key: String): String? {
    return this[key]?.let {
        try {
            it.jsonPrimitive.contentOrNull
        } catch (_: Exception) {
            null
        }
    }
}

private fun JsonObject.number(key: String): Double? {
    return this[key]?.let {
        try {
            it.jsonPrimitive.doubleOrNull
        } catch (_: Exception) {
            null
        }
    }
}

private fun JsonObject.boolean(key: String): Boolean? {
    return this[key]?.let {
        try {
            it.jsonPrimitive.booleanOrNull
        } catch (_: Exception) {
            null
        }
    }
}


