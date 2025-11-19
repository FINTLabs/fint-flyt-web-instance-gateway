package no.fintlabs.gateway.instance.exception

import no.fintlabs.gateway.instance.model.Integration

class IntegrationDeactivatedException(
    val integration: Integration,
) : RuntimeException("Integration is deactivated: $integration")
