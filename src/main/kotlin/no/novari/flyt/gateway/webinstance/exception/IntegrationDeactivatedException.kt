package no.novari.flyt.gateway.webinstance.exception

import no.novari.flyt.gateway.webinstance.model.Integration

class IntegrationDeactivatedException(
    val integration: Integration,
) : RuntimeException("Integration is deactivated: $integration")
