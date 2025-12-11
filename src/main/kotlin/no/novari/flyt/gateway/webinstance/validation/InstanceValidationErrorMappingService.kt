package no.novari.flyt.gateway.webinstance.validation

import no.novari.flyt.gateway.webinstance.ErrorCode
import no.novari.flyt.kafka.model.Error
import no.novari.flyt.kafka.model.ErrorCollection
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
