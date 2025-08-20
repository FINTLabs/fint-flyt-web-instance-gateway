package no.fintlabs.gateway.webinstance.validation.constraints

import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import jakarta.validation.ConstraintValidatorContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import kotlin.test.BeforeTest
import kotlin.test.assertTrue

class Base64ValidatorTest {
    private val validator = Base64Validator()

    @MockK
    private lateinit var context: ConstraintValidatorContext

    @BeforeTest
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
    }

    @Test
    fun nullValueShouldBeValid() {
        assertTrue(validator.isValid(null, context))
    }

    @Test
    fun emptyValueShouldBeValid() {
        assertTrue(validator.isValid("", context))
    }

    @Test
    fun valueWithNoPaddingCharactersShouldBeValid() {
        assertTrue(validator.isValid("AAAA", context))
    }

    @Test
    fun valueWithOnePaddingCharacterShouldBeValid() {
        assertTrue(validator.isValid("AAA=", context))
    }

    @Test
    fun valueWithTwoPaddingCharactersShouldBeValid() {
        assertTrue(validator.isValid("AA==", context))
    }

    @Test
    fun valueNotDivisibleBy4ShouldBeValid() {
        assertTrue(validator.isValid("AAA", context))
    }

    @Test
    fun blankValueShouldBeInvalid() {
        assertFalse(validator.isValid("    ", context))
    }

    @Test
    fun valueWithWhitespaceShouldBeInvalid() {
        assertFalse(validator.isValid("AA A", context))
    }

    @Test
    fun valueWithNonBase64CharacterShouldBeInvalid() {
        assertFalse(validator.isValid("AA-A", context))
    }

    @Test
    fun valueWithThreePaddingCharactersShouldBeInvalid() {
        assertFalse(validator.isValid("A===", context))
    }
}
