package no.fintlabs.gateway.webinstance.validation

import no.fintlabs.gateway.webinstance.ErrorCode
import no.fintlabs.kafka.model.Error
import no.fintlabs.kafka.model.ErrorCollection
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
