package no.novari.flyt.gateway.webinstance.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings
import org.springframework.context.annotation.Bean
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.converter.FormHttpMessageConverter
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.endpoint.RestClientClientCredentialsTokenResponseClient
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter
import org.springframework.web.client.RestClient
import java.time.Duration

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
        requestFactoryBuilder: ClientHttpRequestFactoryBuilder<*>,
        requestFactorySettings: ClientHttpRequestFactorySettings,
    ): OAuth2AuthorizedClientManager {
        // The default token client has no timeouts. Same converters and error handler as its default, plus ours.
        val tokenResponseClient = RestClientClientCredentialsTokenResponseClient()
        tokenResponseClient.setRestClient(
            RestClient
                .builder()
                .requestFactory(requestFactoryBuilder.build(requestFactorySettings.withDefaultTimeouts()))
                .messageConverters { converters ->
                    converters.clear()
                    converters.add(FormHttpMessageConverter())
                    converters.add(OAuth2AccessTokenResponseHttpMessageConverter())
                }.defaultStatusHandler(OAuth2ErrorResponseErrorHandler())
                .build(),
        )
        val manager =
            AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrationRepository, authorizedClientService)
        manager.setAuthorizedClientProvider(
            OAuth2AuthorizedClientProviderBuilder
                .builder()
                .clientCredentials { it.accessTokenResponseClient(tokenResponseClient) }
                .build(),
        )
        return manager
    }

    @Bean
    fun intgrConfRestClient(
        restClientBuilder: RestClient.Builder,
        @Qualifier("internalClientAuthorizedClientManager") authorizedClientManager: OAuth2AuthorizedClientManager,
        requestFactoryBuilder: ClientHttpRequestFactoryBuilder<*>,
        requestFactorySettings: ClientHttpRequestFactorySettings,
        @Value(
            "\${novari.flyt.intgr-conf-service-url:http://fint-flyt-intgr-conf-service:8080\${server.servlet.context-path:}}",
        )
        baseUrl: String,
    ): RestClient =
        internalClient(
            restClientBuilder,
            authorizedClientManager,
            requestFactoryBuilder.build(requestFactorySettings.withDefaultTimeouts()),
            "intgr-conf-service",
            baseUrl,
        )

    @Bean
    fun historyRestClient(
        restClientBuilder: RestClient.Builder,
        @Qualifier("internalClientAuthorizedClientManager") authorizedClientManager: OAuth2AuthorizedClientManager,
        requestFactoryBuilder: ClientHttpRequestFactoryBuilder<*>,
        requestFactorySettings: ClientHttpRequestFactorySettings,
        @Value(
            "\${novari.flyt.history-service-url:http://fint-flyt-history-service:8080\${server.servlet.context-path:}}",
        )
        baseUrl: String,
    ): RestClient =
        internalClient(
            restClientBuilder,
            authorizedClientManager,
            requestFactoryBuilder.build(requestFactorySettings.withDefaultTimeouts()),
            "history-service",
            baseUrl,
        )

    private fun internalClient(
        restClientBuilder: RestClient.Builder,
        authorizedClientManager: OAuth2AuthorizedClientManager,
        requestFactory: ClientHttpRequestFactory,
        clientRegistrationId: String,
        baseUrl: String,
    ): RestClient {
        val interceptor = OAuth2ClientHttpRequestInterceptor(authorizedClientManager)
        interceptor.setClientRegistrationIdResolver { clientRegistrationId }
        return restClientBuilder
            .baseUrl("${baseUrl.trimEnd('/')}/api/intern-klient")
            .requestFactory(requestFactory)
            .requestInterceptor(interceptor)
            .build()
    }

    // The calls block the incoming request, so they must not fall back to the HTTP client's minutes-long defaults.
    // spring.http.client.connect-timeout and read-timeout in the app override these.
    private fun ClientHttpRequestFactorySettings.withDefaultTimeouts(): ClientHttpRequestFactorySettings =
        withTimeouts(connectTimeout() ?: DEFAULT_CONNECT_TIMEOUT, readTimeout() ?: DEFAULT_READ_TIMEOUT)

    private companion object {
        val DEFAULT_CONNECT_TIMEOUT: Duration = Duration.ofSeconds(2)
        val DEFAULT_READ_TIMEOUT: Duration = Duration.ofSeconds(5)
    }
}
