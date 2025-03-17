package no.fintlabs.gateway.webinstance.exception

import no.fintlabs.gateway.webinstance.model.Integration

class IntegrationDeactivatedException(
    val integration: Integration
) : RuntimeException("Integration is deactivated: $integration")