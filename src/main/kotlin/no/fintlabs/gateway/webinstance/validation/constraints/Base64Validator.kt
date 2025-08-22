package no.fintlabs.gateway.webinstance.validation.constraints

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import org.springframework.util.Base64Utils

class Base64Validator : ConstraintValidator<ValidBase64, String> {
    override fun isValid(
        value: String?,
        context: ConstraintValidatorContext,
    ): Boolean {
        return value == null || isDecodable(value)
    }

    private fun isDecodable(value: String): Boolean {
        return try {
            Base64Utils.decodeFromString(value)
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}
