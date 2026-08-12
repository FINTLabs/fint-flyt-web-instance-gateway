package no.novari.flyt.gateway.webinstance.config

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import no.novari.flyt.webresourceserver.security.AuthorizationServiceConfiguration
import no.novari.flyt.webresourceserver.security.ExternalClientApiConfiguration
import no.novari.flyt.webresourceserver.security.InternalClientApiConfiguration
import no.novari.flyt.webresourceserver.security.InternalUserApiConfiguration
import no.novari.flyt.webresourceserver.security.SecurityConfiguration
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.test.context.TestConstructor
import org.springframework.web.client.RestClient

/**
 * Regresjonstest for FFS-2223: `fileRestClient` må bygges fra den injiserte, observability-
 * konfigurerte `RestClient.Builder`-bønnen - ikke en fersk `RestClient.builder()` - for at
 * utgående kall til file-service skal få client-span og `traceparent`-header.
 *
 * `RestClientConfiguration` er ekskludert fra auto-konfigurasjonen her siden den krever
 * OAuth2-klientregistrering vi ikke trenger for denne testen; klassen instansieres i stedet
 * direkte, akkurat slik Spring ville gjort det, med den ekte injiserte builderen.
 */
@SpringBootTest(
    classes = [RestClientConfigurationTracingTest.MinimalTestApplication::class],
    properties = ["management.tracing.sampling.probability=1.0"],
)
@AutoConfigureObservability
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class RestClientConfigurationTracingTest(
    private val restClientBuilder: RestClient.Builder,
    private val spanExporter: InMemorySpanExporter,
) {
    private lateinit var mockWebServer: MockWebServer

    @BeforeEach
    fun startServer() {
        spanExporter.reset()
        mockWebServer = MockWebServer()
        mockWebServer.enqueue(MockResponse().setResponseCode(200))
        mockWebServer.start()
    }

    @AfterEach
    fun stopServer() {
        mockWebServer.shutdown()
    }

    @Test
    fun `fileRestClient built from the injected builder sends a traceparent header and produces a client span`() {
        val authorizedClientManager = mock<OAuth2AuthorizedClientManager>()
        val requestFactory = HttpComponentsClientHttpRequestFactory(HttpClientBuilder.create().build())

        val fileRestClient =
            RestClientConfiguration().fileRestClient(restClientBuilder, authorizedClientManager, requestFactory)

        fileRestClient
            .get()
            .uri(mockWebServer.url("/ping").toString())
            .retrieve()
            .toBodilessEntity()

        val recordedRequest = mockWebServer.takeRequest()
        assertThat(recordedRequest.getHeader("traceparent"))
            .`as`("utgående kall skal bære W3C traceparent når fileRestClient bruker den injiserte builderen")
            .isNotNull()

        val clientSpans = spanExporter.finishedSpanItems.filter { it.kind.name == "CLIENT" }
        assertThat(clientSpans)
            .`as`("skal produsere minst ett client-spenn for det utgående kallet")
            .isNotEmpty()
    }

    @TestConfiguration
    class SpanCaptureConfiguration {
        @Bean
        fun inMemorySpanExporter(): InMemorySpanExporter = InMemorySpanExporter.create()

        @Bean
        fun inMemorySpanProcessor(exporter: InMemorySpanExporter): SpanProcessor = SimpleSpanProcessor.create(exporter)
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(
        exclude = [
            RestClientConfiguration::class,
            AuthorizationServiceConfiguration::class,
            ExternalClientApiConfiguration::class,
            InternalClientApiConfiguration::class,
            InternalUserApiConfiguration::class,
            SecurityConfiguration::class,
        ],
    )
    @Import(SpanCaptureConfiguration::class)
    class MinimalTestApplication
}
