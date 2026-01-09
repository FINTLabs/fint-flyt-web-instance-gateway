package no.novari.flyt.gateway.webinstance.error

import jakarta.servlet.http.HttpServletRequest
import java.nio.charset.StandardCharsets

object ErrorResponseUtils {
    val payloadTooLargeBody =
        mapOf(
            "error" to "payload_too_large",
            "message" to "Request payload exceeds configured limit.",
        )

    val payloadTooLargeFallback: ByteArray =
        "{\"error\":\"payload_too_large\",\"message\":\"Request payload exceeds configured limit.\"}"
            .toByteArray(StandardCharsets.UTF_8)

fun resolveFullUrl(request: HttpServletRequest): String {
    val forwardedProto = request.getHeader("X-Forwarded-Proto")
    val forwardedHost = request.getHeader("X-Forwarded-Host")
    val forwardedPort = request.getHeader("X-Forwarded-Port")

    val hasForwardedHeaders =
        forwardedProto != null || forwardedHost != null || forwardedPort != null

    if (!hasForwardedHeaders) {
        val base = request.requestURL.toString()
        return request.queryString
            ?.takeIf { it.isNotBlank() }
            ?.let { "$base?$it" }
            ?: base
    }

    val scheme = forwardedProto ?: request.scheme ?: "http"
    val host = forwardedHost ?: request.serverName
    val port = forwardedPort
        ?: request.serverPort
            .takeIf { it > 0 }
            ?.toString()

    return buildString {
        append(scheme)
        append("://")
        append(host)

        if (
            port != null &&
            !host.contains(":") &&
            port != "80" &&
            port != "443"
        ) {
            append(":")
            append(port)
        }

        append(request.requestURI)

        request.queryString
            ?.takeIf { it.isNotBlank() }
            ?.let {
                append("?")
                append(it)
            }
    }
}
}
