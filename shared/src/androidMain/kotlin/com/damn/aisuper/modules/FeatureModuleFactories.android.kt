package com.damn.aisuper.modules

import com.damn.aisuper.modules.impl.geolocation.android.AndroidGeolocationFeatureModuleFactory

actual fun platformFeatureModuleFactories(): List<FeatureModuleFactory> = listOf(
	AndroidGeolocationFeatureModuleFactory
)

