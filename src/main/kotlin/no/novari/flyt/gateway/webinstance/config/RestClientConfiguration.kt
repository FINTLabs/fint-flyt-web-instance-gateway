package no.novari.flyt.gateway.webinstance.config

import org.apache.hc.client5.http.config.ConnectionConfig
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.apache.hc.core5.util.Timeout
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Bean
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

@AutoConfiguration
@EnableRetry
class RestClientConfiguration {
    @Bean("fileClientHttpRequestFactory")
    fun fileClientHttpRequestFactory(): ClientHttpRequestFactory =
        HttpComponentsClientHttpRequestFactory(
            HttpClientBuilder
                .create()
                .setConnectionManager(
                    PoolingHttpClientConnectionManagerBuilder
                        .create()
                        .setDefaultConnectionConfig(
                            ConnectionConfig
                                .custom()
                                .setConnectTimeout(Timeout.ofSeconds(300))
                                .setSocketTimeout(Timeout.ofSeconds(300))
                                .build(),
                        ).build(),
                ).build(),
        )

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
        @Qualifier("fileAuthorizedClientManager") authorizedClientManager: OAuth2AuthorizedClientManager,
        @Qualifier("fileClientHttpRequestFactory") requestFactory: ClientHttpRequestFactory,
    ): RestClient {
        val interceptor = OAuth2ClientHttpRequestInterceptor(authorizedClientManager, "file-service")
        return RestClient
            .builder()
            .requestFactory { uri: URI, method: HttpMethod -> requestFactory.createRequest(uri, method) }
            .requestInterceptor(interceptor)
            .build()
    }
}
