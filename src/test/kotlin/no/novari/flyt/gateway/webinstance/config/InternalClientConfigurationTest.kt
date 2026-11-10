package no.novari.flyt.gateway.webinstance.config

import no.novari.flyt.gateway.webinstance.testAuthorizedClientManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.http.client.HttpClientAutoConfiguration
import org.springframework.boot.test.context.assertj.AssertableApplicationContext
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import java.net.ServerSocket
import java.time.Duration

class InternalClientConfigurationTest {
    private val contextRunner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(HttpClientAutoConfiguration::class.java))
            .withUserConfiguration(InternalClientConfiguration::class.java)
            .withBean(RestClient.Builder::class.java, { RestClient.builder() })
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
                context.expectGet(
                    "intgrConfRestClient",
                    "http://fint-flyt-intgr-conf-service:8080/beta/afk-no/api/intern-klient/x",
                )
                context.expectGet(
                    "historyRestClient",
                    "http://fint-flyt-history-service:8080/beta/afk-no/api/intern-klient/x",
                )
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
                context.expectGet("intgrConfRestClient", "http://localhost:8095/api/intern-klient/x")
                context.expectGet("historyRestClient", "http://localhost:8096/api/intern-klient/x")
            }
    }

    @Test
    fun `read timeout defaults to 5 seconds`() {
        contextRunner.run { context ->
            val elapsed = context.timeToReadTimeout("historyRestClient")

            assertThat(elapsed).isBetween(Duration.ofSeconds(4), Duration.ofSeconds(10))
        }
    }

    @Test
    fun `spring http client read timeout overrides the default`() {
        contextRunner
            .withPropertyValues("spring.http.client.read-timeout=200ms")
            .run { context ->
                val elapsed = context.timeToReadTimeout("intgrConfRestClient")

                assertThat(elapsed).isLessThan(Duration.ofSeconds(2))
            }
    }

    private fun AssertableApplicationContext.expectGet(
        beanName: String,
        url: String,
    ) {
        val builder = getBean(beanName, RestClient::class.java).mutate()
        val server = MockRestServiceServer.bindTo(builder).build()
        server.expect(requestTo(url)).andRespond(withSuccess())

        builder.build().getX()

        server.verify()
    }

    // The socket accepts the connection but never answers, like a hanging service.
    private fun AssertableApplicationContext.timeToReadTimeout(beanName: String): Duration =
        ServerSocket(0).use { socket ->
            val client =
                getBean(beanName, RestClient::class.java)
                    .mutate()
                    .baseUrl("http://localhost:${socket.localPort}")
                    .build()
            val start = System.nanoTime()
            assertThrows<ResourceAccessException> { client.getX() }
            Duration.ofNanos(System.nanoTime() - start)
        }

    private fun RestClient.getX() {
        get()
            .uri("/x")
            .retrieve()
            .toBodilessEntity()
    }
}
