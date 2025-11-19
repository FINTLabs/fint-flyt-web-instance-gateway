package no.novari.gateway.instance.exception

import no.novari.gateway.instance.model.Integration

class IntegrationDeactivatedException(
    val integration: Integration,
) : RuntimeException("Integration is deactivated: $integration")
