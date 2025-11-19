package no.fintlabs.gateway.instance.exception

import no.fintlabs.gateway.instance.model.SourceApplicationIdAndSourceApplicationIntegrationId

class NoIntegrationException(
    val sourceApplicationIdAndSourceApplicationIntegrationId: SourceApplicationIdAndSourceApplicationIntegrationId,
) : RuntimeException(
        "Count not find integration for ${sourceApplicationIdAndSourceApplicationIntegrationId.sourceApplicationId}",
    )
