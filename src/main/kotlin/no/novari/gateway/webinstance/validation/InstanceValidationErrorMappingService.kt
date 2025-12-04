package no.novari.gateway.webinstance.validation

import no.novari.flyt.kafka.model.Error
import no.novari.flyt.kafka.model.ErrorCollection
import no.novari.gateway.webinstance.ErrorCode
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
