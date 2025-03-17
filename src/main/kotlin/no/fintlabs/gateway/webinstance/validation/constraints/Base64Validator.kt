package no.fintlabs.gateway.webinstance.validation.constraints

import org.springframework.util.Base64Utils
import javax.validation.ConstraintValidator
import javax.validation.ConstraintValidatorContext

class Base64Validator : ConstraintValidator<ValidBase64?, String?> {
    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean {
        return value == null || canBeDecoded(value)
    }

    private fun canBeDecoded(value: String): Boolean {
        try {
            Base64Utils.decodeFromString(value)
        } catch (e: IllegalArgumentException) {
            return false
        }
        return true
    }
}