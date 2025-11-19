package no.fintlabs.gateway.instance.web

import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import java.io.IOException

class OAuth2RestTemplateInterceptor(
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
        authorizedClient?.accessToken?.let { accessToken ->
            request.headers.add("Authorization", "Bearer ${accessToken.tokenValue}")
        }
        return execution.execute(request, body)
    }
}
