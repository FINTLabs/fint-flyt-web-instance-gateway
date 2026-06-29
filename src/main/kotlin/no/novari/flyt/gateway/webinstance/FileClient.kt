package no.novari.flyt.gateway.webinstance

import no.novari.flyt.gateway.webinstance.exception.FileUploadException
import no.novari.flyt.gateway.webinstance.exception.MultipartFileUploadException
import no.novari.flyt.gateway.webinstance.model.File
import no.novari.flyt.gateway.webinstance.model.MultipartFileUpload
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.InputStreamResource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Recover
import org.springframework.retry.annotation.Retryable
import org.springframework.retry.support.RetrySynchronizationManager
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.body
import java.net.URI
import java.nio.charset.StandardCharsets
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
        val uri = fileServiceUri()
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
            val status = ex.statusCode.value()
            val statusText = ex.statusText.ifBlank { "Unknown status" }
            log.debug(
                "File upload failed with HTTP response, attempt={}, uri={}, status={}, statusText={}, file={}",
                attempt,
                uri,
                status,
                statusText,
                file.toDebugLogString(),
            )
            throw FileUploadException(file, "HTTP $status $statusText")
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

    @Retryable(
        value = [
            RestClientException::class,
            MultipartFileUploadException::class,
        ],
        maxAttempts = MAX_ATTEMPTS,
        backoff =
            Backoff(
                delay = 1_000,
                multiplier = 2.0,
                maxDelay = 30_000,
            ),
    )
    fun postFile(file: MultipartFileUpload): UUID {
        val uri = fileServiceUri()
        val attempt = currentRetryAttempt()

        try {
            log.debug(
                "Posting multipart file to file service, attempt={}, uri={}, file={}",
                attempt,
                uri,
                file.toDebugLogString(),
            )

            val response =
                restClient
                    .post()
                    .uri(URI.create(uri))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(file.toMultipartBody())
                    .retrieve()
                    .body<UUID>()

            if (response == null) {
                log.debug(
                    "Multipart file upload returned empty response body, attempt={}, uri={}, file={}",
                    attempt,
                    uri,
                    file.toDebugLogString(),
                )
                throw MultipartFileUploadException(file, "Empty response body")
            }

            log.debug(
                "Multipart file upload succeeded, attempt={}, uri={}, fileId={}, file={}",
                attempt,
                uri,
                response,
                file.toDebugLogString(),
            )
            return response
        } catch (ex: RestClientResponseException) {
            val status = ex.statusCode.value()
            val statusText = ex.statusText.ifBlank { "Unknown status" }
            log.debug(
                "Multipart file upload failed with HTTP response, attempt={}, uri={}, status={}, " +
                    "statusText={}, file={}",
                attempt,
                uri,
                status,
                statusText,
                file.toDebugLogString(),
            )
            throw MultipartFileUploadException(file, "HTTP $status $statusText")
        } catch (ex: RestClientException) {
            log.debug(
                "Multipart file upload failed before receiving HTTP response, attempt={}, uri={}, " +
                    "exceptionType={}, message={}, file={}",
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
                is RestClientResponseException -> ex.toHttpStatusMessage()
                else -> ex.message
            } ?: "Unknown error"

        log.debug(
            "File upload retries exhausted, attempts={}, failureType={}, message={}, file={}",
            MAX_ATTEMPTS,
            ex::class.qualifiedName,
            message,
            file.toDebugLogString(),
        )

        throw FileUploadException(
            file = file,
            postResponse = message,
            cause = ex.safeCauseForFileUpload(),
        )
    }

    @Recover
    fun recover(
        ex: Throwable,
        file: MultipartFileUpload,
    ): UUID {
        val message =
            when (ex) {
                is MultipartFileUploadException -> ex.message
                is RestClientResponseException -> ex.toHttpStatusMessage()
                else -> ex.message
            } ?: "Unknown error"

        log.debug(
            "Multipart file upload retries exhausted, attempts={}, failureType={}, message={}, file={}",
            MAX_ATTEMPTS,
            ex::class.qualifiedName,
            message,
            file.toDebugLogString(),
        )

        throw MultipartFileUploadException(
            file = file,
            postResponse = message,
            cause = ex.safeCauseForFileUpload(),
        )
    }

    private fun fileServiceUri(): String = fileServiceUrl.trimEnd('/') + "/api/intern-klient/filer"

    private fun currentRetryAttempt(): Int = (RetrySynchronizationManager.getContext()?.retryCount ?: 0) + 1

    private fun File.toDebugLogString(): String =
        "base64ContentLength=${base64Contents.length}, " +
            "estimatedContentLengthBytes=${estimatedContentLengthBytes()}"

    private fun MultipartFileUpload.toDebugLogString(): String =
        "contentLength=${multipartFile.size}, " +
            "contentType=${multipartFile.contentType}"

    private fun MultipartFileUpload.toMultipartBody(): MultiValueMap<String, Any> {
        val body = LinkedMultiValueMap<String, Any>()
        body.add("metadata", HttpEntity(toMetadata(), metadataHeaders()))
        body.add("file", HttpEntity(toResource(), fileHeaders()))
        return body
    }

    private fun MultipartFileUpload.toMetadata(): FileUploadMetadata =
        FileUploadMetadata(
            name = name,
            sourceApplicationId = sourceApplicationId,
            sourceApplicationInstanceId = sourceApplicationInstanceId,
            type = type.toString(),
            encoding = encoding,
        )

    private fun MultipartFileUpload.toResource(): InputStreamResource = MultipartFileUploadResource(this)

    private fun MultipartFileUpload.fileHeaders(): HttpHeaders =
        HttpHeaders().apply {
            contentType = type
            contentDisposition =
                ContentDisposition
                    .formData()
                    .name("file")
                    .filename(name, StandardCharsets.UTF_8)
                    .build()
        }

    private fun metadataHeaders(): HttpHeaders =
        HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }

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

    private fun RestClientResponseException.toHttpStatusMessage(): String =
        "HTTP ${statusCode.value()} ${statusText.ifBlank { "Unknown status" }}"

    private fun Throwable.safeCauseForFileUpload(): Throwable? = takeUnless { it is RestClientResponseException }

    private data class FileUploadMetadata(
        val name: String,
        val sourceApplicationId: Long,
        val sourceApplicationInstanceId: String,
        val type: String,
        val encoding: String,
    )

    private class MultipartFileUploadResource(
        private val file: MultipartFileUpload,
    ) : InputStreamResource(file.multipartFile.inputStream) {
        override fun getFilename(): String = file.name

        override fun contentLength(): Long = file.multipartFile.size

        override fun getDescription(): String = "Multipart file upload resource"
    }

    private companion object {
        private const val MAX_ATTEMPTS = 5
        private val log = LoggerFactory.getLogger(FileClient::class.java)
    }
}
