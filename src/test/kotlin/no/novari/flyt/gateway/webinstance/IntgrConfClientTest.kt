package no.novari.flyt.gateway.webinstance

import no.novari.flyt.gateway.webinstance.config.InternalClientConfiguration
import no.novari.flyt.gateway.webinstance.model.Integration
import no.novari.flyt.gateway.webinstance.model.SourceApplicationIdAndSourceApplicationIntegrationId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient

class IntgrConfClientTest {
    private val baseUrl = "http://fint-flyt-intgr-conf-service:8080/afk-no/api/intern-klient"
    private val restClientBuilder =
        InternalClientConfiguration()
            .intgrConfRestClient(
                RestClient.builder(),
                testAuthorizedClientManager(),
                ClientHttpRequestFactoryBuilder.detect(),
                ClientHttpRequestFactorySettings.defaults(),
                "http://fint-flyt-intgr-conf-service:8080/afk-no/",
            ).mutate()
    private val server = MockRestServiceServer.bindTo(restClientBuilder).build()
    private val client = IntgrConfClient(restClientBuilder.build())

    @AfterEach
    fun verify() {
        server.verify()
    }

    @Test
    fun `getIntegration sends intgr-conf-service token and returns integration`() {
        server
            .expect(requestTo("$baseUrl/integrasjoner?sourceApplicationId=4&sourceApplicationIntegrationId=SOKNAD"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-intgr-conf-service"))
            .andRespond(
                withSuccess(
                    """
                    {
                      "id": 1,
                      "sourceApplicationId": 4,
                      "sourceApplicationIntegrationId": "SOKNAD",
                      "destination": "fylkesrad",
                      "state": "ACTIVE",
                      "activeConfigurationId": 2,
                      "createdAt": "2026-01-01T00:00:00Z",
                      "createdBy": "someone"
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val integration = client.getIntegration(params("SOKNAD"))

        assertThat(integration).isEqualTo(
            Integration(
                id = 1,
                sourceApplicationId = 4,
                sourceApplicationIntegrationId = "SOKNAD",
                destination = "fylkesrad",
                state = Integration.State.ACTIVE,
                activeConfigurationId = 2,
            ),
        )
    }

    @Test
    fun `sourceApplicationIntegrationId is encoded`() {
        server
            .expect(requestTo("$baseUrl/integrasjoner?sourceApplicationId=4&sourceApplicationIntegrationId=A%26B%20C"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        assertThat(client.getIntegration(params("A&B C"))).isNull()
    }

    @Test
    fun `404 returns null like the Kafka reply did`() {
        server
            .expect(requestTo("$baseUrl/integrasjoner?sourceApplicationId=4&sourceApplicationIntegrationId=SOKNAD"))
            .andRespond(
                withStatus(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"status":404,"error":"Not Found"}"""),
            )

        assertThat(client.getIntegration(params("SOKNAD"))).isNull()
    }

    @Test
    fun `server error is thrown`() {
        server
            .expect(requestTo("$baseUrl/integrasjoner?sourceApplicationId=4&sourceApplicationIntegrationId=SOKNAD"))
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE))

        assertThrows<HttpServerErrorException> { client.getIntegration(params("SOKNAD")) }
    }

    private fun params(sourceApplicationIntegrationId: String) =
        SourceApplicationIdAndSourceApplicationIntegrationId(
            sourceApplicationId = 4,
            sourceApplicationIntegrationId = sourceApplicationIntegrationId,
        )
}
