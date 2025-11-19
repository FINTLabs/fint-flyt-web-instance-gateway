package no.novari.gateway.instance.validation

class InstanceValidationException(
    val validationErrors: List<InstanceValidationService.Error>,
) : RuntimeException("Instance validation errors: $validationErrors")
