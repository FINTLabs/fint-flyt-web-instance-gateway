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
                log.debug("Added OAuth2 bearer token for client '{}'", clientRegistrationId)
            }
        }

        return execution.execute(request, body)
    }

    private companion object {
        private val log = LoggerFactory.getLogger(OAuth2ClientHttpRequestInterceptor::class.java)
    }
}
