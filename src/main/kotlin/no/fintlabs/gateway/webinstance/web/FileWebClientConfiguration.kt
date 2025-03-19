package no.fintlabs.gateway.webinstance.web

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.DefaultUriBuilderFactory

@Configuration
class FileWebClientConfiguration {

    @Bean
    fun fileRestTemplate(
        @Value("\${fint.flyt.file-service-url}") fileServiceUrl: String,
        clientRegistrationRepository: ClientRegistrationRepository,
        authorizedClientService: OAuth2AuthorizedClientService
    ): RestTemplate {
        val requestFactory = HttpComponentsClientHttpRequestFactory().apply {
            setConnectTimeout(300000)
            setReadTimeout(300000)
        }

        val restTemplate = RestTemplate(requestFactory).apply {
            uriTemplateHandler = DefaultUriBuilderFactory("$fileServiceUrl/api/intern-klient/filer")
        }

        val authorizedClientManager: OAuth2AuthorizedClientManager =
            AuthorizedClientServiceOAuth2AuthorizedClientManager(
                clientRegistrationRepository,
                authorizedClientService
            )

        restTemplate.interceptors.add(OAuth2RestTemplateInterceptor(authorizedClientManager, "file-service"))

        return restTemplate
    }
}