package no.novari.gateway.webinstance.validation.constraints

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.FIELD
import kotlin.annotation.AnnotationTarget.PROPERTY_GETTER
import kotlin.annotation.AnnotationTarget.VALUE_PARAMETER
import kotlin.reflect.KClass

@MustBeDocumented
@Constraint(validatedBy = [Base64Validator::class])
@Target(FIELD, PROPERTY_GETTER, VALUE_PARAMETER)
@Retention(RUNTIME)
annotation class ValidBase64(
    val message: String = "Invalid Base64-encoded string",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)
