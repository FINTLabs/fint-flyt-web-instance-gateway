package no.novari.flyt.gateway.webinstance.error

import com.fasterxml.jackson.databind.exc.InvalidNullException
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.UnexpectedTypeException
import jakarta.validation.ValidationException
import no.novari.flyt.gateway.webinstance.exception.IntegrationDeactivatedException
import no.novari.flyt.gateway.webinstance.exception.NoIntegrationException
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.io.buffer.DataBufferLimitException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebInputException
import java.time.OffsetDateTime

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

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

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleNotReadable(
        ex: HttpMessageNotReadableException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        return buildError(HttpStatus.BAD_REQUEST, buildNotReadableMessage(ex), request.requestURI)
    }

    @ExceptionHandler(ValidationException::class)
    fun handleValidationException(
        ex: ValidationException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        val message =
            when (ex) {
                is UnexpectedTypeException -> "Ugyldig valideringsregel for felt. Kontakt systemansvarlig."
                else -> "Ugyldig forespørsel. Se feltfeil for detaljer."
            }
        return buildError(HttpStatus.BAD_REQUEST, message, request.requestURI)
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

    @ExceptionHandler(DataBufferLimitException::class)
    fun handleDataBufferLimitException(
        ex: DataBufferLimitException,
        request: HttpServletRequest,
    ): ResponseEntity<Map<String, String>> {
        val url = ErrorResponseUtils.resolveFullUrl(request)
        log.warn("Payload too large for {} {}: {}", request.method, url, ex.message)
        return ResponseEntity
            .status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(ErrorResponseUtils.payloadTooLargeBody)
    }

    @ExceptionHandler(ServerWebInputException::class)
    fun handleServerWebInputException(
        ex: ServerWebInputException,
        request: HttpServletRequest,
    ): ResponseEntity<Map<String, String>> {
        val cause = ex.cause
        if (cause is DataBufferLimitException) {
            return handleDataBufferLimitException(cause, request)
        }
        val url = ErrorResponseUtils.resolveFullUrl(request)
        log.warn("Bad request for {} {}: {}", request.method, url, ex.message)
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(
                mapOf(
                    "error" to "bad_request",
                    "message" to "Malformed request.",
                ),
            )
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
        log.error("Unexpected error while handling request: {}", request.requestURI, ex)
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

    private fun buildNotReadableMessage(ex: HttpMessageNotReadableException): String {
        val cause = ex.mostSpecificCause
        if (cause is InvalidNullException) {
            val fieldName = cause.path.lastOrNull()?.fieldName
            if (!fieldName.isNullOrBlank()) {
                return "Mangler påkrevd felt: $fieldName"
            }
        }
        if (cause is MismatchedInputException) {
            val fieldName = cause.path.lastOrNull()?.fieldName
            if (!fieldName.isNullOrBlank()) {
                return "Ugyldig verdi for felt: $fieldName"
            }
        }
        return "Ugyldig forespørsel. Mangler påkrevde felter eller ugyldig JSON."
    }
}
