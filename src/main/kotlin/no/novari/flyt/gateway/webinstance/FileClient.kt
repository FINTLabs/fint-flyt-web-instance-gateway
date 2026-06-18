package no.novari.flyt.gateway.webinstance

import no.novari.flyt.gateway.webinstance.exception.FileUploadException
import no.novari.flyt.gateway.webinstance.model.File
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Recover
import org.springframework.retry.annotation.Retryable
import org.springframework.retry.support.RetrySynchronizationManager
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.net.URI
import java.util.UUID

@Service
class FileClient(
    @param:Qualifier("fileRestClient")
    private val restClient: RestClient,
    @param:Value("\${novari.flyt.file-service-url}")
    private val fileServiceUrl: String,
) {
    @Retryable(
        value = [
            RestClientException::class,
            FileUploadException::class,
        ],
        maxAttempts = MAX_ATTEMPTS,
        backoff =
            Backoff(
                delay = 1_000,
                multiplier = 2.0,
                maxDelay = 30_000,
            ),
    )
    fun postFile(file: File): UUID {
        val uri = fileServiceUrl.trimEnd('/') + "/api/intern-klient/filer"
        val attempt = currentRetryAttempt()

        try {
            log.debug(
                "Posting file to file service, attempt={}, uri={}, file={}",
                attempt,
                uri,
                file.toDebugLogString(),
            )

            val response =
                restClient
                    .post()
                    .uri(URI.create(uri))
                    .body(file)
                    .retrieve()
                    .body(UUID::class.java)

            if (response == null) {
                log.debug(
                    "File upload returned empty response body, attempt={}, uri={}, file={}",
                    attempt,
                    uri,
                    file.toDebugLogString(),
                )
                throw FileUploadException(file, "Empty response body")
            }

            log.debug(
                "File upload succeeded, attempt={}, uri={}, fileId={}, file={}",
                attempt,
                uri,
                response,
                file.toDebugLogString(),
            )
            return response
        } catch (ex: RestClientResponseException) {
            val body = ex.responseBodyAsString.ifBlank { "<empty>" }
            val status = ex.statusCode.value()
            val statusText = ex.statusText.ifBlank { "Unknown status" }
            log.debug(
                "File upload failed with HTTP response, attempt={}, uri={}, status={}, statusText={}, responseBody={}, file={}",
                attempt,
                uri,
                status,
                statusText,
                body.toLogValue(),
                file.toDebugLogString(),
                ex,
            )
            throw FileUploadException(file, "HTTP $status $statusText: $body", ex)
        } catch (ex: RestClientException) {
            log.debug(
                "File upload failed before receiving HTTP response, attempt={}, uri={}, exceptionType={}, message={}, file={}",
                attempt,
                uri,
                ex::class.qualifiedName,
                ex.message,
                file.toDebugLogString(),
                ex,
            )
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

        log.debug(
            "File upload retries exhausted, attempts={}, failureType={}, message={}, file={}",
            MAX_ATTEMPTS,
            ex::class.qualifiedName,
            message,
            file.toDebugLogString(),
            ex,
        )

        throw FileUploadException(
            file = file,
            postResponse = message,
            cause = ex,
        )
    }

    private fun currentRetryAttempt(): Int = (RetrySynchronizationManager.getContext()?.retryCount ?: 0) + 1

    private fun File.toDebugLogString(): String =
        "$this, base64ContentLength=${base64Contents.length}, " +
            "estimatedContentLengthBytes=${estimatedContentLengthBytes()}"

    private fun File.estimatedContentLengthBytes(): Long {
        val contentLength = base64Contents.length
        if (contentLength == 0) {
            return 0
        }

        val padding =
            when {
                base64Contents.endsWith("==") -> 2
                base64Contents.endsWith("=") -> 1
                else -> 0
            }

        return (contentLength * 3L / 4) - padding
    }

    private fun String.toLogValue(): String =
        if (length <= MAX_RESPONSE_BODY_LOG_LENGTH) {
            this
        } else {
            "${take(MAX_RESPONSE_BODY_LOG_LENGTH)}<truncated ${length - MAX_RESPONSE_BODY_LOG_LENGTH} chars>"
        }

    private companion object {
        private const val MAX_ATTEMPTS = 5
        private const val MAX_RESPONSE_BODY_LOG_LENGTH = 4_000
        private val log = LoggerFactory.getLogger(FileClient::class.java)
    }
}
