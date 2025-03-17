package no.fintlabs.gateway.webinstance.validation

import org.springframework.stereotype.Service
import javax.validation.Validator
import javax.validation.ValidatorFactory
import java.util.Optional

@Service
class InstanceValidationService(validatorFactory: ValidatorFactory) {

    private val fieldValidator: Validator = validatorFactory.validator

    data class Error(
        val fieldPath: String,
        val errorMessage: String
    )

    fun validate(instance: Any): Optional<List<Error>> {
        val errors = fieldValidator.validate(instance)
            .map { violation ->
                Error(
                    fieldPath = violation.propertyPath.toString(),
                    errorMessage = violation.message
                )
            }
            .sortedBy {  it.fieldPath }
            .toList()

        return if (errors.isEmpty()) Optional.empty() else Optional.of(errors)
    }
}