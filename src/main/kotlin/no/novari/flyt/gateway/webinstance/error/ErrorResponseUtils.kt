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

        if (forwardedProto == null && forwardedHost == null && forwardedPort == null) {
            val base = request.requestURL.toString()
            val query = request.queryString
            return if (query.isNullOrBlank()) base else "$base?$query"
        }

        val scheme = forwardedProto ?: request.scheme ?: "http"
        val host = forwardedHost ?: request.serverName
        val port = forwardedPort ?: request.serverPort.takeIf { it > 0 }?.toString()

        val url = StringBuilder()
        url.append(scheme).append("://")
        if (!host.isNullOrBlank()) {
            url.append(host)
        }
        if (port != null && !host.contains(":") && port != "80" && port != "443") {
            url.append(":").append(port)
        }
        url.append(request.requestURI)
        val query = request.queryString
        if (!query.isNullOrBlank()) {
            url.append("?").append(query)
        }
        return url.toString()
    }
}
