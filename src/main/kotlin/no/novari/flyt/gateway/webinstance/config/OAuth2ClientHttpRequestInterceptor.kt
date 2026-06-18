package no.novari.flyt.gateway.webinstance.config

import org.slf4j.LoggerFactory
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import java.io.IOException

class OAuth2ClientHttpRequestInterceptor(
    private val authorizedClientManager: OAuth2AuthorizedClientManager,
    private val clientRegistrationId: String,
) : ClientHttpRequestInterceptor {
    @Throws(IOException::class)
    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution,
    ): ClientHttpResponse {
        log.debug(
            "Authorizing OAuth2 client request, clientRegistrationId={}, method={}, uri={}",
            clientRegistrationId,
            request.method,
            request.uri,
        )

        val principal =
            AnonymousAuthenticationToken(
                "anonymous",
                "anonymousUser",
                listOf(SimpleGrantedAuthority("ROLE_ANONYMOUS")),
            )

        val authorizeRequest =
            OAuth2AuthorizeRequest
                .withClientRegistrationId(clientRegistrationId)
                .principal(principal)
                .build()

        val authorizedClient = authorizedClientManager.authorize(authorizeRequest)
        if (authorizedClient == null) {
            log.warn(
                "OAuth2 client '{}' not authorized; request will be sent without a bearer token",
                clientRegistrationId,
            )
        } else {
            authorizedClient.accessToken?.let { accessToken ->
                request.headers.add("Authorization", "Bearer ${accessToken.tokenValue}")
                log.debug(
                    "Added OAuth2 bearer token, clientRegistrationId={}, expiresAt={}",
                    clientRegistrationId,
                    accessToken.expiresAt,
                )
            }
        }

        return try {
            val response = execution.execute(request, body)
            log.debug(
                "OAuth2 client request completed, clientRegistrationId={}, method={}, uri={}, statusCode={}",
                clientRegistrationId,
                request.method,
                request.uri,
                response.statusCode,
            )
            response
        } catch (ex: IOException) {
            log.debug(
                "OAuth2 client request failed before receiving HTTP response, clientRegistrationId={}, method={}, uri={}, exceptionType={}, message={}",
                clientRegistrationId,
                request.method,
                request.uri,
                ex::class.qualifiedName,
                ex.message,
                ex,
            )
            throw ex
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(OAuth2ClientHttpRequestInterceptor::class.java)
    }
}
