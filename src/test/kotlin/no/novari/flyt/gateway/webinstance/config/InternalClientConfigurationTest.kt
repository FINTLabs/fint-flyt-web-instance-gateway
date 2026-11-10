package no.novari.flyt.gateway.webinstance.config

import no.novari.flyt.gateway.webinstance.testAuthorizedClientManager
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class InternalClientConfigurationTest {
    private val restClientBuilder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(restClientBuilder).build()

    private val contextRunner =
        ApplicationContextRunner()
            .withUserConfiguration(InternalClientConfiguration::class.java)
            .withBean(RestClient.Builder::class.java, { restClientBuilder })
            .withBean(ClientRegistrationRepository::class.java, { mock<ClientRegistrationRepository>() })
            .withBean(OAuth2AuthorizedClientService::class.java, { mock<OAuth2AuthorizedClientService>() })
            .withBean(
                "internalClientAuthorizedClientManager",
                OAuth2AuthorizedClientManager::class.java,
                { testAuthorizedClientManager() },
            ).withAllowBeanDefinitionOverriding(true)

    @Test
    fun `default base urls follow the context path of the app`() {
        contextRunner
            .withPropertyValues("server.servlet.context-path=/beta/afk-no")
            .run { context ->
                expectGet("http://fint-flyt-intgr-conf-service:8080/beta/afk-no/api/intern-klient/x")
                expectGet("http://fint-flyt-history-service:8080/beta/afk-no/api/intern-klient/x")

                context.getBean("intgrConfRestClient", RestClient::class.java).getX()
                context.getBean("historyRestClient", RestClient::class.java).getX()

                server.verify()
            }
    }

    @Test
    fun `base urls can be overridden`() {
        contextRunner
            .withPropertyValues(
                "server.servlet.context-path=/afk-no",
                "novari.flyt.intgr-conf-service-url=http://localhost:8095",
                "novari.flyt.history-service-url=http://localhost:8096",
            ).run { context ->
                expectGet("http://localhost:8095/api/intern-klient/x")
                expectGet("http://localhost:8096/api/intern-klient/x")

                context.getBean("intgrConfRestClient", RestClient::class.java).getX()
                context.getBean("historyRestClient", RestClient::class.java).getX()

                server.verify()
            }
    }

    private fun expectGet(url: String) {
        server.expect(requestTo(url)).andRespond(withSuccess())
    }

    private fun RestClient.getX() {
        get()
            .uri("/x")
            .retrieve()
            .toBodilessEntity()
    }
}
