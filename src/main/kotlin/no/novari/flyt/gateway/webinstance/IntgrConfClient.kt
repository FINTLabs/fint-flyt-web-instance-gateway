package no.novari.flyt.gateway.webinstance

import no.novari.flyt.gateway.webinstance.model.Integration
import no.novari.flyt.gateway.webinstance.model.SourceApplicationIdAndSourceApplicationIntegrationId
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * Client for the internal client API in fint-flyt-intgr-conf-service.
 *
 * Returns `null` on 404, matching the `null` reply of the Kafka request/reply topic it replaces.
 */
@Service
class IntgrConfClient(
    @param:Qualifier("intgrConfRestClient")
    private val restClient: RestClient,
) {
    fun getIntegration(params: SourceApplicationIdAndSourceApplicationIntegrationId): Integration? =
        try {
            restClient
                .get()
                .uri { builder ->
                    builder
                        .path("/integrasjoner")
                        .queryParam("sourceApplicationId", params.sourceApplicationId)
                        .queryParam("sourceApplicationIntegrationId", "{sourceApplicationIntegrationId}")
                        .build(params.sourceApplicationIntegrationId)
                }.retrieve()
                .body<Integration>()
        } catch (_: HttpClientErrorException.NotFound) {
            null
        }
}
