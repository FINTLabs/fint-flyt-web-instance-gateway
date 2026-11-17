package no.novari.flyt.gateway.webinstance.config

import no.novari.flyt.gateway.webinstance.testAuthorizedClientManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.http.client.HttpClientAutoConfiguration
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration
import org.springframework.boot.test.context.assertj.AssertableApplicationContext
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import java.net.ServerSocket
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class InternalClientConfigurationTest {
    private val contextRunner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(HttpClientAutoConfiguration::class.java))
            .withUserConfiguration(InternalClientConfiguration::class.java)
            // Prototype, like Boot's builder, so the clients do not share interceptors.
            .withBean(RestClient.Builder::class.java, { RestClient.builder() }, { it.scope = "prototype" })
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
    fun `callers share one token per registration`() {
        val registration =
            ClientRegistration
                .withRegistrationId("history-service")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .clientId("client")
                .tokenUri("http://idp/token")
                .build()
        val registrations = InMemoryClientRegistrationRepository(registration)
        val tokenRequests = AtomicInteger()
        val manager =
            AuthorizedClientServiceOAuth2AuthorizedClientManager(
                registrations,
                InMemoryOAuth2AuthorizedClientService(registrations),
            )
        manager.setAuthorizedClientProvider(
            OAuth2AuthorizedClientProvider { context ->
                context.authorizedClient ?: OAuth2AuthorizedClient(
                    context.clientRegistration,
                    context.principal.name,
                    OAuth2AccessToken(
                        OAuth2AccessToken.TokenType.BEARER,
                        "token-${tokenRequests.incrementAndGet()}",
                        Instant.now(),
                        Instant.now().plusSeconds(60),
                    ),
                )
            },
        )

        contextRunner
            .withBean("internalClientAuthorizedClientManager", OAuth2AuthorizedClientManager::class.java, { manager })
            .run { context ->
                val builder = context.getBean("historyRestClient", RestClient::class.java).mutate()
                val server = MockRestServiceServer.bindTo(builder).build()
                server
                    .expect(
                        ExpectedCount.twice(),
                        requestTo("http://fint-flyt-history-service:8080/api/intern-klient/x"),
                    ).andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-1"))
                    .andRespond(withSuccess())
                val client = builder.build()

                try {
                    listOf("source-application-a", "source-application-b").forEach { caller ->
                        SecurityContextHolder.getContext().authentication = TestingAuthenticationToken(caller, null)
                        client.getX()
                    }
                } finally {
                    SecurityContextHolder.clearContext()
                }

                server.verify()
                assertThat(tokenRequests).hasValue(1)
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

    @Test
    @Timeout(30) // Without a token timeout the call hangs forever.
    fun `token request to NAM times out after 5 seconds by default`() {
        // The socket accepts the connection but never answers, like a hanging NAM.
        ServerSocket(0).use { nam ->
            ApplicationContextRunner()
                .withConfiguration(
                    AutoConfigurations.of(
                        OAuth2ClientAutoConfiguration::class.java,
                        HttpClientAutoConfiguration::class.java,
                        RestClientAutoConfiguration::class.java,
                    ),
                ).withUserConfiguration(InternalClientConfiguration::class.java)
                .withPropertyValues(
                    "spring.security.oauth2.client.provider.fint-idp.token-uri=http://localhost:${nam.localPort}/token",
                    "spring.security.oauth2.client.registration.history-service.provider=fint-idp",
                    "spring.security.oauth2.client.registration.history-service.authorization-grant-type=" +
                        "client_credentials",
                    "spring.security.oauth2.client.registration.history-service.client-id=client",
                    "spring.security.oauth2.client.registration.history-service.client-secret=secret",
                ).run { context ->
                    assertThat(context).hasNotFailed()
                    val client = context.getBean("historyRestClient", RestClient::class.java)
                    val start = System.nanoTime()
                    assertThrows<RuntimeException> { client.getX() }
                    val elapsed = Duration.ofNanos(System.nanoTime() - start)

                    assertThat(elapsed).isBetween(Duration.ofSeconds(4), Duration.ofSeconds(10))
                }
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
