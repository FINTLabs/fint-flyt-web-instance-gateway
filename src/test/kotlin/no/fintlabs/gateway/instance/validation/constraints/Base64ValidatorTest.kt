
package no.fintlabs.gateway.instance.validation.constraints

import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import jakarta.validation.ConstraintValidatorContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class Base64ValidatorTest {
    private val validator = Base64Validator()

    @MockK(relaxed = true)
    private lateinit var context: ConstraintValidatorContext

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
    }

    @Nested
    @DisplayName("Permissive cases that should be valid")
    inner class ValidCases {
        @Test
        fun `null value should be valid`() {
            assertTrue(validator.isValid(null, context))
        }

        @Test
        fun `empty value should be valid`() {
            assertTrue(validator.isValid("", context))
        }

        @ParameterizedTest
        @DisplayName("base64 strings with optional padding are valid")
        @ValueSource(
            strings = [
                // no padding
                "AAAA",
                // single padding
                "AAA=",
                // double padding
                "AA==",
                // "Man"
                "TWFu",
                // contains letters, digits, '+' and '/'
                "YWJjMTIzKysvLy8=",
                // long string
                "QUJDZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXo=",
                // length not divisible by 4 but commonly allowed when padding omitted
                "QUJj",
            ],
        )
        fun validValues(value: String) {
            assertTrue(validator.isValid(value, context), "Expected valid for '$value'")
        }
    }

    @Nested
    @DisplayName("Cases that should be invalid")
    inner class InvalidCases {
        @ParameterizedTest
        @DisplayName("strings with whitespace or illegal characters are invalid")
        @ValueSource(
            strings = [
                // blank
                "    ",
                // space inside
                "AA A",
                // tab inside
                "AA\tA",
                // newline inside
                "AA\nA",
                // hyphen not allowed in standard base64
                "AA-A",
                // three padding characters
                "A===",
                // non-ascii
                "ÅA==",
                // illegal symbols
                "@@@=",
            ],
        )
        fun invalidValues(value: String) {
            assertFalse(validator.isValid(value, context), "Expected invalid for '$value'")
        }
    }
}
