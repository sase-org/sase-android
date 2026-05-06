package org.sase.mobile.data.api.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

object GatewayJson {
    @OptIn(ExperimentalSerializationApi::class)
    val format: Json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
    }
}
