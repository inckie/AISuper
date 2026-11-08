package com.damn.aisuper.modules.impl.geolocation.android

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.damn.aisuper.modules.FeatureModule
import com.damn.aisuper.modules.FeatureModuleContext
import com.damn.aisuper.modules.FeatureModuleFactory
import com.damn.aisuper.modules.impl.platform.android.AndroidAppContextHolder
import com.damn.aisuper.runtime.ModuleDefinition
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class AndroidGeolocationFeatureModule : FeatureModule {
    override suspend fun attach(context: FeatureModuleContext) {
        context.registerSuspendFunction("geoRequestPermission") { _ ->
            JsonPrimitive(requestLocationPermissionAsync())
        }

        context.registerSuspendFunction("geoGetCurrent") { _ ->
            resolveCurrentLocation()
        }
    }

    override fun detach() = Unit

    private fun resolveCurrentLocation(): JsonObject {
        val context = AndroidAppContextHolder.appContext
            ?: return locationError("Android context is not available")

        if (!hasLocationPermission(context)) {
            return locationError("Location permission is not granted")
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return locationError("LocationManager is not available")

        @SuppressLint("MissingPermission")
        val location = findBestLastKnownLocation(locationManager)
            ?: return locationError("No last known location available")

        return buildJsonObject {
            put("success", JsonPrimitive(true))
            put("source", JsonPrimitive("android-os"))
            put("provider", JsonPrimitive(location.provider ?: ""))
            put("latitude", JsonPrimitive(location.latitude))
            put("longitude", JsonPrimitive(location.longitude))
            put("accuracyMeters", JsonPrimitive(location.accuracy.toDouble()))
            put("timeMs", JsonPrimitive(location.time))
            put("error", JsonNull)
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun findBestLastKnownLocation(locationManager: LocationManager): Location? {
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        return providers
            .mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    private fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fine || coarse
    }

    private suspend fun requestLocationPermissionAsync(): Boolean {
        val context = AndroidAppContextHolder.appContext ?: return false
        if (hasLocationPermission(context)) return true

        return AndroidAppContextHolder.requestPermissions(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun locationError(message: String): JsonObject {
        return buildJsonObject {
            put("success", JsonPrimitive(false))
            put("source", JsonPrimitive("android-os"))
            put("provider", JsonNull)
            put("latitude", JsonNull)
            put("longitude", JsonNull)
            put("accuracyMeters", JsonNull)
            put("timeMs", JsonNull)
            put("error", JsonPrimitive(message))
        }
    }
}

object AndroidGeolocationFeatureModuleFactory : FeatureModuleFactory {
    override val type: String = "geolocation"

    override suspend fun create(definition: ModuleDefinition): FeatureModule {
        return AndroidGeolocationFeatureModule()
    }
}


