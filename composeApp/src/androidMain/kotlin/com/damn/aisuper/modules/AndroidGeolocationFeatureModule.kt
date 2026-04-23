package com.damn.aisuper.modules

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.damn.aisuper.runtime.ModuleDefinition
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class AndroidGeolocationFeatureModule : FeatureModule {
    override suspend fun attach(context: FeatureModuleContext) {
        context.registerFunction("geoRequestPermission") { _ ->
            JsonPrimitive(requestLocationPermissionIfNeeded())
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

    private fun requestLocationPermissionIfNeeded(): Boolean {
        val context = AndroidAppContextHolder.appContext ?: return false
        if (hasLocationPermission(context)) return true

        val activity = AndroidAppContextHolder.currentActivity ?: return false
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )

        // The user decision arrives asynchronously; caller should retry geolocation after prompt.
        return false
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

private const val LOCATION_PERMISSION_REQUEST_CODE = 1107

object AndroidGeolocationFeatureModuleFactory : FeatureModuleFactory {
    override val type: String = "geolocation"

    override fun create(definitions: List<ModuleDefinition>): FeatureModule {
        return AndroidGeolocationFeatureModule()
    }
}

