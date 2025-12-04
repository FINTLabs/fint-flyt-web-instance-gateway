package no.novari.gateway.webinstance.exception

import no.novari.gateway.webinstance.model.Integration

class IntegrationDeactivatedException(
    val integration: Integration,
) : RuntimeException("Integration is deactivated: $integration")
