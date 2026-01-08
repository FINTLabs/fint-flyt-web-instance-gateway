package no.novari.flyt.gateway.webinstance.error

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.unit.DataSize
import org.springframework.web.filter.OncePerRequestFilter
import java.nio.charset.StandardCharsets

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty("novari.flyt.web-instance-gateway.max-request-size")
class RequestSizeLimitFilter(
    @Value("\${novari.flyt.web-instance-gateway.max-request-size}") maxInMemorySize: DataSize,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {
    private val maxInMemoryBytes = maxInMemorySize.toBytes()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val contentLength = request.contentLengthLong
        if (contentLength > 0 && maxInMemoryBytes > 0 && contentLength > maxInMemoryBytes) {
            val url = ErrorResponseUtils.resolveFullUrl(request)
            log.warn(
                "Payload too large for {} {}: content-length={} max-in-memory={}",
                request.method,
                url,
                contentLength,
                maxInMemoryBytes,
            )
            response.status = HttpStatus.PAYLOAD_TOO_LARGE.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.characterEncoding = StandardCharsets.UTF_8.name()
            response.outputStream.use { output ->
                output.write(serializeBody())
                output.flush()
            }
            return
        }
        filterChain.doFilter(request, response)
    }

    private fun serializeBody(): ByteArray {
        return try {
            objectMapper.writeValueAsBytes(ErrorResponseUtils.payloadTooLargeBody)
        } catch (e: JsonProcessingException) {
            ErrorResponseUtils.payloadTooLargeFallback
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(RequestSizeLimitFilter::class.java)
    }
}
