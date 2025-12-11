package no.novari.flyt.gateway.webinstance.exception

import no.novari.flyt.gateway.webinstance.model.SourceApplicationIdAndSourceApplicationIntegrationId

class NoIntegrationException(
    val sourceApplicationIdAndSourceApplicationIntegrationId: SourceApplicationIdAndSourceApplicationIntegrationId,
) : RuntimeException(
        "Count not find integration for ${sourceApplicationIdAndSourceApplicationIntegrationId.sourceApplicationId}",
    )
