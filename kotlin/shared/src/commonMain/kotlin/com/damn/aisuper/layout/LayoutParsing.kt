package com.damn.aisuper.layout

import kotlinx.serialization.json.Json

private val layoutJson = Json { ignoreUnknownKeys = true }

fun parseLayout(jsonString: String): LayoutRoot =
    layoutJson.decodeFromString<LayoutRoot>(jsonString)

