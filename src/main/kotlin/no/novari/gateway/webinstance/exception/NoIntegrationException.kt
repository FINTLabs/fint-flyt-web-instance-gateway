package no.novari.gateway.webinstance.exception

import no.novari.gateway.webinstance.model.SourceApplicationIdAndSourceApplicationIntegrationId

class NoIntegrationException(
    val sourceApplicationIdAndSourceApplicationIntegrationId: SourceApplicationIdAndSourceApplicationIntegrationId,
) : RuntimeException(
        "Count not find integration for ${sourceApplicationIdAndSourceApplicationIntegrationId.sourceApplicationId}",
    )
