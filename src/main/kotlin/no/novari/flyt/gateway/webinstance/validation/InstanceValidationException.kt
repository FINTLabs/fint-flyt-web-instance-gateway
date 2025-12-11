package no.novari.flyt.gateway.webinstance.validation

class InstanceValidationException(
    val validationErrors: List<InstanceValidationService.Error>,
) : RuntimeException("Instance validation errors: $validationErrors")
