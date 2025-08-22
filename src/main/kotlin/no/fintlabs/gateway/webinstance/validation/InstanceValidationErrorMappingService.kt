package no.fintlabs.gateway.webinstance.validation

import no.fintlabs.gateway.webinstance.ErrorCode
import no.fintlabs.kafka.event.error.Error
import no.fintlabs.kafka.event.error.ErrorCollection
import org.springframework.stereotype.Service

@Service
class InstanceValidationErrorMappingService {
    fun map(instanceValidationException: InstanceValidationException): ErrorCollection =
        ErrorCollection(
            instanceValidationException.validationErrors.map { validationError ->
                Error
                    .builder()
                    .errorCode(ErrorCode.INSTANCE_VALIDATION_ERROR.getCode())
                    .args(
                        mapOf(
                            "fieldPath" to validationError.fieldPath,
                            "errorMessage" to validationError.errorMessage,
                        ),
                    ).build()
            },
        )
}
