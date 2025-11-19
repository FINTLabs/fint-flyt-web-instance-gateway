package no.novari.gateway.instance.web

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.retry.annotation.EnableRetry
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.web.client.RestClient
import java.net.URI
import java.time.Duration

@Configuration
@EnableRetry
class RestClientConfiguration {
    @Bean("fileClientHttpRequestFactory")
    fun fileClientHttpRequestFactory(): ClientHttpRequestFactory =
        HttpComponentsClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(300))
            setReadTimeout(Duration.ofSeconds(300))
        }

    @Bean("fileAuthorizedClientManager")
    fun fileAuthorizedClientManager(
        clientRegistrationRepository: ClientRegistrationRepository,
        authorizedClientService: OAuth2AuthorizedClientService,
    ): OAuth2AuthorizedClientManager {
        val manager =
            AuthorizedClientServiceOAuth2AuthorizedClientManager(
                clientRegistrationRepository,
                authorizedClientService,
            )
        manager.setAuthorizedClientProvider(
            OAuth2AuthorizedClientProviderBuilder
                .builder()
                .clientCredentials()
                .refreshToken()
                .build(),
        )
        return manager
    }

    @Bean
    fun fileRestClient(
        @Value("\${novari.flyt.file-service-url}") fileServiceUrl: String,
        @Qualifier("fileAuthorizedClientManager") authorizedClientManager: OAuth2AuthorizedClientManager,
        @Qualifier("fileClientHttpRequestFactory") requestFactory: ClientHttpRequestFactory,
    ): RestClient {
        val interceptor = OAuth2ClientHttpRequestInterceptor(authorizedClientManager, "file-service")

        return RestClient
            .builder()
            .baseUrl("$fileServiceUrl/api/intern-klient/filer")
            .requestFactory { uri: URI, method: HttpMethod -> requestFactory.createRequest(uri, method) }
            .requestInterceptor(interceptor)
            .build()
    }
}
