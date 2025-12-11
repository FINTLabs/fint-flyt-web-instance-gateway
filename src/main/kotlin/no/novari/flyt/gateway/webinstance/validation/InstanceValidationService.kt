package no.novari.flyt.gateway.webinstance.validation

import jakarta.validation.Validator
import org.springframework.stereotype.Service

@Service
class InstanceValidationService(
    private val validator: Validator,
) {
    data class Error(
        val fieldPath: String,
        val errorMessage: String,
    )

    fun validate(instance: Any): List<Error>? {
        val errors =
            validator
                .validate(instance)
                .asSequence()
                .map { violation ->
                    Error(
                        fieldPath = violation.propertyPath.toString(),
                        errorMessage = violation.message,
                    )
                }.sortedBy { it.fieldPath }
                .toList()

        return errors.takeIf { it.isNotEmpty() }
    }
}
