package no.novari.gateway.instance

import no.novari.gateway.instance.exception.FileUploadException
import no.novari.gateway.instance.model.File
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Recover
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.util.UUID

@Service
class FileClient(
    @field:Qualifier("fileRestClient")
    private val restClient: RestClient,
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
            val response =
                restClient
                    .post()
                    .uri("")
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
