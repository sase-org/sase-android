package org.sase.mobile.data.session

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.sase.mobile.data.api.GatewayApiClient

object QrPairingPayloadParser {
    const val JsonType = "sase_mobile_pair"
    const val UriScheme = "sase"
    const val UriHost = "pair"

    private val json = Json { ignoreUnknownKeys = false }

    fun parse(rawPayload: String): QrPairingPayload {
        val payload = rawPayload.trim()
        require(payload.isNotEmpty()) { "QR payload is empty" }
        return if (payload.startsWith("{")) {
            parseJson(payload)
        } else {
            parseUri(payload)
        }.validated()
    }

    private fun parseJson(payload: String): QrPairingPayload {
        val obj = try {
            json.parseToJsonElement(payload) as? JsonObject
        } catch (error: SerializationException) {
            throw IllegalArgumentException("QR payload is not valid JSON", error)
        } ?: throw IllegalArgumentException("QR payload must be a JSON object")

        require(obj.string("type") == JsonType) { "QR payload type is not supported" }
        require(obj.int("schema_version") == 1) { "QR payload schema version is not supported" }
        rejectUnexpectedKeys(
            keys = obj.keys,
            allowed = setOf("schema_version", "type", "base_url", "pairing_id", "code", "host_label"),
        )
        return QrPairingPayload(
            baseUrl = obj.requiredString("base_url"),
            pairingId = obj.requiredString("pairing_id"),
            code = obj.requiredString("code"),
            hostLabel = obj.string("host_label"),
        )
    }

    private fun parseUri(payload: String): QrPairingPayload {
        val uri = try {
            URI(payload)
        } catch (error: Exception) {
            throw IllegalArgumentException("QR URI is invalid", error)
        }
        require(uri.scheme == UriScheme && uri.host == UriHost && uri.path.isNullOrEmpty()) {
            "QR URI must use sase://pair"
        }
        val queryParameters = uri.queryParameters()
        rejectUnexpectedKeys(
            keys = queryParameters.keys,
            allowed = setOf("base_url", "pairing_id", "code", "host_label"),
        )
        return QrPairingPayload(
            baseUrl = queryParameters.requiredQuery("base_url"),
            pairingId = queryParameters.requiredQuery("pairing_id"),
            code = queryParameters.requiredQuery("code"),
            hostLabel = queryParameters["host_label"]?.takeIf { it.isNotBlank() },
        )
    }

    private fun QrPairingPayload.validated(): QrPairingPayload {
        GatewayApiClient.normalizeBaseUrl(baseUrl)
        require(pairingId.isNotBlank()) { "Pairing ID is required" }
        require(code.isNotBlank()) { "Pairing code is required" }
        require(!baseUrl.contains('\n') && !pairingId.contains('\n') && !code.contains('\n')) {
            "QR payload fields must be single-line values"
        }
        return copy(
            baseUrl = GatewayApiClient.normalizeBaseUrl(baseUrl).toString(),
            pairingId = pairingId.trim(),
            code = code.trim(),
            hostLabel = hostLabel?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    private fun rejectUnexpectedKeys(keys: Set<String>, allowed: Set<String>) {
        val unexpected = keys - allowed
        require(unexpected.isEmpty()) { "QR payload contains unsupported fields: ${unexpected.sorted()}" }
    }

    private fun JsonObject.requiredString(name: String): String {
        return string(name)?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("QR payload is missing $name")
    }

    private fun JsonObject.string(name: String): String? {
        return (this[name] as? JsonPrimitive)?.contentOrNull
    }

    private fun JsonObject.int(name: String): Int? {
        return this[name]?.jsonPrimitive?.intOrNull
    }

    private fun URI.queryParameters(): Map<String, String> {
        return rawQuery.orEmpty()
            .split("&")
            .filter { it.isNotBlank() }
            .associate { parameter ->
                val parts = parameter.split("=", limit = 2)
                val name = parts[0].urlDecode()
                val value = parts.getOrElse(1) { "" }.urlDecode()
                name to value
            }
    }

    private fun String.urlDecode(): String {
        return URLDecoder.decode(this, StandardCharsets.UTF_8.name())
    }

    private fun Map<String, String>.requiredQuery(name: String): String {
        return this[name]?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("QR payload is missing $name")
    }
}
