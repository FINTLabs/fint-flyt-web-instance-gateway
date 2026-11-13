package no.novari.flyt.gateway.webinstance

import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.OAuth2AccessToken
import java.time.Instant

/** Returns a token named after the requested registration, so tests can assert which registration was used. */
fun testAuthorizedClientManager(): OAuth2AuthorizedClientManager =
    mock {
        on { authorize(any()) } doAnswer { invocation ->
            val request = invocation.getArgument<OAuth2AuthorizeRequest>(0)
            val registration =
                ClientRegistration
                    .withRegistrationId(request.clientRegistrationId)
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .clientId("client")
                    .tokenUri("http://idp/token")
                    .build()
            val token =
                OAuth2AccessToken(
                    OAuth2AccessToken.TokenType.BEARER,
                    "token-${request.clientRegistrationId}",
                    Instant.now(),
                    Instant.now().plusSeconds(60),
                )
            OAuth2AuthorizedClient(registration, "anonymousUser", token)
        }
    }
