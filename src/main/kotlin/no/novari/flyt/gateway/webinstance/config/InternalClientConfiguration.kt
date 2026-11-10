package no.novari.flyt.gateway.webinstance.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor
import org.springframework.web.client.RestClient

/**
 * RestClients for the internal client APIs (`/api/intern-klient`) in intgr-conf-service and history-service.
 */
@AutoConfiguration
class InternalClientConfiguration {
    // client_credentials tokens belong to the app, not to an HTTP session, so they are kept in the
    // OAuth2AuthorizedClientService. One manager serves both registrations below.
    @Bean("internalClientAuthorizedClientManager")
    fun internalClientAuthorizedClientManager(
        clientRegistrationRepository: ClientRegistrationRepository,
        authorizedClientService: OAuth2AuthorizedClientService,
    ): OAuth2AuthorizedClientManager =
        AuthorizedClientServiceOAuth2AuthorizedClientManager(
            clientRegistrationRepository,
            authorizedClientService,
        )

    // Timeouts come from spring.http.client.* in the host app.
    @Bean
    fun intgrConfRestClient(
        restClientBuilder: RestClient.Builder,
        @Qualifier("internalClientAuthorizedClientManager") authorizedClientManager: OAuth2AuthorizedClientManager,
        @Value(
            "\${novari.flyt.intgr-conf-service-url:http://fint-flyt-intgr-conf-service:8080\${server.servlet.context-path:}}",
        )
        baseUrl: String,
    ): RestClient = internalClient(restClientBuilder, authorizedClientManager, "intgr-conf-service", baseUrl)

    @Bean
    fun historyRestClient(
        restClientBuilder: RestClient.Builder,
        @Qualifier("internalClientAuthorizedClientManager") authorizedClientManager: OAuth2AuthorizedClientManager,
        @Value(
            "\${novari.flyt.history-service-url:http://fint-flyt-history-service:8080\${server.servlet.context-path:}}",
        )
        baseUrl: String,
    ): RestClient = internalClient(restClientBuilder, authorizedClientManager, "history-service", baseUrl)

    private fun internalClient(
        restClientBuilder: RestClient.Builder,
        authorizedClientManager: OAuth2AuthorizedClientManager,
        clientRegistrationId: String,
        baseUrl: String,
    ): RestClient {
        val interceptor = OAuth2ClientHttpRequestInterceptor(authorizedClientManager)
        interceptor.setClientRegistrationIdResolver { clientRegistrationId }
        return restClientBuilder
            .baseUrl("${baseUrl.trimEnd('/')}/api/intern-klient")
            .requestInterceptor(interceptor)
            .build()
    }
}
