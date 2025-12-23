package no.novari.flyt.gateway.webinstance.error

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.exc.InvalidNullException
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.Test
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.mock.http.MockHttpInputMessage
import org.springframework.mock.web.MockHttpServletRequest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class GlobalExceptionHandlerTest {
    private val handler = GlobalExceptionHandler()

    @Test
    fun `returns bad request with missing field message`() {
        val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        val cause =
            assertFailsWith<InvalidNullException> {
                mapper.readValue("{}", MissingFieldPayload::class.java)
            }
        val request = MockHttpServletRequest().apply { requestURI = "/test" }
        val input = MockHttpInputMessage("{}".toByteArray())
        val exception = HttpMessageNotReadableException("Invalid payload", cause, input)

        val response = handler.handleNotReadable(exception, request)

        assertEquals(400, response.statusCode.value())
        val body = assertNotNull(response.body)
        assertEquals("Mangler påkrevd felt: requiredField", body.message)
        assertEquals("/test", body.path)
    }

    data class MissingFieldPayload(
        val requiredField: String,
    )
}
