package org.sase.mobile.data.helpers

import org.sase.mobile.data.api.GatewayApiClient
import org.sase.mobile.data.api.GatewayApiError
import org.sase.mobile.data.api.GatewayApiResult
import org.sase.mobile.data.api.dto.MobileBeadListRequestWire
import org.sase.mobile.data.api.dto.MobileBeadListResponseWire
import org.sase.mobile.data.api.dto.MobileBeadShowRequestWire
import org.sase.mobile.data.api.dto.MobileBeadShowResponseWire
import org.sase.mobile.data.api.dto.MobileChangeSpecTagListRequestWire
import org.sase.mobile.data.api.dto.MobileChangeSpecTagListResponseWire
import org.sase.mobile.data.api.dto.MobileXpromptCatalogRequestWire
import org.sase.mobile.data.api.dto.MobileXpromptCatalogResponseWire
import org.sase.mobile.data.session.HostSessionStorage
import org.sase.mobile.data.session.TokenVault

class HelperRepository(
    private val sessionStorage: HostSessionStorage,
    private val tokenVault: TokenVault,
    private val clientFactory: (baseUrl: String, tokenProvider: () -> String?) -> GatewayApiClient,
) {
    suspend fun changespecTags(
        project: String? = null,
        limit: Int? = null,
    ): HelperLoadResult<MobileChangeSpecTagListResponseWire> {
        return call { client, deviceId ->
            client.changespecTags(
                MobileChangeSpecTagListRequestWire(
                    project = project.cleanFilter(),
                    limit = limit,
                    deviceId = deviceId,
                ),
            )
        }
    }

    suspend fun xpromptCatalog(
        project: String? = null,
        source: String? = null,
        tag: String? = null,
        query: String? = null,
        includePdf: Boolean = false,
        limit: Int? = null,
    ): HelperLoadResult<MobileXpromptCatalogResponseWire> {
        return call { client, deviceId ->
            client.xpromptCatalog(
                MobileXpromptCatalogRequestWire(
                    project = project.cleanFilter(),
                    source = source.cleanFilter(),
                    tag = tag.cleanFilter(),
                    query = query.cleanFilter(),
                    includePdf = includePdf,
                    limit = limit,
                    deviceId = deviceId,
                ),
            )
        }
    }

    suspend fun beads(
        project: String? = null,
        allProjects: Boolean = false,
        status: String? = null,
        beadType: String? = null,
        tier: String? = null,
        includeClosed: Boolean = false,
        limit: Int? = null,
    ): HelperLoadResult<MobileBeadListResponseWire> {
        return call { client, deviceId ->
            client.beads(
                MobileBeadListRequestWire(
                    project = project.cleanFilter(),
                    allProjects = allProjects,
                    status = status.cleanFilter(),
                    beadType = beadType.cleanFilter(),
                    tier = tier.cleanFilter(),
                    includeClosed = includeClosed,
                    limit = limit,
                    deviceId = deviceId,
                ),
            )
        }
    }

    suspend fun beadDetail(
        beadId: String,
        project: String? = null,
        allProjects: Boolean = false,
    ): HelperLoadResult<MobileBeadShowResponseWire> {
        val cleanBeadId = beadId.trim()
        if (cleanBeadId.isEmpty()) {
            return HelperLoadResult.InvalidRequest("Choose a bead before opening detail.")
        }
        return call { client, deviceId ->
            client.beadDetail(
                MobileBeadShowRequestWire(
                    beadId = cleanBeadId,
                    project = project.cleanFilter(),
                    allProjects = allProjects,
                    deviceId = deviceId,
                ),
            )
        }
    }

    private suspend fun <T> call(
        block: suspend (GatewayApiClient, String) -> GatewayApiResult<T>,
    ): HelperLoadResult<T> {
        val session = sessionStorage.read() ?: return HelperLoadResult.LoggedOut
        val token = runCatching { tokenVault.readToken() }.getOrNull()
        val client = clientFactory(session.baseUrl) { token }
        return when (val result = block(client, session.deviceId)) {
            is GatewayApiResult.Success -> HelperLoadResult.Success(result.value)
            is GatewayApiResult.Failure -> HelperLoadResult.Failure(result.error)
        }
    }
}

sealed interface HelperLoadResult<out T> {
    data object LoggedOut : HelperLoadResult<Nothing>
    data class InvalidRequest(val message: String) : HelperLoadResult<Nothing>
    data class Success<T>(val value: T) : HelperLoadResult<T>
    data class Failure(val error: GatewayApiError) : HelperLoadResult<Nothing>
}

private fun String?.cleanFilter(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
