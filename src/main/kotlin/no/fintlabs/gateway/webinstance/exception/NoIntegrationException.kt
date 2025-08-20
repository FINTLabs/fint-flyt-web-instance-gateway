package no.fintlabs.gateway.webinstance.exception

import no.fintlabs.gateway.webinstance.model.SourceApplicationIdAndSourceApplicationIntegrationId

class NoIntegrationException(
    val sourceApplicationIdAndSourceApplicationIntegrationId: SourceApplicationIdAndSourceApplicationIntegrationId,
) : RuntimeException(
        "Count not find integration for ${sourceApplicationIdAndSourceApplicationIntegrationId.sourceApplicationId}",
    )
