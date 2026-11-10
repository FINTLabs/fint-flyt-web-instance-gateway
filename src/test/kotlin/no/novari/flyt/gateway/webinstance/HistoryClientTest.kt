package no.novari.flyt.gateway.webinstance

import no.novari.flyt.gateway.webinstance.config.InternalClientConfiguration
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

class HistoryClientTest {
    private val baseUrl = "http://fint-flyt-history-service:8080/beta/afk-no/api/intern-klient"
    private val restClientBuilder =
        InternalClientConfiguration()
            .historyRestClient(
                RestClient.builder(),
                testAuthorizedClientManager(),
                ClientHttpRequestFactoryBuilder.detect(),
                ClientHttpRequestFactorySettings.defaults(),
                "http://fint-flyt-history-service:8080/beta/afk-no",
            ).mutate()
    private val server = MockRestServiceServer.bindTo(restClientBuilder).build()
    private val client = HistoryClient(restClientBuilder.build())

    @AfterEach
    fun verify() {
        server.verify()
    }

    @Test
    fun `getArchiveInstanceId sends history-service token and returns plain text id`() {
        server
            .expect(requestTo("$baseUrl/archive-instance-id?sourceApplicationId=4&sourceApplicationInstanceId=123"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-history-service"))
            .andRespond(withSuccess("2026/42", MediaType.TEXT_PLAIN))

        assertThat(client.getArchiveInstanceId(4, "123")).isEqualTo("2026/42")
    }

    @Test
    fun `sourceApplicationInstanceId is encoded`() {
        server
            .expect(
                requestTo("$baseUrl/archive-instance-id?sourceApplicationId=4&sourceApplicationInstanceId=a%2Bb%26c"),
            ).andRespond(withSuccess("2026/42", MediaType.TEXT_PLAIN))

        assertThat(client.getArchiveInstanceId(4, "a+b&c")).isEqualTo("2026/42")
    }

    @Test
    fun `404 returns null like the Kafka reply did`() {
        server
            .expect(requestTo("$baseUrl/archive-instance-id?sourceApplicationId=4&sourceApplicationInstanceId=123"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        assertThat(client.getArchiveInstanceId(4, "123")).isNull()
    }

    @Test
    fun `server error is thrown`() {
        server
            .expect(requestTo("$baseUrl/archive-instance-id?sourceApplicationId=4&sourceApplicationInstanceId=123"))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        assertThrows<HttpServerErrorException> { client.getArchiveInstanceId(4, "123") }
    }
}
