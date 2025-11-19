package no.fintlabs.gateway.instance.validation

import no.fintlabs.gateway.instance.ErrorCode
import no.novari.kafka.model.Error
import no.novari.kafka.model.ErrorCollection
import org.springframework.stereotype.Service

@Service
class InstanceValidationErrorMappingService {
    fun map(instanceValidationException: InstanceValidationException): ErrorCollection =
        ErrorCollection(
            instanceValidationException.validationErrors.map { validationError ->
                Error(
                    ErrorCode.INSTANCE_VALIDATION_ERROR.getCode(),
                    mapOf(
                        "fieldPath" to validationError.fieldPath,
                        "errorMessage" to validationError.errorMessage,
                    ),
                )
            },
        )
}
