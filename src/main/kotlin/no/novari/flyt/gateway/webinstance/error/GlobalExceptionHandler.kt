package no.novari.flyt.gateway.webinstance.error

import jakarta.servlet.http.HttpServletRequest
import no.novari.flyt.gateway.webinstance.exception.IntegrationDeactivatedException
import no.novari.flyt.gateway.webinstance.exception.NoIntegrationException
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException
import java.time.OffsetDateTime

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        val msg =
            ex.bindingResult.allErrors.joinToString("; ") {
                it.defaultMessage ?: "Ugyldig forespørsel. Se feltfeil for detaljer."
            }
        return buildError(HttpStatus.BAD_REQUEST, msg, request.requestURI)
    }

    @ExceptionHandler(NoIntegrationException::class)
    fun handleNoIntegration(
        ex: NoIntegrationException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        return buildError(HttpStatus.UNPROCESSABLE_ENTITY, ex.message, request.requestURI)
    }

    @ExceptionHandler(IntegrationDeactivatedException::class)
    fun handleIntegrationDeactivated(
        ex: IntegrationDeactivatedException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        return buildError(HttpStatus.UNPROCESSABLE_ENTITY, ex.message, request.requestURI)
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatus(
        ex: ResponseStatusException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        val status = HttpStatus.valueOf(ex.statusCode.value())
        return buildError(status, ex.reason, request.requestURI)
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(
        ex: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        return buildError(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "En uventet feil oppsto. Prøv igjen senere.",
            request.requestURI,
        )
    }

    private fun buildError(
        status: HttpStatus,
        message: String?,
        path: String,
    ): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(status)
            .body(
                ErrorResponse(
                    timestamp = OffsetDateTime.now(),
                    status = status.value(),
                    error = status.reasonPhrase,
                    message = message,
                    path = path,
                ),
            )
    }
}
