package no.novari.flyt.gateway.webinstance.validation.constraints

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class Base64Validator : ConstraintValidator<ValidBase64, String> {
    override fun isValid(
        value: String?,
        context: ConstraintValidatorContext,
    ): Boolean {
        return value == null || isDecodable(value)
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun isDecodable(value: String): Boolean {
        return try {
            Base64.decode(value)
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}
