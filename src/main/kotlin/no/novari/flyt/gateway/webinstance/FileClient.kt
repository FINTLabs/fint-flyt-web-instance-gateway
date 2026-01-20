package no.novari.flyt.gateway.webinstance

import no.novari.flyt.gateway.webinstance.exception.FileUploadException
import no.novari.flyt.gateway.webinstance.model.File
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Recover
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.net.URI
import java.util.UUID

@Service
class FileClient(
    @field:Qualifier("fileRestClient")
    private val restClient: RestClient,
    @Value("\${novari.flyt.file-service-url}")
    private val fileServiceUrl: String,
) {
    @Retryable(
        value = [
            RestClientException::class,
            FileUploadException::class,
        ],
        maxAttempts = 5,
        backoff =
            Backoff(
                delay = 1_000,
                multiplier = 2.0,
                maxDelay = 30_000,
            ),
    )
    fun postFile(file: File): UUID {
        try {
            val uri = fileServiceUrl.trimEnd('/') + "/api/intern-klient/filer"
            log.info("File upload URL base: {}", fileServiceUrl)
            log.info("File upload URL: {}", uri)
            val response =
                restClient
                    .post()
                    .uri(URI.create(uri))
                    .body(file)
                    .retrieve()
                    .body(UUID::class.java)

            if (response == null) {
                throw FileUploadException(file, "Empty response body")
            }

            return response
        } catch (ex: RestClientResponseException) {
            val body = ex.responseBodyAsString ?: "Unknown error"
            throw FileUploadException(file, body, ex)
        } catch (ex: RestClientException) {
            throw ex
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(FileClient::class.java)
    }

    @Recover
    fun recover(
        ex: Throwable,
        file: File,
    ): UUID {
        val message =
            when (ex) {
                is FileUploadException -> ex.message
                is RestClientResponseException -> ex.responseBodyAsString
                else -> ex.message
            } ?: "Unknown error"

        throw FileUploadException(
            file = file,
            postResponse = message,
            cause = ex,
        )
    }
}
